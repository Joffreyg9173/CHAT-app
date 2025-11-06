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
    private String username;
    private int userId;
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
            broadcastSystem(username + " joined the chat!");
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
                        sendPrivate(username + ": " + text, recipient);
                        saveMessage(text, recipient);
                    }
                } else {
                    String fullMsg = username + ": " + message;
                    broadcastMessage(fullMsg);
                    saveMessage(message, null);
                }
            }
        } catch (IOException e) {
            System.out.println((username != null ? username : "Client") + " disconnected.");
        } finally {
            logout();
        }
    }

    private void authenticate() {
        try {
            out.write("1. Login\n2. Register\nChoose (1/2): ");
            out.flush();
            String choice = in.readLine();

            if ("1".equals(choice)) login();
            else if ("2".equals(choice)) register();
            else {
                out.write("Invalid choice.\n");
                out.flush();
                isAuthenticated = false;
            }
        } catch (IOException e) {
            isAuthenticated = false;
        }
    }

    private void login() {
        try {
            out.write("Username: "); out.flush(); username = in.readLine();
            out.write("Password: "); out.flush(); String password = in.readLine();

            PreparedStatement ps = dbConnection.prepareStatement(
                    "SELECT client_id FROM Clients WHERE client_name = ? AND password = ?");
            ps.setString(1, username); ps.setString(2, password);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                userId = rs.getInt("client_id");
                out.write("Welcome, " + username + "!\n"); out.flush();
            } else {
                out.write("Invalid login.\n"); out.flush();
                isAuthenticated = false;
            }
        } catch (Exception e) {
            try { out.write("Error during login.\n"); out.flush(); } catch (IOException ignored) {}
            isAuthenticated = false;
        }
    }

    private void register() {
        try {
            out.write("New username: "); out.flush(); username = in.readLine();
            out.write("New password: "); out.flush(); String password = in.readLine();

            PreparedStatement ps = dbConnection.prepareStatement(
                    "INSERT INTO Clients (client_name, password) VALUES (?, ?)");
            ps.setString(1, username); ps.setString(2, password);
            ps.executeUpdate();
            userId = getUserId(username);
            out.write("Registered as " + username + "!\n"); out.flush();
        } catch (SQLException e) {
            try { out.write("Username taken.\n"); out.flush(); } catch (IOException ignored) {}
            isAuthenticated = false;
        } catch (IOException e) {
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
        } catch (SQLException e) { return -1; }
    }

    private void saveMessage(String text, String recipient) {
        try {
            String sql = recipient == null ?
                    "INSERT INTO Messages (sender_id, sender_name, message_text) VALUES (?, ?, ?)" :
                    "INSERT INTO Messages (sender_id, sender_name, recipient_id, message_text) VALUES (?, ?, (SELECT client_id FROM Clients WHERE client_name = ?), ?)";

            PreparedStatement ps = dbConnection.prepareStatement(sql);
            ps.setInt(1, userId);
            ps.setString(2, username);
            ps.setString(3, text);
            if (recipient != null) ps.setString(4, recipient);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void sendHistory() {
        try {
            String sql = """
                SELECT sender_name, message_text, message_time
                FROM Messages
                WHERE recipient_id IS NULL OR sender_id = ? OR recipient_id = ?
                ORDER BY message_time DESC
                LIMIT 30
                """;

            PreparedStatement ps = dbConnection.prepareStatement(sql);
            ps.setInt(1, userId); ps.setInt(2, userId);
            ResultSet rs = ps.executeQuery();

            List<String> lines = new ArrayList<>();
            while (rs.next()) {
                String time = rs.getTimestamp("message_time").toString().substring(11, 19);
                lines.add("[" + time + "] " + rs.getString("sender_name") + ": " + rs.getString("message_text"));
            }

            for (int i = lines.size() - 1; i >= 0; i--) {
                out.write(lines.get(i)); out.newLine();
            }
            out.flush();
        } catch (Exception ignored) {}
    }

    private void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler c : new ArrayList<>(clients)) {
                if (c.isAuthenticated) {
                    try {
                        c.out.write(message); c.out.newLine(); c.out.flush();
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
            for (ClientHandler c : new ArrayList<>(clients)) {
                if (c.isAuthenticated && c.username.equals(recipientName)) {
                    try {
                        c.out.write("(private) " + message); c.out.newLine(); c.out.flush();
                        out.write("(to " + recipientName + ") " + message); out.newLine(); out.flush();
                    } catch (IOException ignored) {}
                    return;
                }
            }
            try { out.write("@" + recipientName + " not found.\n"); out.flush(); } catch (IOException ignored) {}
        }
    }

    private void broadcastSystem(String message) {
        synchronized (clients) {
            for (ClientHandler c : new ArrayList<>(clients)) {
                if (c.isAuthenticated) {
                    try {
                        c.out.write("*** " + message + " ***"); c.out.newLine(); c.out.flush();
                    } catch (IOException e) {
                        c.closeEverything(); clients.remove(c);
                    }
                }
            }
        }
    }

    private void broadcastOnlineList() {
        StringBuilder sb = new StringBuilder("ONLINE: ");
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.isAuthenticated) sb.append(c.username).append(", ");
            }
        }
        String msg = sb.length() > 8 ? sb.substring(0, sb.length() - 2) : "nobody";
        broadcastSystem(msg);
    }

    private void logout() {
        synchronized (clients) { clients.remove(this); }
        if (isAuthenticated && username != null) broadcastSystem(username + " left the chat.");
        closeEverything();
    }

    private void closeEverything() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    public static void initDB() {
        try {
            dbConnection = DriverManager.getConnection("jdbc:mysql://localhost:3306/chat_app", "root", "14789");
        } catch (SQLException e) {
            System.err.println("DB connection failed: " + e.getMessage());
        }
    }
}
