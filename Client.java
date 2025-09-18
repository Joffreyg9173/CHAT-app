import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        Socket socket = null;
        InputStreamReader inputStreamReader = null;
        OutputStreamWriter outputStreamWriter = null;
        BufferedReader bufferedReader = null;
        BufferedWriter bufferedWriter = null;

        try {
            // Initialize socket and streams
            socket = new Socket("localhost", 1234);
            System.out.println("Connected to server.");

            inputStreamReader = new InputStreamReader(socket.getInputStream());
            outputStreamWriter = new OutputStreamWriter(socket.getOutputStream());
            bufferedReader = new BufferedReader(inputStreamReader);
            bufferedWriter = new BufferedWriter(outputStreamWriter);

            // Create a final variable for use in the lambda expression
            final BufferedReader finalBufferedReader = bufferedReader;

            Scanner scanner = new Scanner(System.in);

            // Start a separate thread to read server messages
            Thread readerThread = new Thread(() -> {
                try {
                    while (true) {
                        String serverMessage = finalBufferedReader.readLine();
                        if (serverMessage == null) break;
                        System.out.println("Server: " + serverMessage);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            readerThread.start();

            // Main thread handles sending messages
            while (true) {
                String msgToSend = scanner.nextLine();
                bufferedWriter.write(msgToSend);
                bufferedWriter.newLine();
                bufferedWriter.flush();

                if (msgToSend.equalsIgnoreCase("BYE")) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Safely close resources if they were initialized
            try {
                if (bufferedWriter != null) bufferedWriter.close();
                if (bufferedReader != null) bufferedReader.close();
                if (outputStreamWriter != null) outputStreamWriter.close();
                if (inputStreamReader != null) inputStreamReader.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}