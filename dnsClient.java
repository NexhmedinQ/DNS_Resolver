import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class dnsClient {
    private static final int NAME_ERROR = 3;
    private static final int FORMAT_ERROR = 2;
    private static final int SERVER_ERROR = 0;


    public static void main(String[] args) throws Exception {
        
        if (args.length != 3) {
            System.err.println("Error: invalid arguments");
            System.err.println("Usage: client resolver_ip resolver_port name");
            System.exit(1);
        }
        String resolverAddr = args[0];
        int resolverPort = Integer.parseInt(args[1]);
		String requestAddr = args[2];
        
        InetAddress ipAddress = InetAddress.getByName(resolverAddr);
        DatagramSocket socket = new DatagramSocket();
        
        Random random = new Random();
        // Generate a random short value
        short requestID = (short) random.nextInt(Short.MAX_VALUE + 1);
        byte [] byteArray = buildPacket(requestID, requestAddr);
        DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, ipAddress, resolverPort);
        socket.send(packet);

        byte[] response = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        socket.receive(responsePacket);
        DataInputStream dataInputStream = new DataInputStream(new ByteArrayInputStream(response));

        short queryID = dataInputStream.readShort();
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
            System.out.println("Error: server error (RCODE == 1)");
        }
        // need to parse packet -> first check for errors
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
}
