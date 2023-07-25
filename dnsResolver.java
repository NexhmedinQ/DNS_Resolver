import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
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
            System.out.println("GOT IT");
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


            for (int i = 0; i < NSCOUNT; i++) {
                skipQname(dataInputStream);
                ArrayList<Integer> RDATA = new ArrayList<>();
                short TYPE = dataInputStream.readShort();
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

            for (int i = 0; i < ARCOUNT; i++) {
                skipQname(dataInputStream);
                ArrayList<Integer> RDATA = new ArrayList<>();
                short TYPE = dataInputStream.readShort();
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


		// bind resolver to port 5300 to listen
        // keep it in an infinite loop
        // when a new request is sent create a new thread to process the request


        // processing the request
        // if error:
        // server error -> try different root server
        // all other errors -> return packet to client
        // else:
        // if not answer -> check if anything in additional section
        // if we have > 0 in additional section get that IP and send the next query
        // if we have = 0 then send A packet for record in authoritative section
        // if answer -> send to client
	
    private static void printData(DatagramPacket request) throws Exception
   {
      // Obtain references to the packet's array of bytes.
      byte[] buf = request.getData();

      // Wrap the bytes in a byte array input stream,
      // so that you can read the data as a stream of bytes.
      ByteArrayInputStream bais = new ByteArrayInputStream(buf);

      // Wrap the byte array output stream in an input stream reader,
      // so you can read the data as a stream of characters.
      InputStreamReader isr = new InputStreamReader(bais);

      // Wrap the input stream reader in a bufferred reader,
      // so you can read the character data a line at a time.
      // (A line is a sequence of chars terminated by any combination of \r and \n.) 
      BufferedReader br = new BufferedReader(isr);

      // The message data is contained in a single line, so read this line.
      String line = br.readLine();

      // Print host address and data received from it.
      System.out.println(
         "Received from " + 
         request.getAddress().getHostAddress() + 
         ": " +
         new String(line) );
   }

    private static void skipQname(DataInputStream dataInputStream) throws IOException {
        int labelLength;
        while ((labelLength = dataInputStream.readUnsignedByte()) > 0) {
        // If the first two bits of label length are 11 (11000000 in binary), it's a compression pointer.
            if ((labelLength & 0xC0) == 0xC0) {
                dataInputStream.skipBytes(1);
                break;
            }
            dataInputStream.skipBytes(labelLength);
        }
    }
}
