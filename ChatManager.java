import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.Socket;
import javax.swing.plaf.basic.BasicScrollBarUI;

public class ChatManager {
    private final JFrame frame;
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final String username;

    private JTextArea chatArea = new JTextArea();
    private JTextField inputField = new JTextField();
    private DefaultListModel<String> userModel = new DefaultListModel<>();
    private JList<String> userList = new JList<>(userModel);

    private CardLayout cardLayout = new CardLayout();
    private JPanel mainChatPanel = new JPanel(cardLayout);

    private JPanel modePanel = new JPanel(new GridBagLayout());
    private JPanel specificPanel = new JPanel(new GridBagLayout());
    private JPanel chatPanel = new JPanel(new BorderLayout());

    private String currentRecipient; // null for broadcast

    public ChatManager(JFrame frame, Socket socket, BufferedReader in, BufferedWriter out, String username) {
        this.frame = frame;
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.username = username;

        setupChatComponents();
        startMessageReader();
    }

    private void setupChatComponents() {
        mainChatPanel.setBackground(new Color(240, 240, 245)); // Светлый фон

        // Панель выбора режима с градиентом
        modePanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(255, 255, 255), 0, getHeight(), new Color(220, 220, 220));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.ipady = 60; // Большие кнопки

        JButton broadcastButton = createStyledButton("Broadcast", new Color(46, 204, 113), new Color(39, 174, 96));
        JButton specificButton = createStyledButton("Specific user", new Color(52, 152, 219), new Color(41, 128, 185));

        broadcastButton.addActionListener(e -> {
            currentRecipient = null;
            cardLayout.show(mainChatPanel, "chat");
        });
        specificButton.addActionListener(e -> cardLayout.show(mainChatPanel, "specific"));

        gbc.gridy = 0;
        modePanel.add(broadcastButton, gbc);
        gbc.gridy = 1;
        modePanel.add(specificButton, gbc);

        mainChatPanel.add(modePanel, "mode");

        // Панель для specific с похожим стилем
        specificPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(255, 255, 255), 0, getHeight(), new Color(220, 220, 220));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        JLabel label = new JLabel("Имя получателя:");
        label.setFont(new Font("SansSerif", Font.BOLD, 24));
        JTextField recipientField = new JTextField(20);
        recipientField.setFont(new Font("SansSerif", Font.PLAIN, 20));
        JButton startButton = createStyledButton("Start", new Color(255, 193, 7), new Color(251, 140, 0));

        startButton.addActionListener(e -> {
            currentRecipient = recipientField.getText().trim();
            if (!currentRecipient.isEmpty()) {
                cardLayout.show(mainChatPanel, "chat");
            }
        });

        gbc.gridy = 0;
        specificPanel.add(label, gbc);
        gbc.gridy = 1;
        specificPanel.add(recipientField, gbc);
        gbc.gridy = 2;
        specificPanel.add(startButton, gbc);

        mainChatPanel.add(specificPanel, "specific");

        // Панель чата
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 20));
        chatArea.setBackground(new Color(255, 255, 255));
        chatArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        chatScroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(180, 180, 180);
                this.trackColor = new Color(240, 240, 240);
            }
        });

        inputField.setPreferredSize(new Dimension(0, 70));
        inputField.setFont(new Font("SansSerif", Font.PLAIN, 20));
        inputField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));
        inputField.addActionListener(e -> sendMessage());

        JButton endButton = createStyledButton("End", new Color(231, 76, 60), new Color(192, 57, 43));
        endButton.addActionListener(e -> cardLayout.show(mainChatPanel, "mode"));

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(endButton, BorderLayout.EAST);
        bottomPanel.setBackground(new Color(245, 245, 245));

        // Список пользователей с иконками
        userList.setFont(new Font("SansSerif", Font.PLAIN, 18));
        userList.setBackground(new Color(245, 245, 245));
        userList.setBorder(BorderFactory.createTitledBorder("Online Users"));
        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setPreferredSize(new Dimension(300, 0));
        userScroll.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));

        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(bottomPanel, BorderLayout.SOUTH);
        chatPanel.add(userScroll, BorderLayout.EAST);
        chatPanel.setBackground(new Color(255, 255, 255));

        mainChatPanel.add(chatPanel, "chat");

        frame.setContentPane(mainChatPanel);
        frame.revalidate();
    }

    private JButton createStyledButton(String text, Color baseColor, Color hoverColor) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(getBackground());
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("SansSerif", Font.BOLD, 30));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() + fm.getAscent()) / 2 - 5);
            }
        };
        btn.setPreferredSize(new Dimension(400, 100));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setBackground(baseColor);
        btn.setOpaque(false);

        // Hover эффект
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(hoverColor);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(baseColor);
            }
        });

        return btn;
    }

    private void startMessageReader() {
        new Thread(() -> {
            try {
                String msg;
                while ((msg = in.readLine()) != null) {
                    final String finalMsg = msg;
                    SwingUtilities.invokeLater(() -> {
                        if (finalMsg.startsWith("ONLINE: ")) {
                            userModel.clear();
                            String users = finalMsg.substring(8);
                            for (String u : users.split(",\\s*")) {
                                if (!u.isBlank()) userModel.addElement("• " + u.trim());
                            }
                        } else {
                            // Цветные сообщения: свои - синие, чужие - серые
                            Color color = finalMsg.startsWith(username + ":") ? new Color(52, 152, 219) : new Color(100, 100, 100);
                            chatArea.setForeground(color);
                            chatArea.append(finalMsg + "\n");
                            chatArea.setCaretPosition(chatArea.getDocument().getLength());
                        }
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> chatArea.append("Соединение разорвано.\n"));
            }
        }).start();
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        // Автоматически broadcast если currentRecipient null, иначе private с @
        String toSend = (currentRecipient != null) ? "@" + currentRecipient + " " + text : text;

        try {
            out.write(toSend);
            out.newLine();
            out.flush();
            inputField.setText("");
        } catch (IOException e) {
            chatArea.append("Ошибка отправки.\n");
        }
    }
}