import java.net.*;

public class Server {
    public static void main(String[] args) {
        try {
            ClientHandler.initDB();
            ServerSocket serverSocket = new ServerSocket(1234);
            System.out.println("Server started on port 1234...");

            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket);
                new Thread(handler).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
