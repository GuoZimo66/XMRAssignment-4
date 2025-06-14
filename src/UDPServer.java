import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;

public class UDPServer {
    private static final int MIN_DATA_PORT = 50000;
    private static final int MAX_DATA_PORT = 51000;
    private static final int MAX_BLOCK_SIZE = 1000;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java UDPServer <port>");
            return;
        }

        int port = Integer.parseInt(args[0]);

        try (DatagramSocket socket = new DatagramSocket(port)) {
            System.out.println("Server started on port " + port);

            while (true) {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String request = new String(packet.getData(), 0, packet.getLength()).trim();
                String[] parts = request.split(" ");

                if (parts.length >= 2 && parts[0].equals("DOWNLOAD")) {
                    String filename = parts[1];
                    File file = new File(filename);

                    if (!file.exists() || !file.isFile()) {
                        String errorMsg = "ERR " + filename + " NOT_FOUND";
                        DatagramPacket errorPacket = new DatagramPacket(
                                errorMsg.getBytes(), errorMsg.length(),
                                packet.getAddress(), packet.getPort());
                        socket.send(errorPacket);
                        continue;
                    }

                    // Create new thread to handle file transfer
                    new Thread(() -> {
                        try {
                            handleFileTransfer(file, packet.getAddress(), packet.getPort());
                        } catch (IOException e) {
                            System.err.println("Error handling file transfer: " + e.getMessage());
                        }
                    }).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void handleFileTransfer(File file, InetAddress clientAddress, int clientPort) throws IOException {
        Random random = new Random();
        int dataPort = MIN_DATA_PORT + random.nextInt(MAX_DATA_PORT - MIN_DATA_PORT + 1);

        try (DatagramSocket dataSocket = new DatagramSocket(dataPort);
             RandomAccessFile raf = new RandomAccessFile(file, "r")) {

            // Send OK response with file size and data port
            String okMsg = "OK " + file.getName() + " SIZE " + file.length() + " PORT " + dataPort;
            DatagramPacket okPacket = new DatagramPacket(
                    okMsg.getBytes(), okMsg.length(), clientAddress, clientPort);
            dataSocket.send(okPacket);

            byte[] buffer = new byte[2048];
            DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);

            while (true) {
                dataSocket.receive(requestPacket);
                String request = new String(requestPacket.getData(), 0, requestPacket.getLength()).trim();
                String[] parts = request.split(" ");

                if (parts.length >= 3 && parts[0].equals("FILE") && parts[2].equals("CLOSE")) {
                    String closeOkMsg = "FILE " + file.getName() + " CLOSE_OK";
                    DatagramPacket closeOkPacket = new DatagramPacket(
                            closeOkMsg.getBytes(), closeOkMsg.length(),
                            requestPacket.getAddress(), requestPacket.getPort());
                    dataSocket.send(closeOkPacket);
                    break;
                }

                if (parts.length >= 7 && parts[0].equals("FILE") && parts[2].equals("GET")) {
                    long start = Long.parseLong(parts[4]);
                    long end = Long.parseLong(parts[6]);
                    int blockSize = (int) (end - start + 1);

                    byte[] fileData = new byte[blockSize];
                    raf.seek(start);
                    raf.read(fileData);

                    String base64Data = Base64.getEncoder().encodeToString(fileData);
                    String responseMsg = "FILE " + file.getName() + " OK START " + start + " END " + end + " DATA " + base64Data;

                    DatagramPacket responsePacket = new DatagramPacket(
                            responseMsg.getBytes(), responseMsg.length(),
                            requestPacket.getAddress(), requestPacket.getPort());
                    dataSocket.send(responsePacket);
                }
            }
        }
    }
}