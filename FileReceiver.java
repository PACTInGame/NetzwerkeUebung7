import java.io.*;
import java.net.*;
import java.util.Random;
import java.util.zip.Adler32;
import java.util.zip.Checksum;

public class FileReceiver {

    enum State {
        WAIT_FOR_SEQ_0, WAIT_FOR_SEQ_1
    }

    private final boolean debug = true;

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
            if (!created) {
                System.out.println("Failed to create the directory. Check your rights.");
            }
        }
        File file = new File(receivedDir, "received_file");
        this.fileOutputStream = new FileOutputStream(file);
    }

    private boolean isChecksumValid(byte[] data, long checksum) {
        Checksum checksumAdler = new Adler32();
        checksumAdler.update(data, 0, data.length);
        System.out.println("ChecksumAdler=" + checksumAdler.getValue());
        System.out.println("Checksum rcvd=" + checksum);
        return (byte) checksumAdler.getValue() == checksum;

    }
    private void sendAck(int seqNum) throws IOException {

        byte[] ackPacket = new byte[]{'A', (byte) seqNum};
        InetAddress ipAddress = InetAddress.getByName("127.0.0.1"); // Assuming localhost for ACKs
        int ackPort = 9877;
        DatagramPacket packet = new DatagramPacket(ackPacket, ackPacket.length, ipAddress, ackPort);
        if (debug)
            System.out.println("Sending ACK " + seqNum);
        socket.send(packet);
    }

    private int badlyBehavedPipe(byte[] receivePacket, int percentageBitFault, int percentageLost, int percentageDuplicate) {

        Random rand = new Random();
        int result = 0;
        // Check for lost packet
        if (rand.nextInt(100) < percentageLost || receivePacket == null) {
            // Implement the logic for a lost packet here
            result = result + 1;
        }

        // Check for bit fault
        if (rand.nextInt(100) < percentageBitFault) {
            // Implement the logic for a bit fault here
            // Example: increase the second byte by 1
            result = result + 2;
        }

        // Check for duplicate packet
        if (rand.nextInt(100) < percentageDuplicate) {
            // Implement the logic for a duplicate packet here
            // Example: return a new array that duplicates the packet
            result = result + 4;
        }

        // Return the modified packet if no duplication occurred
        return result;
    }

    public void processReceivedData(byte[] receiveData) throws IOException {
        byte seqNum = receiveData[0];
        byte checksum = receiveData[1];
        int length = receiveData.length-2;
        byte[] data = new byte[length];
        System.out.println("Packet looks like:" + receiveData[0]+ receiveData[1]+ receiveData[2]);
        System.arraycopy(receiveData, 2, data, 0, length);
        System.out.println(receiveData.length);
        if (debug) {
            System.out.println("Received Packet with seqNum " + seqNum);
            //System.out.println("PacketData = " + data);
        }
        if (seqNum == 0 || seqNum == 1) {
            switch (state) {
                case WAIT_FOR_SEQ_0:
                    if (debug)
                        System.out.println("Waiting for SEQ 0");
                    if (seqNum == 0 && isChecksumValid(data, checksum)) {
                        fileOutputStream.write(data);
                        sendAck(0);
                        state = State.WAIT_FOR_SEQ_1;
                    } else if (seqNum == 1 && isChecksumValid(data, checksum)) {
                        sendAck(1);
                    }
                    break;
                case WAIT_FOR_SEQ_1:
                    if (debug)
                        System.out.println("Waiting for SEQ 1");
                    if (seqNum == 1 && isChecksumValid(data, checksum)) {
                        fileOutputStream.write(data);
                        sendAck(1);
                        state = State.WAIT_FOR_SEQ_0;
                    } else if (seqNum == 0 && isChecksumValid(data, checksum)) {
                        sendAck(0);
                    }

                    break;
            }
        }
    }
    public void receiveFile() throws IOException {
        byte[] receiveData = new byte[502]; // Assuming packet structure [SEQ_NUM, CHECKSUM,  DATA[500]]
        long receive_time_start = 0;
        long receive_time_end;
        socket.setSoTimeout(5000);
        while (true) {

            DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try{
                socket.receive(receivePacket);
                if (receive_time_start == 0){
                    receive_time_start = System.currentTimeMillis();
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Receiving took:");
                receive_time_end = System.currentTimeMillis() - 5000;
                System.out.println(receive_time_end-receive_time_start + "ms");
                System.out.println("Therefore Goodput is: " + 1955/((receive_time_end-receive_time_start)/1000) + "KB/s");
                break;
            }

            int result = badlyBehavedPipe(receiveData, 0, 0, 0);
            final int LOST_FLAG = 1;
            final int FAULT_FLAG = 2;
            final int DUPLICATE_FLAG = 4;
            if ((result & FAULT_FLAG) != 0){
                receiveData[1] = (byte) (receiveData[1] + 2);
            }
            if ((result & DUPLICATE_FLAG) != 0){
                processReceivedData(receiveData);
                processReceivedData(receiveData);
            }
            if ((result & LOST_FLAG) == 0) {
                processReceivedData(receiveData);
            }
        }
        // fileOutputStream.close(); // You can close the stream when you decide to end the loop
    }

    public static void main(String[] args) throws IOException {
        FileReceiver receiver = new FileReceiver();
        receiver.receiveFile();
    }
}
