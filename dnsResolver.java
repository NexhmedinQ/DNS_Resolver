import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class dnsResolver {
    private static final int DNS_SERVER_PORT = 53;
    public static void main(String[] args) throws Exception {

        if (args.length != 1) {
            System.err.println("Error: invalid arguments");
            System.err.println("Usage: resolver port");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        DatagramSocket socket = new DatagramSocket(port);
        while (true) {
            DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
            socket.receive(request);
            byte[] array = new byte[request.getLength()];
            System.arraycopy(request.getData(), request.getOffset(), array, 0, request.getLength());
            System.out.println(array.length);
            // details to build return datagram packet
            InetAddress clientHost = request.getAddress();
            int clientPort = request.getPort();
            // NEW UNTESTED LINE BELOLW
            HashSet<String> visited = new HashSet<>();
            String filepath = "named.root";
            ArrayList<String> rootServers = extractRootAddresses(filepath);
            System.out.println(rootServers);
            DatagramSocket socket2 = new DatagramSocket();
            byte[] ret = sendAnswerPacket(rootServers, array, socket2, new ArrayList<>(rootServers), visited);
            DatagramPacket packet;
            if (ret == null) {
                array[3] = (byte) (array[3] & 0b00001111);
                packet = new DatagramPacket(array, array.length, clientHost, clientPort);
            } else {
                packet = new DatagramPacket(ret, ret.length, clientHost, clientPort);
            }
            
            socket.send(packet);
        }
    } 


    private static boolean checkServerError(int RCODE) throws IOException {
        if (RCODE == 2) {
            return true;
        }
        return false;
    }

    private static boolean checkOtherErrors(int RCODE) throws IOException {
        if (RCODE == 1 || RCODE == 3) {
            return true;
        }
        return false;  
    }

    private static ArrayList<String> getIpAddresses(DataInputStream dataInputStream) {
        return new ArrayList<>();
    }

    private static String getNSRecord(byte[] rdata, int index, byte[] response) {
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
                String label = getNSRecord(response, offset, response); // Recursive call to continue parsing after compression
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

    private static void skipQname(DataInputStream dataInputStream) throws IOException {
        int labelLength;
        while ((labelLength = dataInputStream.readUnsignedByte()) > 0) {
            if ((labelLength & 0xC0) == 0xC0) {
                dataInputStream.skipBytes(1);
                break;
            }
            dataInputStream.skipBytes(labelLength);
        }
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

    private static byte[] buildPacket(short requestID, String requestAddr, boolean isRecursive) throws Exception {
        ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);
        short requestFlags;
        if (isRecursive) {
            requestFlags = Short.parseShort("0000000100000000", 2);
        } else {
            requestFlags = Short.parseShort("0000000000000000", 2);
        }
        dataOutput.writeShort(requestID);
        dataOutput.writeShort(requestFlags);
        // the counts
        dataOutput.writeShort(1);
        dataOutput.writeShort(0);
        dataOutput.writeShort(0);
        dataOutput.writeShort(0);
        String[] addressParts = requestAddr.split("\\.");
        for (int i = 0; i < addressParts.length; i++) {
            byte[] bytes = addressParts[i].getBytes(StandardCharsets.UTF_8);
            dataOutput.writeByte(bytes.length);
            dataOutput.write(bytes);
        }
        dataOutput.writeByte(0);
        // A record
        dataOutput.writeShort(1);
        // IN class
        dataOutput.writeShort(1);
        return byteArrayOutput.toByteArray();
    }

    private static byte[] sendAnswerPacket(ArrayList<String> ipAddresses, byte[] data, DatagramSocket socket, ArrayList<String> rootServers, HashSet<String> visited) throws Exception {
        while (!ipAddresses.isEmpty()) {
            // pop from list
            String sendAddressString = ipAddresses.remove(ipAddresses.size() - 1);
            System.out.println(sendAddressString);
            InetAddress sendAddress = InetAddress.getByName(sendAddressString);
            // send packet
            DatagramPacket rep = new DatagramPacket(data, data.length, sendAddress, DNS_SERVER_PORT);
            socket.send(rep);
            byte[] response = new byte[1024];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            visited.add(sendAddressString);
            // decode and add to the list if possible (or return)
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));
            System.out.println("Transaction ID: " + dataInputStream.readShort()); // ID
            short flags = dataInputStream.readByte();
            flags = dataInputStream.readByte();
            int RCODE = flags & 0b00001111; // for errors
            dataInputStream.readShort();
            short ANCOUNT = dataInputStream.readShort();
            short NSCOUNT = dataInputStream.readShort();
            short ARCOUNT = dataInputStream.readShort();
            // send packet to client and continue
            if (checkOtherErrors(RCODE) || ANCOUNT > 0) {
                System.out.println("we have answer");
                return response;
            }
            // go to next iteration of loop
            if (checkServerError(RCODE)) {
                System.out.println("done");
                continue;
            }
            // now we're left with the case where we don't have an answer
            skipQuestionSection(dataInputStream);

            ArrayList<String> nsRecords = getNS(dataInputStream, NSCOUNT, response);
            ArrayList<String> aRecords = getAdditionalRecords(dataInputStream, ARCOUNT);
            
            if (aRecords.size() > 0) {
                System.out.println("please WORK");
                for (String address : aRecords) {
                    if (!visited.contains(address)) {
                        ipAddresses.add(address);
                    }
                }
            } else {
                System.out.println("please WORKKFGMWKNFGMOWJNFGUOIWN");
                for (int i = 0; i < nsRecords.size(); i++) {
                    ArrayList<String> nsAddresses = new ArrayList<>();
                    for (String rootServer : rootServers) {
                        InetAddress rootAddress = InetAddress.getByName(rootServer);
                        nsAddresses = getIpFromNS(rootAddress, socket, nsRecords.get(i));
                        if (!nsAddresses.isEmpty()) {
                            break;
                        }
                    }
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

    private static ArrayList<String> getIpFromNS(InetAddress rootServer, DatagramSocket socket, String domain) throws Exception {
        Random random = new Random();
        // Generate a random short value
        short requestID = (short) random.nextInt(Short.MAX_VALUE + 1);
        byte[] data = buildPacket(requestID, domain, true);
        DatagramPacket packet = new DatagramPacket(data, data.length, rootServer, DNS_SERVER_PORT);
        socket.send(packet);

        byte[] response = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        socket.receive(responsePacket);
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));
        dataInputStream.skipBytes(6);
        short ANCOUNT = dataInputStream.readShort();
        dataInputStream.skipBytes(4);
        skipQuestionSection(dataInputStream);
        return getAnswers(dataInputStream, ANCOUNT);
    }

    private static ArrayList<String> getAnswers(DataInputStream dataInputStream, short ANCOUNT) throws IOException {
        ArrayList<String> answerList = new ArrayList<>();
        for (int i = 0; i < ANCOUNT; i++) {
            skipQname(dataInputStream);
            ArrayList<Integer> RDATA = new ArrayList<>();
            dataInputStream.skipBytes(8);
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
            answerList.add(ipFinal);
        }
        return answerList;
    }

    private static void skipQuestionSection(DataInputStream dataInputStream) throws IOException {
        int wordLen;
        while ((wordLen = dataInputStream.readByte()) > 0) {
            for (int i = 0; i < wordLen; i++) {
                dataInputStream.readByte();
            }
        }

        dataInputStream.skipBytes(4);
    }

    private static ArrayList<String> getNS(DataInputStream dataInputStream, int NSCOUNT, byte[] response) throws IOException {
        ArrayList<String> nsRecords = new ArrayList<>();
        for (int i = 0; i < NSCOUNT; i++) {
            skipQname(dataInputStream);
            short TYPE = dataInputStream.readShort();
            if (TYPE != 2) {
                skipOtherRecordTypes(dataInputStream);
                continue;
            }
            dataInputStream.skipBytes(6);
            int RDLENGTH = dataInputStream.readShort();

            byte[] rdata = new byte[RDLENGTH];
            dataInputStream.readFully(rdata);
            String NSRecord = getNSRecord(rdata, 0, response);
            nsRecords.add(NSRecord);
        }
        return nsRecords;

    }

    private static ArrayList<String> getAdditionalRecords(DataInputStream dataInputStream, int ARCOUNT) throws IOException {
        ArrayList<String> aRecords = new ArrayList<>();
        for (int i = 0; i < ARCOUNT; i++) {
            skipQname(dataInputStream);
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
                int nx = dataInputStream.readByte() & 255;// and with 255 to
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
}
