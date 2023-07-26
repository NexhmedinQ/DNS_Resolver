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
        InetAddress ipAddress = InetAddress.getByName("125.253.99.33");
        // need to regex root file to get ipv4 addresses

        while (true) {
            DatagramPacket request = new DatagramPacket(new byte[1024], 1024);
            socket.receive(request);

            // details to build return datagram packet
            InetAddress clientHost = request.getAddress();
            int clientPort = request.getPort();

            byte[] array = request.getData();
            DatagramPacket rep = new DatagramPacket(array, array.length, ipAddress, DNS_SERVER_PORT);
            socket.send(rep);
            byte[] response = new byte[1024];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            
            System.out.println("\n\nReceived: " + packet.getLength() + " bytes");
            for (int i = 0; i < packet.getLength(); i++) {
                System.out.print(String.format("%s", response[i]) + " ");
            }
            System.out.println("\n");

            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));
            System.out.println("\n\nStart response decode");
            System.out.println("Transaction ID: " + dataInputStream.readShort()); // ID
            short flags = dataInputStream.readByte();
            int QR = (flags & 0b10000000) >>> 7;
            int opCode = ( flags & 0b01111000) >>> 3;
            int AA = ( flags & 0b00000100) >>> 2;
            int TC = ( flags & 0b00000010) >>> 1;
            int RD = flags & 0b00000001;
            System.out.println("QR "+QR);
            System.out.println("Opcode "+opCode);
            System.out.println("AA "+AA);
            System.out.println("TC "+TC);
            System.out.println("RD "+RD);
            flags = dataInputStream.readByte();
            int RA = (flags & 0b10000000) >>> 7;
            int Z = ( flags & 0b01110000) >>> 4;
            int RCODE = flags & 0b00001111; // for errors
            // send packet to client and continue
            if (checkOtherErrors(RCODE)) {
                 
            }
            // have to give the function a different socket to use. 

            // if we have the answer || we have a non-server error we return to the client
            // if we have a server error or we don't have an answer we continue
            // in the case of a server error pop from array
            // in the case of no answer extract any relevant info ->
            // if we have ip addresses in the additional info then we add them to the list and continue our search
            // if we don't have ip addresses then we get all ip's from authoritative info whereby we need to send A queries to get the ip of the NS.
            // do this for one of the NS records -> then make a recursive call
            // our base case is while there is still ip addresses in the list 
            // so we can pass a new list of size 1 with the ip address taken from that NS
            // lets say the ip addresswill end up giving us an additional 2 ip addresses to search
            // so we'll pop then add the 2 ip addresses
            // if both of the ip addresses fail 
            // the list size will now be 0 so we return false to the function that called us
            // this recursive call will only be in the even that we only have NS records and nothing in additional.
            System.out.println("RA "+RA);
            System.out.println("Z "+ Z);
            System.out.println("RCODE " +RCODE);

            short QDCOUNT = dataInputStream.readShort();
            short ANCOUNT = dataInputStream.readShort();
            short NSCOUNT = dataInputStream.readShort();
            short ARCOUNT = dataInputStream.readShort();

            System.out.println("Questions: " + String.format("%s",QDCOUNT ));
            System.out.println("Answers RRs: " + String.format("%s", ANCOUNT));
            System.out.println("Authority RRs: " + String.format("%s", NSCOUNT));
            System.out.println("Additional RRs: " + String.format("%s", ARCOUNT));

            // check RDCODE to see if we have error (0 is no error), 1 or 3 should return to client, 2 should search next root address. 
            // if QR is 1 and ANCOUNT is 0 -> we don't have result so get next server address to go to.


            int wordLen;
            while ((wordLen = dataInputStream.readByte()) > 0) {
                for (int i = 0; i < wordLen; i++) {
                    dataInputStream.readByte();
                }
            }

            short QTYPE = dataInputStream.readShort();
            short QCLASS = dataInputStream.readShort();
            //System.out.println("Record: " + QNAME);
            System.out.println("Record Type: " + String.format("%s", QTYPE));
            System.out.println("Class: " + String.format("%s", QCLASS));

            System.out.println("\n\nstart answer, authority, and additional sections\n");

            for (int i = 0; i < ANCOUNT; i++) {
                skipQname(dataInputStream);
                ArrayList<Integer> RDATA = new ArrayList<>();
                short TYPE = dataInputStream.readShort();
                short CLASS = dataInputStream.readShort();
                int TTL = dataInputStream.readInt();
                int RDLENGTH = dataInputStream.readShort();
                for (int s = 0; s < RDLENGTH; s++) {
                    int nx = dataInputStream.readByte() & 255;// and with 255 to
                    RDATA.add(nx);
                }

                System.out.println("Type: " + TYPE);
                System.out.println("Class: " + CLASS);
                System.out.println("Time to live: " + TTL);
                System.out.println("Rd Length: " + RDLENGTH);
                StringBuilder ip = new StringBuilder();
                for (Integer ipPart:RDATA) {
                    ip.append(ipPart).append(".");
                }
                ip.deleteCharAt(ip.length() - 1);
                String ipFinal = ip.toString();
                System.out.println(ipFinal);
            }
            System.out.println("\n");
            // holds NS records -> skip if we have A records in the additional section
            // otherwise need to process one of the NS records
            int nsRecordCount = 0;
            for (int i = 0; i < NSCOUNT; i++) {
                skipQname(dataInputStream);
                short TYPE = dataInputStream.readShort();
                if (TYPE != 2) {
                    skipOtherRecordTypes(dataInputStream);
                    continue;
                }
                nsRecordCount++;
                short CLASS = dataInputStream.readShort();
                int TTL = dataInputStream.readInt();
                int RDLENGTH = dataInputStream.readShort();

                System.out.println("Type: " + TYPE);
                System.out.println("Class: " + CLASS);
                System.out.println("Time to live: " + TTL);
                System.out.println("Rd Length: " + RDLENGTH);
                byte[] rdata = new byte[RDLENGTH];
                dataInputStream.readFully(rdata);
                String NSRecord = getNSRecord(rdata, 0, response);
                System.out.println(NSRecord);
                System.out.println("\n");
            }

            // usually holds AAAA and A records
            // skip all AAAA records and query first A record we come across. 
            int aRecordCount = 0;
            for (int i = 0; i < ARCOUNT; i++) {
                skipQname(dataInputStream);
                ArrayList<Integer> RDATA = new ArrayList<>();
                short TYPE = dataInputStream.readShort();
                // some support to skip AAAA records if we come across them
                if (TYPE != 1) {
                    skipOtherRecordTypes(dataInputStream);
                    continue;
                }
                aRecordCount++;
                short CLASS = dataInputStream.readShort();
                int TTL = dataInputStream.readInt();
                int RDLENGTH = dataInputStream.readShort();
                for(int s = 0; s < RDLENGTH; s++) {
                    int nx = dataInputStream.readByte() & 255;// and with 255 to
                    RDATA.add(nx);
                }

                System.out.println("Type: " + TYPE);
                System.out.println("Class: " + CLASS);
                System.out.println("Time to live: " + TTL);
                System.out.println("Rd Length: " + RDLENGTH);
                StringBuilder ip = new StringBuilder();
                for (Integer ipPart:RDATA) {
                    ip.append(ipPart).append(".");
                }
                ip.deleteCharAt(ip.length() - 1);
                String ipFinal = ip.toString();
                System.out.println(ipFinal);
            }
            
        }
    }
        // processing the request
        // if error:
        // server error -> try different root server
        // all other errors -> return packet to client
        // else:
        // if not answer -> check if anything in additional section
        // if we have > 0 in additional section get that IP and send the next query
        // if we have = 0 then send A packet for record in authoritative section
        // if answer -> send to client

        // have a read only list of all the root servers
        // have a lsit of name servers to look up to find the result. 
        // when we use an ip address we can pop from the back and use the next one
        // upon a successful non-answer query we add the new ip address to the end. 
        // if we reach a situation where the list is empty we can query the next root server. 


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

    private static List<String> extractRootAddresses(String filePath) {
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

    private static byte[] sendAnswerPacket(ArrayList<String> ipAddresses, byte[] data, DatagramSocket socket) throws Exception {
        while (!ipAddresses.isEmpty()) {
            // pop from list
            InetAddress sendAddress = InetAddress.getByName(ipAddresses.remove(ipAddresses.size() - 1));
            // send packet
            DatagramPacket rep = new DatagramPacket(data, data.length, sendAddress, DNS_SERVER_PORT);
            socket.send(rep);
            byte[] response = new byte[1024];
            DatagramPacket packet = new DatagramPacket(response, response.length);
            socket.receive(packet);
            // decode and add to the list if possible (or return)
            DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));
            System.out.println("Transaction ID: " + dataInputStream.readShort()); // ID
            short flags = dataInputStream.readByte();
            flags = dataInputStream.readByte();
            int RCODE = flags & 0b00001111; // for errors
            short QDCOUNT = dataInputStream.readShort();
            short ANCOUNT = dataInputStream.readShort();
            short NSCOUNT = dataInputStream.readShort();
            short ARCOUNT = dataInputStream.readShort();
            // send packet to client and continue
            if (checkOtherErrors(RCODE)) {
                 
            }
        }
        return null;
    }
}
