import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
public class UDPClient
{
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

    private static void downloadFile(DatagramSocket socket, InetAddress serverAddress, int serverPort, String filename) {}
    private static void sendAndReceive(){}



}


