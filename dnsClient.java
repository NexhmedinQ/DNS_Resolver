import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class dnsClient {
    private static final int NAME_ERROR = 3;
    private static final int FORMAT_ERROR = 1;
    private static final int SERVER_ERROR = 2;


    public static void main(String[] args) throws Exception  {
        
        if (args.length != 4) {
            System.err.println("Error: invalid arguments");
            System.err.println("Usage: client resolver_ip resolver_port name timeout");
            System.exit(1);
        }
        String resolverAddr = args[0];
        int resolverPort = Integer.parseInt(args[1]);
		String requestAddr = args[2];
        int timeout = Integer.parseInt(args[3]) * 1000;
        
        InetAddress ipAddress = InetAddress.getByName(resolverAddr);
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(timeout);
        Random random = new Random();
        // Generate a random short value
        short requestID = (short) random.nextInt(Short.MAX_VALUE + 1);
        byte [] byteArray = buildPacket(requestID, requestAddr);
        System.out.println(byteArray.length);
        DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, ipAddress, resolverPort);
        socket.send(packet);

        byte[] response = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        try {
            socket.receive(responsePacket);
        } catch (SocketTimeoutException e) {
            System.out.println("TIMEOUT" + e.getMessage());
        }
        //socket.receive(responsePacket);
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));

        dataInputStream.skipBytes(2);
        short flags = dataInputStream.readByte();
        int AA = ( flags & 0b00000100) >>> 2;
        int TC = ( flags & 0b00000010) >>> 1;
        flags = dataInputStream.readByte();
        int RCODE = flags & 0b00001111; // for errors
        if (RCODE == NAME_ERROR) {
            System.out.println("Error: server can't find " + requestAddr);
        } else if (RCODE == FORMAT_ERROR) {
            System.out.println("Error: format error (RCODE == 1)");
        } else if (RCODE == SERVER_ERROR) {
            System.out.println("Error: server error (RCODE == 2)");
        }
        // exit upon error
        if (RCODE != 0) {
            socket.close();
            return;
        }
        dataInputStream.skipBytes(2);
        short ANCOUNT = dataInputStream.readShort();
        dataInputStream.skipBytes(4);
        skipQuestionSection(dataInputStream);
        ArrayList<String> address = getAnswers(dataInputStream, ANCOUNT);
        System.out.println(address + ", truncation bit is " + TC + " authoritative answer bit is " + AA);
        socket.close();
        // send all the answers (IP addresses), whether the answer was authoritative (check AA bit) and whether it was truncated (TC bit)
	}

    private static byte[] buildPacket(short requestID, String requestAddr) throws Exception {
        ByteArrayOutputStream byteArrayOutput = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(byteArrayOutput);
        short requestFlags = Short.parseShort("0000000000000000", 2);
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

    private static void skipQuestionSection(DataInputStream dataInputStream) throws IOException {
        int wordLen;
        while ((wordLen = dataInputStream.readByte()) > 0) {
            for (int i = 0; i < wordLen; i++) {
                dataInputStream.readByte();
            }
        }
        dataInputStream.skipBytes(4);
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
}
