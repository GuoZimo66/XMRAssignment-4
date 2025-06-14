import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;

public class UDPClient {
    private static final int MAX_RETRIES = 5;
    private static final int INITIAL_TIMEOUT = 1000; // 1 second
    private static final int MAX_BLOCK_SIZE = 1000;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java UDPClient <hostname> <port> <filelist>");
            return;
        }

        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        String fileListName = args[2];

        try {
            List<String> filesToDownload = Files.readAllLines(Paths.get(fileListName));
            DatagramSocket socket = new DatagramSocket();
            InetAddress serverAddress = InetAddress.getByName(hostname);

            for (String filename : filesToDownload) {
                downloadFile(socket, serverAddress, port, filename.trim());
            }

            socket.close();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, int serverPort, String filename) {
        try {
            System.out.print("Requesting " + filename + "... ");

            // Send DOWNLOAD request
            String downloadMsg = "DOWNLOAD " + filename;
            DatagramPacket downloadPacket = new DatagramPacket(
                    downloadMsg.getBytes(), downloadMsg.length(), serverAddress, serverPort);

            String response = sendAndReceive(socket, downloadPacket, serverAddress, serverPort);
            String[] parts = response.split(" ");

            if (parts[0].equals("ERR")) {
                System.out.println("Error: " + parts[2]);
                return;
            }

            if (!parts[0].equals("OK")) {
                System.out.println("Invalid response from server");
                return;
            }

            long fileSize = Long.parseLong(parts[3]);
            int dataPort = Integer.parseInt(parts[5]);
            System.out.println("OK (" + fileSize + " bytes)");

            // Create local file
            RandomAccessFile file = new RandomAccessFile(filename, "rw");
            file.setLength(fileSize);

            // Download file blocks
            System.out.print("Downloading [");
            long bytesReceived = 0;
            while (bytesReceived < fileSize) {
                long end = Math.min(bytesReceived + MAX_BLOCK_SIZE - 1, fileSize - 1);
                String blockRequest = "FILE " + filename + " GET START " + bytesReceived + " END " + end;

                DatagramPacket requestPacket = new DatagramPacket(
                        blockRequest.getBytes(), blockRequest.length(), serverAddress, dataPort);

                String blockResponse = sendAndReceive(socket, requestPacket, serverAddress, dataPort);
                String[] blockParts = blockResponse.split(" ");

                if (!blockParts[0].equals("FILE") || !blockParts[1].equals(filename) || !blockParts[2].equals("OK")) {
                    System.out.println("\nInvalid block response");
                    file.close();
                    return;
                }

                long start = Long.parseLong(blockParts[4]);
                end = Long.parseLong(blockParts[6]);
                String base64Data = blockResponse.substring(blockResponse.indexOf("DATA") + 5).trim();
                byte[] data = Base64.getDecoder().decode(base64Data);

                file.seek(start);
                file.write(data);
                bytesReceived += data.length;

                System.out.print("*");
            }

            // Send CLOSE message
            String closeMsg = "FILE " + filename + " CLOSE";
            DatagramPacket closePacket = new DatagramPacket(
                    closeMsg.getBytes(), closeMsg.length(), serverAddress, dataPort);

            sendAndReceive(socket, closePacket, serverAddress, dataPort);

            file.close();
            System.out.println("] Done");
        } catch (IOException e) {
            System.err.println("Error downloading " + filename + ": " + e.getMessage());
        }
    }
}


