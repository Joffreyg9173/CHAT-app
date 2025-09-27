import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Server {
    private static final List<ClientHandler> clients = new ArrayList<>();
    private static Connection dbConnection;

    public static void main(String[] args) throws IOException, SQLException {
        // Initialize database connection
        dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_app", "root", "14789");
        System.out.println("Connected to database.");

        ServerSocket serverSocket = new ServerSocket(1234);
        System.out.println("Server started on port 1234...");

        while (true) {
            try {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected: " + socket);

                // Create a new thread to handle the client
                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
                synchronized (clients) {
                    clients.add(clientHandler);
                }
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
        private String clientName;
        private int clientId;
        private String currentRecipient; // null for broadcast, client name for specific

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                // Prompt for and register client name
                registerClient();
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }

        private void registerClient() throws IOException, SQLException {
            bufferedWriter.write("Enter your name: ");
            bufferedWriter.newLine();
            bufferedWriter.flush();

            clientName = bufferedReader.readLine();
            if (clientName == null || clientName.trim().isEmpty()) {
                clientName = "Anonymous_" + socket.getPort();
            }

            // Register client in database
            PreparedStatement stmt = dbConnection.prepareStatement("INSERT INTO Clients (client_name) VALUES (?) ON DUPLICATE KEY UPDATE client_name = client_name");
            stmt.setString(1, clientName);
            stmt.executeUpdate();

            // Get client ID
            stmt = dbConnection.prepareStatement("SELECT client_id FROM Clients WHERE client_name = ?");
            stmt.setString(1, clientName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                clientId = rs.getInt("client_id");
            }
        }

        @Override
        public void run() {
            try {
                while (true) {
                    // Prompt for broadcast or specific
                    bufferedWriter.write("Do you want to broadcast your message or send to a specific person? (broad/specific): ");
                    bufferedWriter.newLine();
                    bufferedWriter.flush();

                    String choice = bufferedReader.readLine();
                    if (choice == null) break;

                    if (choice.equalsIgnoreCase("broad")) {
                        currentRecipient = null; // Broadcast mode
                    } else if (choice.equalsIgnoreCase("specific")) {
                        bufferedWriter.write("Enter recipient name: ");
                        bufferedWriter.newLine();
                        bufferedWriter.flush();
                        currentRecipient = bufferedReader.readLine();
                        if (currentRecipient == null) break;
                    } else {
                        bufferedWriter.write("Invalid choice. Please enter 'broad' or 'specific'.");
                        bufferedWriter.newLine();
                        bufferedWriter.flush();
                        continue;
                    }

                    // Handle messages until STOP or disconnect
                    String msgFromClient = null;
                    while (true) {
                        msgFromClient = bufferedReader.readLine();
                        if (msgFromClient == null || msgFromClient.equalsIgnoreCase("BYE")) {
                            break;
                        }
                        if (msgFromClient.equalsIgnoreCase("STOP") && currentRecipient != null) {
                            currentRecipient = null; // Exit specific chat
                            break;
                        }

                        System.out.println(clientName + ": " + msgFromClient);
                        saveMessage(msgFromClient);
                        if (currentRecipient == null) {
                            broadcastMessage(clientName + ": " + msgFromClient);
                        } else {
                            sendToSpecific(clientName + ": " + msgFromClient, currentRecipient);
                        }
                    }
                    if (msgFromClient != null && msgFromClient.equalsIgnoreCase("BYE")) {
                        break;
                    }
                }
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            } finally {
                try {
                    synchronized (clients) {
                        clients.remove(this);
                    }
                    if (bufferedReader != null) bufferedReader.close();
                    if (bufferedWriter != null) bufferedWriter.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void saveMessage(String message) throws SQLException, IOException {
            PreparedStatement stmt = null;
            if (currentRecipient == null) {
                stmt = dbConnection.prepareStatement("INSERT INTO Messages (sender_id, message_text) VALUES (?, ?)");
                stmt.setInt(1, clientId);
                stmt.setString(2, message);
                stmt.executeUpdate();
            } else {
                stmt = dbConnection.prepareStatement("SELECT client_id FROM Clients WHERE client_name = ?");
                stmt.setString(1, currentRecipient);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    int recipientId = rs.getInt("client_id");
                    stmt = dbConnection.prepareStatement("INSERT INTO Messages (sender_id, recipient_id, message_text) VALUES (?, ?, ?)");
                    stmt.setInt(1, clientId);
                    stmt.setInt(2, recipientId);
                    stmt.setString(3, message);
                    stmt.executeUpdate();
                } else {
                    bufferedWriter.write("Recipient not found: " + currentRecipient);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                    return;
                }
            }
        }

        private void broadcastMessage(String message) throws IOException {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client != this) {
                        client.bufferedWriter.write(message);
                        client.bufferedWriter.newLine();
                        client.bufferedWriter.flush();
                    }
                }
            }
        }

        private void sendToSpecific(String message, String recipientName) throws IOException {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    if (client.clientName.equals(recipientName)) {
                        client.bufferedWriter.write(message);
                        client.bufferedWriter.newLine();
                        client.bufferedWriter.flush();
                        return;
                    }
                }
                bufferedWriter.write("Recipient not found: " + recipientName);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }
        }
    }
}