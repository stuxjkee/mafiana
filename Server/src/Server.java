import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Server
 * Created by stuxjkee on 24.12.2014.
 */
public class Server {
    private ServerSocket serverSocket;
    private Thread serverThread;
    private int port;
    int usersCnt = 0;
    int playersCnt = 0;
    Stage stage = Stage.CHAT;


    BlockingQueue<User> users = new LinkedBlockingQueue<User>();
    ConcurrentHashMap<Integer, User> players = new ConcurrentHashMap<Integer, User>();

    public static void main(String args[]) throws IOException{
        new Server(45000).run();
    }

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        this.port = port;
    }

    public void run() {
        System.out.println("Server is running");
        serverThread = Thread.currentThread();
        while (true) {
            Socket socket = getNewConnection();
            if (serverThread.isInterrupted()) {
                break;
            } else if (socket != null){
                try {
                    final User user = new User(socket);
                    final Thread thread = new Thread(user);
                    thread.setDaemon(true);
                    thread.start();
                    users.offer(user);
                }
                catch (IOException ignored) {}
            }
        }
    }


    private Socket getNewConnection() {
        Socket s = null;
        try {
            s = serverSocket.accept();
        } catch (IOException e) {
            shutdownServer();
        }
        return s;
    }

    private synchronized void shutdownServer() {
        for (User usr: users) {
            usr.close();
        }
        if (!serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {}
        }
    }

    private synchronized void casting() {
        ArrayList<Integer> IDs = new ArrayList<Integer>();
        for (Map.Entry<Integer, User> pair : players.entrySet()) {
            IDs.add(pair.getKey());
        }


        int donIndex = (int)(Math.random() * playersCnt);
        players.get(IDs.get(donIndex)).role = Role.DON;
        IDs.remove(donIndex);

        int detectiveIndex = (int)(Math.random() * (playersCnt - 1));
        players.get(IDs.get(detectiveIndex)).role = Role.DETECTIVE;
        IDs.remove(detectiveIndex);

        int docIndex = (int)(Math.random() * (playersCnt - 2));
        players.get(IDs.get(docIndex)).role = Role.DOC;
        IDs.remove(docIndex);

        int whoreIndex = (int)(Math.random() * (playersCnt - 3));
        players.get(IDs.get(whoreIndex)).role = Role.WHORE;
        IDs.remove(whoreIndex);

        ArrayList<User> maffs = new ArrayList<User>();

        int mafCnt = (playersCnt - 4) / 3 + 1;
        int i = playersCnt - 4;
        while (mafCnt > 0) {
            int mafIndex = (int)(Math.random() * i);
            players.get(IDs.get(mafIndex)).role = Role.MAFIA;
            maffs.add(players.get(IDs.get(mafIndex)));
            IDs.remove(mafIndex);
            mafCnt--;
        }

        for (Integer id : IDs) {
            players.get(id).role = Role.CIVILIAN;
        }


        for (Map.Entry<Integer, User> pair : players.entrySet()) {
            System.out.println(pair.getValue().username + " " +  pair.getValue().role.toString());
            pair.getValue().send("\nMafff: You are " + pair.getValue().role.toString() + "\n");
        }

        for (User usr : maffs) {
            usr.send("Mafff: Your accomplices: ");
            for (User maff : maffs) {
                if (usr != maff ) {
                    if (maff.role.equals(Role.DON)) {
                        usr.send("#" + maff.ID + " " + maff.username + " (DON)");
                    } else {
                        usr.send("#" + maff.ID + " " + maff.username);
                    }
                }
            }
        }

        stage = Stage.NIGTH;

    }

    private class User implements Runnable {
        Socket socket;
        BufferedReader br;
        BufferedWriter bw;
        String username = "unnamed";
        Role role;
        int ID;
        boolean isDead = false;
        int votes = 0;

        public User(Socket socket) throws IOException {
            this.ID = ++usersCnt;
            this.socket = socket;
            this.br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            this.bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
            this.role = Role.SHADOW;
        }


        @Override
        public void run() {
            try {
                username = br.readLine();

                for (User usr : users) {
                    while (usr.username.equals(username) && usr != this) {
                        username = username + "(1)";
                    }
                }

                sendToAll("Server: " + username + " joined us");
            } catch (IOException ignored) {}


            while (!socket.isClosed()) {
                String line = null;
                try {
                    line = br.readLine();
                } catch (IOException e) {
                    close();
                }

                if (line == null) {
                    close();
                } else if (line.equals("!shutdown")) {
                    serverThread.interrupt();
                    try {
                        new Socket("localhost", port);
                    } catch (IOException ignored) {
                    } finally {
                        shutdownServer();
                    }
                } else if (line.equals("!join")) {
                    if (players.containsKey(ID)) {
                        send("Mafff: You already in the game");
                    }
                    else if (!stage.equals(Stage.CHAT)) {
                        send("Mafff: Can't join. Game already started");
                    } else {
                        players.put(ID, this);
                        playersCnt++;
                        sendToAll("Mafff: " + username + " joined to the game");
                        if (playersCnt < 6) {
                            sendToAll("Mafff: Need " + (6 - playersCnt) +  " more players to start the game");
                        }
                    }
                } else if (line.equals("!start")) {
                    if (!stage.equals(Stage.CHAT)) {
                        send("Mafff: Game already started");
                    } else if (playersCnt < 6) {
                        send("Mafff: Need " + (6 - playersCnt) + " more players to start the game");
                    } else {
                        stage = Stage.CASTING;
                        sendToAll("Mafff: Game started!");

                        casting();
                    }
                } else if (line.equals("!leave")) {
                    if (!players.containsKey(ID)) {
                        send("Mafff: You can't leave. Are you playing?");
                    }
                    else if (!stage.equals(Stage.CHAT)) {
                        send("Mafff: Can't leave. Game already started");
                    } else {
                        sendToAll("Mafff: " + username + " has left the game");
                        playersCnt--;
                        players.remove(ID);
                        if (playersCnt < 6) {
                            sendToAll("Mafff: Need " + (6 - playersCnt) +  " more players to start the game");
                        }
                    }
                } else if (line.equals("!users")) {
                    send("\nServer: Active users \n");
                    for (User usr : users) {
                        send("#" + usr.ID + " " + usr.username);
                    }
                    send("\n");
                } else if (line.equals("!players")) {
                    send("\nMafff: Active players: ID - Username\n");
                    for (Map.Entry<Integer, User> pair : players.entrySet()) {
                        if (!pair.getValue().isDead)
                            send("#" + pair.getKey() + " " + pair.getValue().username);
                    }
                    send("\n");
                } else {
                    sendToAll(username + ": " + line);
                }
            }
        }

        public synchronized void sendToAll(String line) {
            System.out.println(line);
            for (User usr : users) {
                usr.send(line);
            }
        }

        public synchronized void send(String line) {
            try {
                bw.write(line);
                bw.write("\n");
                bw.flush();
            } catch (IOException e) {
                close();
            }
        }

        public synchronized void close() {
            sendToAll("Server: " + username + " has left us");
            users.remove(this);
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

    }
}
