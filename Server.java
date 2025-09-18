import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final List<BufferedWriter> clients = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(1234);
        System.out.println("Server started on port 1234...");

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket);

                // Create a new thread to handle the client
                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Class to handle each client in a separate thread
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader bufferedReader;
        private BufferedWriter bufferedWriter;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                synchronized (clients) {
                    clients.add(bufferedWriter);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    String msgFromClient = bufferedReader.readLine();
                    if (msgFromClient == null || msgFromClient.equalsIgnoreCase("BYE")) {
                        break;
                    }
                    System.out.println("Client: " + msgFromClient);
                    broadcastMessage(msgFromClient);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    synchronized (clients) {
                        clients.remove(bufferedWriter);
                    }
                    if (bufferedReader != null) bufferedReader.close();
                    if (bufferedWriter != null) bufferedWriter.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // Broadcast message to all other clients
        private void broadcastMessage(String message) throws IOException {
            synchronized (clients) {
                for (BufferedWriter client : clients) {
                    if (client != bufferedWriter) { // Don't send to self
                        client.write(message);
                        client.newLine();
                        client.flush();
                    }
                }
            }
        }
    }
}