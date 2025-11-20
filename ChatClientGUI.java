import javax.swing.*;

public class  ChatClientGUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("ChatApp");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);

            AuthManager auth = new AuthManager(frame);
            auth.showLoginScreen();

            frame.setVisible(true);
        });
    }
}