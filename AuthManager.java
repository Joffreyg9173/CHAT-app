import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import javax.swing.plaf.basic.BasicScrollBarUI;

public class AuthManager {
    private final JFrame frame;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;
    private String username;

    public AuthManager(JFrame frame) {
        this.frame = frame;
    }

    public void showLoginScreen() {
        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, new Color(70, 130, 180), 0, getHeight(), new Color(123, 104, 238));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        panel.setOpaque(false);

        JLabel title = new JLabel("Welcome to The Line");
        title.setFont(new Font("Arial", Font.BOLD, 100));
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);

        JButton logIn = createButton("Log in", new Color(46, 204, 113));
        JButton signIn = createButton("Sign in", new Color(52, 152, 219));
        JButton closeButton = createButton("Close", new Color(231, 76, 60)); // НОВАЯ КНОПКА

        logIn.addActionListener(e -> showAuthDialog("1"));
        signIn.addActionListener(e -> showAuthDialog("2"));
        closeButton.addActionListener(e -> System.exit(0)); // Закрывает приложение

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(20, 20, 20, 20);
        gbc.gridy = 0; panel.add(title, gbc);
        gbc.gridy = 1; panel.add(logIn, gbc);
        gbc.gridy = 2; panel.add(signIn, gbc);
        gbc.gridy = 3; panel.add(closeButton, gbc);

        frame.setContentPane(panel);
        frame.revalidate();
    }

    private JButton createButton(String text, Color color) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setColor(color);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 40, 40);
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Arial", Font.BOLD, 40));
                g2d.drawString(text, (getWidth() - g2d.getFontMetrics().stringWidth(text)) / 2, (getHeight() + g2d.getFontMetrics().getAscent()) / 2);
            }
        };
        btn.setPreferredSize(new Dimension(450, 120));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        return btn;
    }


    private void showAuthDialog(String choice) {
        JTextField nameField = new JTextField(20);
        JPasswordField passField = new JPasswordField(20);
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.gridx = 0; gbc.gridy = 0; panel.add(new JLabel("Имя:"), gbc);
        gbc.gridx = 1; panel.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; panel.add(new JLabel("Пароль:"), gbc);
        gbc.gridx = 1; panel.add(passField, gbc);

        int result = JOptionPane.showConfirmDialog(frame, panel, choice.equals("1") ? "Log in" : "Sign in", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            username = nameField.getText();
            String pass = new String(passField.getPassword());
            try {
                socket = new Socket("localhost", 1234);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                out.write(choice + "\n" + username + "\n" + pass + "\n");
                out.flush();

                new ChatManager(frame, socket, in, out, username);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Ошибка входа! Попробуйте снова.");
                showLoginScreen(); // Возврат к логину при ошибке
            }
        } else {
            showLoginScreen(); // Возврат при отмене
        }
    }
}