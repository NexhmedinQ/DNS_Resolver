import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public class Client {
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
        byte [] byteArray = Helper.buildPacket(requestID, requestAddr);
        DatagramPacket packet = new DatagramPacket(byteArray, byteArray.length, ipAddress, resolverPort);
        long currentTimeMillis = System.nanoTime();
        socket.send(packet);

        byte[] response = new byte[1024];
        DatagramPacket responsePacket = new DatagramPacket(response, response.length);
        try {
            socket.receive(responsePacket);
        } catch (SocketTimeoutException e) {
            System.out.println("The query has timed out");
            socket.close();
            return;
        }
        long recTime = System.nanoTime();
        long timeTaken = recTime - currentTimeMillis;
        String filePath = "data.txt";
        double millis = (double) timeTaken / 1000000.0;

        // Convert to a string with 3 decimal places
        String formattedTime = String.format("%.3f", millis);
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file, true);

            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            bufferedWriter.write(formattedTime);
            bufferedWriter.newLine();

            bufferedWriter.close();
            fileWriter.close();

            System.out.println("Data appended to the file successfully.");
        } catch (IOException e) {
            System.err.println("Error appending data to the file: " + e.getMessage());
        }
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
        Helper.skipQuestionSection(dataInputStream);
        ArrayList<String> address = Helper.getAnswers(dataInputStream, ANCOUNT);
        System.out.println(address + ", truncation bit is " + TC + " authoritative answer bit is " + AA);
        socket.close();
	}
}
