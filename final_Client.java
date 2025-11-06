import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ChatClientGUI {
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;

    private JFrame frame = new JFrame("ChatApp v2.1");
    private JTextArea chatArea = new JTextArea(25, 60);
    private JTextField inputField = new JTextField();
    private JList<String> userList = new JList<>();
    private DefaultListModel<String> userModel = new DefaultListModel<>();

    public ChatClientGUI() {
        setupGUI();
        connectToServer();
        startMessageReader();
    }

    private void setupGUI() {
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.addActionListener(e -> sendMessage());

        userList.setModel(userModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userList.setFixedCellWidth(160);

        JScrollPane chatScroll = new JScrollPane(chatArea);
        JScrollPane usersScroll = new JScrollPane(userList);

        frame.setLayout(new BorderLayout());
        frame.add(chatScroll, BorderLayout.CENTER);
        frame.add(inputField, BorderLayout.SOUTH);
        frame.add(usersScroll, BorderLayout.EAST);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 1234);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            String choice = JOptionPane.showInputDialog(frame, "1 — Войти\n2 — Регистрация", "ChatApp", JOptionPane.QUESTION_MESSAGE);
            if (choice == null) System.exit(0);
            out.write(choice + "\n"); out.flush();

            username = JOptionPane.showInputDialog(frame, "Логин:");
            String password = JOptionPane.showInputDialog(frame, "Пароль:");
            if (username == null || password == null) System.exit(0);

            out.write(username + "\n" + password + "\n");
            out.flush();

            appendToChat("Подключено как " + username);

        } catch (Exception e) {
            JOptionPane.showMessageDialog(frame, "Ошибка подключения!");
            System.exit(1);
        }
    }

    private void startMessageReader() {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    final String finalMsg = msg;  // ← final переменная

                    if (msg.startsWith("ONLINE: ")) {
                        SwingUtilities.invokeLater(() -> {
                            userModel.clear();
                            String users = finalMsg.substring(8);
                            for (String u : users.split(",\\s*")) {
                                if (!u.isBlank()) userModel.addElement(u.trim());
                            }
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> chatArea.append(finalMsg + "\n"));
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> appendToChat("Соединение разорвано."));
            }
        }).start();
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        try {
            // Показываем СВОЁ сообщение СРАЗУ в чате
            appendToChat(username + ": " + text);

            // Отправляем на сервер
            out.write(text + "\n");
            out.flush();
            inputField.setText("");

            // Отладка: видно в консоли клиента
            System.out.println("[CLIENT] Отправлено: " + text);
        } catch (IOException e) {
            appendToChat("Ошибка сети: сообщение не отправлено.");
            System.out.println("[CLIENT] ОШИБКА: " + e.getMessage());
        }
    }
    private void appendToChat(String text) {
        SwingUtilities.invokeLater(() -> chatArea.append(text + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChatClientGUI::new);
    }
}
