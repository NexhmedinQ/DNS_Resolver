import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Helper {
    public static byte[] buildPacket(short requestID, String requestAddr) throws Exception {
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

    public static void skipQname(DataInputStream dataInputStream) throws IOException {
        int labelLength;
        while ((labelLength = dataInputStream.readUnsignedByte()) > 0) {
            if ((labelLength & 0xC0) == 0xC0) {
                dataInputStream.skipBytes(1);
                break;
            }
            dataInputStream.skipBytes(labelLength);
        }
    }

    public static void skipQuestionSection(DataInputStream dataInputStream) throws IOException {
        int wordLen;
        while ((wordLen = dataInputStream.readByte()) > 0) {
            for (int i = 0; i < wordLen; i++) {
                dataInputStream.readByte();
            }
        }

        dataInputStream.skipBytes(4);
    }

    public static ArrayList<String> getAnswers(DataInputStream dataInputStream, short ANCOUNT) throws IOException {
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
