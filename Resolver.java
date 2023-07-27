import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class Resolver {
    private static final int DNS_SERVER_PORT = 53;
    private static final int NAME_ERROR = 3;
    private static final int FORMAT_ERROR = 1;
    private static final int SERVER_ERROR = 2;
    public static void main(String[] args) throws Exception {

        if (args.length != 2) {
            System.err.println("Error: invalid arguments");
            System.err.println("Usage: resolver port timeout");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        int timeout = Integer.parseInt(args[1]) * 1000;
        DatagramSocket socket = new DatagramSocket(port);
        String filepath = "named.root";
        ArrayList<String> rootServers = extractRootAddresses(filepath);
        // listening for clients loop
        while (true) {
            DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
            socket.receive(request);
            byte[] array = new byte[request.getLength()];
            System.arraycopy(request.getData(), request.getOffset(), array, 0, request.getLength());
            // details to build return datagram packet
            InetAddress clientHost = request.getAddress();
            int clientPort = request.getPort();

            HashSet<String> visited = new HashSet<>();
            DatagramSocket processingSocket = new DatagramSocket();
            processingSocket.setSoTimeout(timeout);
            Runnable query = () -> {
                try {
                    byte[] ret = sendAnswerPacket(new ArrayList<>(rootServers), array, processingSocket, new ArrayList<>(rootServers), visited);
                    DatagramPacket packet;
                    // in the very unlikely case we don't have an error and we exhaust the search space we send a "dummy packet" with a server error so the client displays that
                    if (ret == null) {
                        array[3] = (byte) (array[3] | 0b00000010);
                        packet = new DatagramPacket(array, array.length, clientHost, clientPort);
                    } else {
                        packet = new DatagramPacket(ret, ret.length, clientHost, clientPort);
                    }
        
                    processingSocket.send(packet);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            Thread thread = new Thread(query);
            thread.start();
        }
    } 

    private static boolean checkServerError(int RCODE) {
        return RCODE == SERVER_ERROR;
    }

    private static boolean checkOtherErrors(int RCODE) {
        return RCODE != 0;  
    }

    private static String getNSRecordString(byte[] rdata, int index, byte[] response) {
        StringBuilder nsRecord = new StringBuilder();

        int i = index;
        int length = rdata.length;
        while (i < length) {
            int labelLength = rdata[i++];
            if (labelLength == 0) {
                break;
            }

            if (nsRecord.length() > 0) {
                nsRecord.append(".");
            }

            if ((labelLength & 0b11000000) == 0b11000000) {
                // Compressed label, jump to the offset specified in the next byte
                int offset = ((labelLength & 0b00111111) << 8) + (rdata[i++] & 0b11111111);
                String label = getNSRecordString(response, offset, response); // Recursive call to continue parsing after compression
                nsRecord.append(label);
                break;
            } else {
                // Non-compressed label
                for (int j = 0; j < labelLength; j++) {
                    nsRecord.append((char) rdata[i++]);
                }
            }
        }

        return nsRecord.toString();
    }

    private static void skipOtherRecordTypes(DataInputStream stream) throws IOException {
        stream.skipBytes(6); // TTL and CLASS fields
        short rdataLength = stream.readShort();
        stream.skipBytes(rdataLength);
    }

    private static ArrayList<String> extractRootAddresses(String filePath) {
        ArrayList<String> ipAddresses = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            Pattern ipv4Pattern = Pattern.compile("(\\d+\\.){3}\\d+");
            
            while ((line = reader.readLine()) != null) {
                Matcher matcher = ipv4Pattern.matcher(line);
                while (matcher.find()) {
                    String ipv4Address = matcher.group();
                    ipAddresses.add(ipv4Address);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading the file: " + e.getMessage());
        }

        return ipAddresses;
        
    }

    private static byte[] sendAnswerPacket(ArrayList<String> ipAddresses, byte[] data, DatagramSocket socket, ArrayList<String> rootServers, HashSet<String> visited) throws Exception {
        while (!ipAddresses.isEmpty()) {
            // pop from list
            String sendAddressString = ipAddresses.remove(ipAddresses.size() - 1);
            InetAddress sendAddress = InetAddress.getByName(sendAddressString);
            // send packet
            DatagramPacket rep = new DatagramPacket(data, data.length, sendAddress, DNS_SERVER_PORT);
            socket.send(rep);
            byte[] response = new byte[1024];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            try {
                socket.receive(packet);
            } catch (SocketTimeoutException e) {
                continue;
            }
            visited.add(sendAddressString);
            // decode and add to the list if possible (or return)
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));
            short flags = dataInputStream.readByte();
            flags = dataInputStream.readByte();
            int RCODE = flags & 0b00001111; // for errors
            dataInputStream.readShort();
            short ANCOUNT = dataInputStream.readShort();
            short NSCOUNT = dataInputStream.readShort();
            short ARCOUNT = dataInputStream.readShort();
            // send packet to client and continue
            if (checkOtherErrors(RCODE) || ANCOUNT > 0) {
                return response;
            }
            // go to next iteration of loop
            if (checkServerError(RCODE)) {
                continue;
            }
            // now we're left with the case where we don't have an answer
            Helper.skipQuestionSection(dataInputStream);

            ArrayList<String> nsRecords = getNS(dataInputStream, NSCOUNT, response);
            ArrayList<String> aRecords = getAdditionalRecords(dataInputStream, ARCOUNT);
            
            if (aRecords.size() > 0) {
                for (String address : aRecords) {
                    if (!visited.contains(address)) {
                        ipAddresses.add(address);
                    }
                }
            } else {
                for (int i = 0; i < nsRecords.size(); i++) {
                    ArrayList<String> nsAddresses = new ArrayList<>();
                    Random random = new Random();
                    short requestID = (short) random.nextInt(Short.MAX_VALUE + 1);
                    byte[] NSPacket = Helper.buildPacket(requestID, nsRecords.get(i));
                    byte[] dataFromNS = sendAnswerPacket(rootServers, NSPacket, socket, rootServers, new HashSet<>());
                    nsAddresses = getAnswersFromBeginningOfPacket(dataFromNS);
                    if (!nsAddresses.isEmpty()) {
                        byte[] res = sendAnswerPacket(nsAddresses, data, socket, rootServers, visited);
                        if (res != null) {
                            return res;
                        }
                    } 
                }
            }

        }
        return null;
    }

    private static ArrayList<String> getNS(DataInputStream dataInputStream, int NSCOUNT, byte[] response) throws IOException {
        ArrayList<String> nsRecords = new ArrayList<>();
        for (int i = 0; i < NSCOUNT; i++) {
            Helper.skipQname(dataInputStream);
            short TYPE = dataInputStream.readShort();
            if (TYPE != 2) {
                skipOtherRecordTypes(dataInputStream);
                continue;
            }
            dataInputStream.skipBytes(6);
            int RDLENGTH = dataInputStream.readShort();

            byte[] rdata = new byte[RDLENGTH];
            dataInputStream.readFully(rdata);
            String NSRecord = getNSRecordString(rdata, 0, response);
            nsRecords.add(NSRecord);
        }
        return nsRecords;

    }

    private static ArrayList<String> getAdditionalRecords(DataInputStream dataInputStream, int ARCOUNT) throws IOException {
        ArrayList<String> aRecords = new ArrayList<>();
        for (int i = 0; i < ARCOUNT; i++) {
            Helper.skipQname(dataInputStream);
            ArrayList<Integer> RDATA = new ArrayList<>();
            short TYPE = dataInputStream.readShort();
            // some support to skip AAAA records if we come across them
            if (TYPE != 1) {
                skipOtherRecordTypes(dataInputStream);
                continue;
            }
            dataInputStream.skipBytes(6);
            int RDLENGTH = dataInputStream.readShort();
            for (int s = 0; s < RDLENGTH; s++) {
                int nx = dataInputStream.readByte() & 255;
                RDATA.add(nx);
            }

            StringBuilder ip = new StringBuilder();
            for (Integer ipPart:RDATA) {
                ip.append(ipPart).append(".");
            }
            ip.deleteCharAt(ip.length() - 1);
            String ipFinal = ip.toString();
            aRecords.add(ipFinal);
        }
        return aRecords;
    }

    private static ArrayList<String> getAnswersFromBeginningOfPacket(byte[] packet) throws IOException {
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(packet));

        dataInputStream.skipBytes(2);
        short flags = dataInputStream.readByte();
        flags = dataInputStream.readByte();
        int RCODE = flags & 0b00001111; // for errors
        if (RCODE == NAME_ERROR || RCODE == FORMAT_ERROR || RCODE == SERVER_ERROR) {
            return new ArrayList<>();
        } 
        dataInputStream.skipBytes(2);
        short ANCOUNT = dataInputStream.readShort();
        dataInputStream.skipBytes(4);
        Helper.skipQuestionSection(dataInputStream);
        ArrayList<String> res = Helper.getAnswers(dataInputStream, ANCOUNT);
        return res;
    }
}
