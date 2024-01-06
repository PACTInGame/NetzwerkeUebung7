import java.io.*;
import java.net.*;

public class FileSender {

    enum State {
        WAIT_FOR_CALL_0, WAIT_FOR_ACK_0, WAIT_FOR_CALL_1, WAIT_FOR_ACK_1
    }


    private final DatagramSocket socket;
    private InetAddress ipAddress;
    private State state = State.WAIT_FOR_CALL_0;

    public FileSender() throws SocketException {
        int ackPort = 9877;
        this.socket = new DatagramSocket(ackPort);
        int timeoutMS = 40;
        this.socket.setSoTimeout(timeoutMS);
    }

    public void send(String fileName, String ipAddress) throws IOException {
        this.ipAddress = InetAddress.getByName(ipAddress);
        send_file(fileName);
    }

    private int createChecksumForData(byte data) {
        // simple checksum logic (for illustration)
        return data % 256;
    }

    private byte[] makePacket(int seqNum, byte data, int checksum) {
        return new byte[]{(byte) seqNum, data, (byte) checksum};
    }

    private void sendPacket(byte[] packet) throws IOException {
        System.out.println("Sending Packet with seqNum" + packet[0]);
        int port = 9876;
        DatagramPacket datagramPacket = new DatagramPacket(packet, packet.length, ipAddress, port);
        socket.send(datagramPacket);
    }

    private void send_file(String fileName) throws IOException {
        FileInputStream fis = new FileInputStream(fileName);
        int seqNum = 0;
        int dataByte;
        while ((dataByte = fis.read()) != -1) {
            byte data = (byte) dataByte;
            int checksum = createChecksumForData(data);
            byte[] packet = makePacket(seqNum, data, checksum);
            boolean receivedAck = false;
            switch (state) {
                case WAIT_FOR_CALL_0:
                    while (!receivedAck) {
                        sendPacket(packet);
                        state = State.WAIT_FOR_ACK_0;
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
        System.out.println("Waiting to receive ACK " + expectedSeqNum);
        byte[] ackPacket = new byte[2];
        DatagramPacket packet = new DatagramPacket(ackPacket, ackPacket.length);
        try {
            socket.receive(packet);
            // Simple ACK format: [ACK, SEQ_NUM]
            return ackPacket[0] == 'A' && ackPacket[1] == (byte) expectedSeqNum;
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout occurred");
            return false; // Timeout occurred
        } catch (IOException e) {
            return false;
        }
    }

    public static void main(String[] args) throws IOException {
        FileSender fileSender = new FileSender();
        fileSender.send("longTest.txt", "192.168.0.11");
    }
}
