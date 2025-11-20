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

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                // Register client
                registerClient();
            } catch (IOException | SQLException e) {
                e.printStackTrace();
            }
        }

        private void registerClient() throws IOException, SQLException {
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
            sendHistory(); // Отправляем историю сообщений при подключении
            broadcastSystem(clientName + " joined the chat!");
        }

        private void sendHistory() throws IOException {
            try {
                String sql = "SELECT sender_name, message_text, timestamp FROM Messages WHERE recipient_id IS NULL ORDER BY timestamp ASC";
                PreparedStatement stmt = dbConnection.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery();
                bufferedWriter.write("--- История сообщений ---");
                bufferedWriter.newLine();
                bufferedWriter.flush();
                while (rs.next()) {
                    String sender = rs.getString("sender_name");
                    String text = rs.getString("message_text");
                    String time = rs.getTimestamp("timestamp").toString();
                    bufferedWriter.write("[" + time + "] " + sender + ": " + text);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                }
                bufferedWriter.write("--- Конец истории ---");
                bufferedWriter.newLine();
                bufferedWriter.flush();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            try {
                String msgFromClient;
                while ((msgFromClient = bufferedReader.readLine()) != null) {
                    if (msgFromClient.equalsIgnoreCase("BYE")) {
                        break;
                    }
                    if (msgFromClient.isBlank()) continue;

                    System.out.println(clientName + ": " + msgFromClient);

                    if (msgFromClient.startsWith("@")) {
                        // Private message
                        String[] parts = msgFromClient.split(" ", 2);
                        String recipient = parts[0].substring(1);
                        String text = parts.length > 1 ? parts[1].trim() : "";
                        if (!text.isEmpty()) {
                            sendToSpecific(clientName + ": " + text, recipient);
                            saveMessage(text, recipient);
                        }
                    } else {
                        // Broadcast
                        broadcastMessage(clientName + ": " + msgFromClient);
                        saveMessage(msgFromClient, null);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    synchronized (clients) {
                        clients.remove(this);
                    }
                    if (bufferedReader != null) bufferedReader.close();
                    if (bufferedWriter != null) bufferedWriter.close();
                    if (socket != null) socket.close();
                    if (clientName != null) {
                        broadcastSystem(clientName + " left the chat.");
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void saveMessage(String message, String recipientName) throws IOException {
            try {
                PreparedStatement stmt;
                if (recipientName == null) {
                    stmt = dbConnection.prepareStatement("INSERT INTO Messages (sender_id, sender_name, message_text) VALUES (?, ?, ?)");
                    stmt.setInt(1, clientId);
                    stmt.setString(2, clientName);
                    stmt.setString(3, message);
                } else {
                    stmt = dbConnection.prepareStatement("SELECT client_id FROM Clients WHERE client_name = ?");
                    stmt.setString(1, recipientName);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        int recipientId = rs.getInt("client_id");
                        stmt = dbConnection.prepareStatement("INSERT INTO Messages (sender_id, sender_name, recipient_id, message_text) VALUES (?, ?, ?, ?)");
                        stmt.setInt(1, clientId);
                        stmt.setString(2, clientName);
                        stmt.setInt(3, recipientId);
                        stmt.setString(4, message);
                    } else {
                        bufferedWriter.write("Recipient not found: " + recipientName);
                        bufferedWriter.newLine();
                        bufferedWriter.flush();
                        return;
                    }
                }
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        private void broadcastMessage(String message) throws IOException {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        client.bufferedWriter.write(message);
                        client.bufferedWriter.newLine();
                        client.bufferedWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void sendToSpecific(String message, String recipientName) throws IOException {
            synchronized (clients) {
                boolean found = false;
                for (ClientHandler client : clients) {
                    if (client.clientName.equals(recipientName)) {
                        client.bufferedWriter.write("(private) " + message);
                        client.bufferedWriter.newLine();
                        client.bufferedWriter.flush();
                        found = true;
                        break;
                    }
                }
                if (found) {
                    bufferedWriter.write("(to " + recipientName + ") " + message);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                } else {
                    bufferedWriter.write("Recipient not found: " + recipientName);
                    bufferedWriter.newLine();
                    bufferedWriter.flush();
                }
            }
        }

        private void broadcastSystem(String message) throws IOException {
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        client.bufferedWriter.write("*** " + message + " ***");
                        client.bufferedWriter.newLine();
                        client.bufferedWriter.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}