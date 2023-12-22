import java.io.*;
import java.net.*;

public class FileReceiver {

    enum State {
        WAIT_FOR_SEQ_0, WAIT_FOR_SEQ_1
    }


    private final DatagramSocket socket;
    private State state;
    private final FileOutputStream fileOutputStream;

    public FileReceiver() throws IOException {
        // Assuming the same port as in FileSender
        int port = 9876;
        this.socket = new DatagramSocket(port);
        this.state = State.WAIT_FOR_SEQ_0;
        File receivedDir = new File("received");
        if (!receivedDir.exists()) {
            boolean created = receivedDir.mkdir(); // Create 'received' directory if it does not exist
            if (!created){
                System.out.println("Failed to create the directory. Check your rights.");
            }
        }
        File file = new File(receivedDir, "received_file");
        this.fileOutputStream = new FileOutputStream(file);
    }

    private boolean isChecksumValid(byte data, byte checksum) {
        // simple checksum validation (for illustration)
        return (data % 256) == checksum;
    }

    private void sendAck(int seqNum) throws IOException {

        byte[] ackPacket = new byte[]{'A', (byte) seqNum};
        InetAddress ipAddress = InetAddress.getByName("127.0.0.1"); // Assuming localhost for ACKs
        int ackPort = 9877;
        DatagramPacket packet = new DatagramPacket(ackPacket, ackPacket.length, ipAddress, ackPort);
        System.out.println("Sending ACK " + seqNum);
        socket.send(packet);
    }

    public void receiveFile() throws IOException {
        byte[] receiveData = new byte[3]; // Assuming packet structure [SEQ_NUM, DATA, CHECKSUM]

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            socket.receive(receivePacket);

            byte seqNum = receiveData[0];
            byte data = receiveData[1];
            byte checksum = receiveData[2];
            System.out.println("Received Packet with seqNum " + seqNum);
            System.out.println("PacketData = " + data);
            if (seqNum == 0 || seqNum == 1) {
                switch (state) {
                    case WAIT_FOR_SEQ_0:
                        System.out.println("Waiting for SEQ 0");
                        if (seqNum == 0 && isChecksumValid(data, checksum)) {
                            fileOutputStream.write(data);
                            sendAck(0);
                            state = State.WAIT_FOR_SEQ_1;
                        } else if (seqNum == 1 && isChecksumValid(data, checksum)) {
                            System.out.println("resending ack");
                            sendAck(1);
                        }
                        break;
                    case WAIT_FOR_SEQ_1:
                        System.out.println("Waiting for SEQ 1");
                        if (seqNum == 1 && isChecksumValid(data, checksum)) {
                            fileOutputStream.write(data);
                            sendAck(1);
                            state = State.WAIT_FOR_SEQ_0;
                        } else if (seqNum == 0 && isChecksumValid(data, checksum)) {
                            System.out.println("resending ack");
                            sendAck(0);
                        }

                        break;
                }
            }
        }
        // fileOutputStream.close(); // You can close the stream when you decide to end the loop
    }

    public static void main(String[] args) throws IOException {
        FileReceiver receiver = new FileReceiver();
        receiver.receiveFile();
    }
}
