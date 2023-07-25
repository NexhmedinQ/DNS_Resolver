import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class dnsClient {
    
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
        // System.out.println("Sending: " + byteArray.length + " bytes");
        // for (int i = 0; i < byteArray.length; i++) {
        //     System.out.print(String.format("%s", byteArray[i]));
        // }



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
