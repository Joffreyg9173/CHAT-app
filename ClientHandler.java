import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClientHandler implements Runnable {

    private static final List<ClientHandler> clients = new ArrayList<>();
    private static Connection dbConnection;

    private final Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String clientName;
    private int clientId;
    private boolean isAuthenticated = false;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            closeEverything();
        }
    }

    @Override
    public void run() {
        try {
            authenticate();
            if (!isAuthenticated) return;

            synchronized (clients) { clients.add(this); }
            broadcastSystem(clientName + " joined the chat!");
            sendHistory();
            broadcastOnlineList();

            String message;
            while ((message = in.readLine()) != null) {
                if (message.isBlank()) continue;

                if (message.equalsIgnoreCase("BYE")) break;
                if (message.equalsIgnoreCase("/users")) {
                    broadcastOnlineList();
                    continue;
                }

                if (message.startsWith("@")) {
                    String[] parts = message.split(" ", 2);
                    String recipient = parts[0].substring(1);
                    String text = parts.length > 1 ? parts[1].trim() : "";
                    if (!text.isEmpty()) {
                        sendPrivate(clientName + ": " + text, recipient);
                        saveMessage(text, recipient);
                    }
                } else {
                    String fullMsg = clientName + ": " + message;
                    broadcastMessage(fullMsg);
                    saveMessage(message, null);
                }
            }
        } catch (IOException e) {
            System.out.println(clientName + " disconnected.");
        } finally {
            logout();
        }
    }

    private void authenticate() {
        try {
            String choice = in.readLine();

            if ("1".equals(choice)) login();
            else if ("2".equals(choice)) register();
            else {
                out.write("Invalid choice.\n"); out.flush();
                isAuthenticated = false;
            }
        } catch (IOException e) {
            isAuthenticated = false;
        }
    }

    private void login() {
        try {
            clientName = in.readLine();
            String password = in.readLine();

            PreparedStatement ps = dbConnection.prepareStatement(
                    "SELECT client_id FROM Clients WHERE client_name = ? AND password = ?");
            ps.setString(1, clientName);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                clientId = rs.getInt("client_id");
                out.write("Welcome, " + clientName + "!\n"); out.flush();
                isAuthenticated = true;
            } else {
                out.write("Invalid login.\n"); out.flush();
                isAuthenticated = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try { out.write("Error.\n"); out.flush(); } catch (IOException ignored) {}
            isAuthenticated = false;
        }
    }

    private void register() {
        try {
            clientName = in.readLine();
            String password = in.readLine();

            PreparedStatement ps = dbConnection.prepareStatement(
                    "INSERT INTO Clients (client_name, password) VALUES (?, ?)");
            ps.setString(1, clientName);
            ps.setString(2, password);
            ps.executeUpdate();
            clientId = getUserId(clientName);
            out.write("Registered as " + clientName + "!\n"); out.flush();
            isAuthenticated = true;
        } catch (SQLException e) {
            e.printStackTrace();
            try { out.write("Username taken.\n"); out.flush(); } catch (IOException ignored) {}
            isAuthenticated = false;
        } catch (IOException e) {
            e.printStackTrace();
            isAuthenticated = false;
        }
    }

    private int getUserId(String name) {
        try {
            PreparedStatement ps = dbConnection.prepareStatement(
                    "SELECT client_id FROM Clients WHERE client_name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next() ? rs.getInt(1) : -1;
        } catch (SQLException e) {
            e.printStackTrace();
            return -1;
        }
    }

    private void saveMessage(String text, String recipient) {
        try {
            if (recipient == null) {
                PreparedStatement ps = dbConnection.prepareStatement(
                        "INSERT INTO Messages (sender_id, sender_name, message_text) VALUES (?, ?, ?)");
                ps.setInt(1, clientId);
                ps.setString(2, clientName);
                ps.setString(3, text);
                ps.executeUpdate();
            } else {
                // Сначала получаем recipient_id отдельно, чтобы избежать ошибок в subquery
                PreparedStatement psId = dbConnection.prepareStatement(
                        "SELECT client_id FROM Clients WHERE client_name = ?");
                psId.setString(1, recipient);
                ResultSet rs = psId.executeQuery();
                if (rs.next()) {
                    int recipientId = rs.getInt("client_id");
                    PreparedStatement ps = dbConnection.prepareStatement(
                            "INSERT INTO Messages (sender_id, sender_name, recipient_id, message_text) VALUES (?, ?, ?, ?)");
                    ps.setInt(1, clientId);
                    ps.setString(2, clientName);
                    ps.setInt(3, recipientId);
                    ps.setString(4, text);
                    ps.executeUpdate();
                } else {
                    System.out.println("Recipient ID not found for " + recipient);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Теперь ошибки в консоли
        }
    }

    private void sendHistory() {
        try {
            String sql = """
                SELECT sender_name, message_text, timestamp
                FROM Messages
                WHERE recipient_id IS NULL
                ORDER BY timestamp DESC
                LIMIT 30
                """;

            PreparedStatement ps = dbConnection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();

            List<String> lines = new ArrayList<>();
            while (rs.next()) {
                String time = rs.getTimestamp("timestamp").toString().substring(11, 19);
                lines.add("[" + time + "] " + rs.getString("sender_name") + ": " + rs.getString("message_text"));
            }

            for (int i = lines.size() - 1; i >= 0; i--) {
                out.write(lines.get(i)); out.newLine();
            }
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler c : new ArrayList<>(clients)) {
                if (c.isAuthenticated) {
                    try {
                        c.out.write(message);
                        c.out.newLine();
                        c.out.flush();
                    } catch (IOException e) {
                        c.closeEverything();
                        clients.remove(c);
                    }
                }
            }
        }
    }

    private void sendPrivate(String message, String recipientName) {
        synchronized (clients) {
            boolean found = false;
            for (ClientHandler c : new ArrayList<>(clients)) {
                if (c.isAuthenticated && c.clientName.equals(recipientName)) {
                    try {
                        c.out.write("(private) " + message);
                        c.out.newLine();
                        c.out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        out.write("(to " + recipientName + ") " + message);
                        out.newLine();
                        out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                try { out.write("Recipient not found: " + recipientName + "\n"); out.flush(); } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void broadcastSystem(String message) {
        synchronized (clients) {
            for (ClientHandler c : new ArrayList<>(clients)) {
                if (c.isAuthenticated) {
                    try {
                        c.out.write("*** " + message + " ***");
                        c.out.newLine();
                        c.out.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                        c.closeEverything();
                        clients.remove(c);
                    }
                }
            }
        }
    }

    private void broadcastOnlineList() {
        StringBuilder sb = new StringBuilder("ONLINE: ");
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.isAuthenticated) sb.append(c.clientName).append(", ");
            }
        }
        String msg = sb.length() > 8 ? sb.substring(0, sb.length() - 2) : "nobody";
        broadcastSystem(msg);
    }

    private void logout() {
        synchronized (clients) { clients.remove(this); }
        if (isAuthenticated && clientName != null) broadcastSystem(clientName + " left the chat.");
        closeEverything();
    }

    private void closeEverything() {
        try { if (in != null) in.close(); } catch (IOException e) { e.printStackTrace(); }
        try { if (out != null) out.close(); } catch (IOException e) { e.printStackTrace(); }
        try { if (socket != null) socket.close(); } catch (IOException e) { e.printStackTrace(); }
    }

    public static void initDB() {
        try {
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_app", "root", "14789");
        } catch (SQLException e) {
            System.err.println("DB error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}