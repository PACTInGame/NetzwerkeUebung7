import java.io.*;
import java.net.*;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class FileSender {

    enum State {
        WAIT_FOR_CALL_0, WAIT_FOR_ACK_0, WAIT_FOR_CALL_1, WAIT_FOR_ACK_1
    }


    private final DatagramSocket socket;
    private InetAddress ipAddress;
    private State state = State.WAIT_FOR_CALL_0;
    private final boolean debug = true;

    public FileSender() throws SocketException {
        int ackPort = 9877;
        this.socket = new DatagramSocket(ackPort);
        int timeoutMS = 100;
        this.socket.setSoTimeout(timeoutMS);
    }

    public void send(String fileName, String ipAddress) throws IOException {
        this.ipAddress = InetAddress.getByName(ipAddress);
        send_file(fileName);
    }

    private long createChecksumForData(byte[] data) {
        Checksum checksum = new Adler32();

        checksum.update(data, 0, data.length);

        return checksum.getValue();

    }

    private byte[] makePacket(int seqNum, byte[] data, long checksum) {
        byte[] result = new byte[data.length + 2];
        result[0] = (byte) seqNum;
        result[1] = (byte) checksum;
        System.arraycopy(data, 0, result, 2, data.length);
        return result;
    }

    private void sendPacket(byte[] packet) throws IOException {
        if (debug) {
            System.out.println("Sending Packet with seqNum" + packet[0]);
            System.out.println("Packet looks like:" + packet[0]+ packet[1]+ packet[2]);
            System.out.println(packet.length);
        }
        int port = 9876;
        DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, ipAddress, port);
        socket.send(datagramPacket);
    }

    private void send_file(String fileName) throws IOException {
        FileInputStream fis = new FileInputStream(fileName);
        int seqNum = 0;
        int dataByte;
        byte[] sendData = new byte[500];
        while ((dataByte = fis.read()) != -1) {
            sendData[0] = (byte) dataByte;
            int packetPos = 1;
            while ((dataByte = fis.read()) != - 1 && packetPos < 500)
                sendData[packetPos++] = (byte) dataByte;

            long checksum = createChecksumForData(sendData);
            byte[] packet = makePacket(seqNum, sendData, checksum);
            boolean receivedAck = false;
            switch (state) {
                case WAIT_FOR_CALL_0:
                    while (!receivedAck) {
                        sendPacket(packet);
                        state = State.WAIT_FOR_ACK_0;
                        if (debug)
                            System.out.println("State: WAIT_FOR_ACK_0");
                        if (receiveAck(0)) {
                            state = State.WAIT_FOR_CALL_1;
                            seqNum = 1 - seqNum;
                            receivedAck = true;
                        }
                    }
                    break;

                case WAIT_FOR_CALL_1:
                    while (!receivedAck) {
                        sendPacket(packet);
                        state = State.WAIT_FOR_ACK_1;
                        if (debug)
                            System.out.println("State: WAIT_FOR_ACK_1");
                        if (receiveAck(1)) {
                            state = State.WAIT_FOR_CALL_0;
                            seqNum = 1 - seqNum;
                            receivedAck = true;
                        }
                    }
                    break;

            }

        }
        fis.close();
    }

    private boolean receiveAck(int expectedSeqNum) {
        if (debug)
            System.out.println("Waiting to receive ACK " + expectedSeqNum);
        byte[] ackPacket = new byte[2];
        DatagramPacket packet = new DatagramPacket(ackPacket, ackPacket.length);
        try {
            socket.receive(packet);
            // Simple ACK format: [ACK, SEQ_NUM]
            return ackPacket[0] == 'A' && ackPacket[1] == (byte) expectedSeqNum;
        } catch (SocketTimeoutException e) {
            if (debug)
                System.out.println("Timeout occurred");
            return false; // Timeout occurred
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) throws IOException {
        FileSender fileSender = new FileSender();
        fileSender.send("longTest.txt", "127.0.0.1");
    }
}
