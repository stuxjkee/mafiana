import java.io.*;
import java.net.Socket;

/**
 * Client
 * Created by stuxjkee on 23.12.2014.
 */
public class Client {
    final Socket socket;
    final BufferedReader socketReader;
    final BufferedWriter socketWriter;
    final BufferedReader userReader;

    public static void main(String args[]) {
        try {
            new Client("localhost", 45000).run();
        } catch (IOException e) {
            System.out.println("Unable to connect. Server not running?");
        }
    }

    public Client(String host, int port) throws IOException{
        socket = new Socket(host, port);
        socketReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        socketWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        userReader = new BufferedReader(new InputStreamReader(System.in));

        new Thread(new Receiver()).start();
    }

    public void run() {

        String username = "unnamed";

        try {
            System.out.print("Input username >: ");
            username = userReader.readLine();
            socketWriter.write(username);
            socketWriter.write("\n");
            socketWriter.flush();
        } catch (IOException e) {
            System.exit(0);
        }


        while (true) {
            String userString = null;
            try {
                userString = userReader.readLine();
            } catch (IOException ignored) {}

            if (userString == null || userString.length() == 0 || socket.isClosed() || userString.equals("!exit")) {
                close();
                break;
            } else {
                try {
                    socketWriter.write(userString);
                    socketWriter.write("\n");
                    socketWriter.flush();
                } catch (IOException e) {
                    close();
                }
            }
        }
    }

    public synchronized void close() {
        if (!socket.isClosed()) {
            try {
                socket.close();
                System.exit(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class Receiver implements Runnable{
        @Override
        public void run() {
            while (!socket.isClosed()) {
                String line = null;

                try {
                    line = socketReader.readLine();

                } catch (IOException e) {
                    if (e.getMessage().equals("Socket closed")) {
                        break;
                    }
                    System.out.println("Connection lost");
                    close();
                }
                if (line == null) {
                    System.out.println("Server has closed connection");
                    close();
                } else {
                    System.out.println(line);
                }
            }
        }
    }
}
