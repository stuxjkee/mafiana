import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
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
        System.out.println("Server is started");
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

        ArrayList<User> maffs = new ArrayList<User>();

        int donIndex = (int)(Math.random() * playersCnt);
        players.get(IDs.get(donIndex)).role = Role.DON;
        maffs.add(players.get(IDs.get(donIndex)));
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



        int mafCnt = (playersCnt - 3) / 3;
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

        new Thread(new Runnable() {
            @Override
            public void run() {
                night();
            }
        }).start();

    }


    public synchronized void sendToAll(String line) {
        System.out.println(line);
        for (User usr : users) {
            usr.send(line);
        }
    }

    public static boolean isNumeric(String str)
    {
        try {
            double d = Double.parseDouble(str);
        }
        catch(NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    private int twait(User usr) {
        boolean fl = false;
        int victim = -1;
        while (usr.move.equals("") && !fl) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (usr.move.equals("")) continue;

            fl = true;

            victim = -1;


            if (usr.role.equals(Role.DETECTIVE) && usr.move.charAt(1) == '!') {
                victim = Integer.parseInt(usr.move.substring(2, usr.move.length()));
            }
            else if (!isNumeric(usr.move.substring(1, usr.move.length()))) {
                fl = false;
                usr.send("Mafff: Wrong command.");
                usr.move = "";
                continue;
            }

            if (victim == -1) victim = Integer.parseInt(usr.move.substring(1, usr.move.length()));
            if (!players.containsKey(victim)) {
                fl = false;
                usr.send("Mafff: Player are dead or not yet born");
                usr.move = "";
            }

        }

        return victim;
    }

    class PlayerThread extends Thread {
        private int victim = -1;
        User usr;

        public PlayerThread(User usr) {
            this.usr = usr;
        }

        @Override
        public void run() {
            victim = twait(usr);
        }

        public int getVictim() {
            return victim;
        }
    }

    int maffCnt = 0, civCnt = 0;

    private synchronized void day() {
        Date before = new Date();

        ConcurrentHashMap<User, PlayerThread> playerThreads = new ConcurrentHashMap<User, PlayerThread>();
        ArrayList<User> victims = new ArrayList<User>();
        maffCnt = civCnt = 0;


        for (Map.Entry<Integer, User> pair : players.entrySet()) {
            if (pair.getValue().role.equals(Role.MAFIA) || pair.getValue().role.equals(Role.DON))
                maffCnt++;
            else
                civCnt++;
            pair.getValue().victim = null;
            PlayerThread cur = new PlayerThread(pair.getValue());
            playerThreads.put(pair.getValue(), cur);
            cur.start();
            pair.getValue().send("Mafff: Write !ID to vote for some player");
        }

        int playersCnt = maffCnt + civCnt;
        int cnt = 0;

        while (true) {
            Date after = new Date();
            for (Map.Entry<User, PlayerThread> pair : playerThreads.entrySet()) {
                if (pair.getValue().getVictim() != -1 && pair.getKey().victim == null) {
                    User victim = players.get(pair.getValue().getVictim());
                    victim.votes++;
                    sendToAll("Mafff: " + pair.getKey().username + " vote for " + victim.username + " (" + victim.votes + ")");
                    pair.getKey().victim = victim;
                    cnt++;
                    if (victims.size() == 0 || (!victims.contains(victim) && victims.get(0).votes <= victim.votes)) {
                        victims.add(victim);
                    }
                }
            }
            if (victims.size() == 1) {
                sendToAll("Mafff: " + victims.get(0).username + " (" + victims.get(0).role.toString() + " ) was killed");
                players.remove(victims.get(0));
                break;
            }
            if (after.getTime()/1000 - before.getTime()/1000 >= 100)
                break;
            if (playersCnt == cnt)
                break;
        }



    }

    private synchronized void night() {
        sendToAll("Night");
        Date before = new Date();
        PlayerThread don = null, detective = null, whore = null, doc = null;

        maffCnt = civCnt = 0;

        for (Map.Entry<Integer, User> pair : players.entrySet()) {
            if (pair.getValue().role.equals(Role.DON) || pair.getValue().role.equals(Role.MAFIA))
                maffCnt++;
            else
                civCnt++;
            if (pair.getValue().role.equals(Role.DON)) {
                pair.getValue().send("Mafff: Who will be killed tonight? Write !ID");
                final User cur = pair.getValue();
                don = new PlayerThread(cur);
                don.start();
            } else if (pair.getValue().role.equals(Role.DETECTIVE)) {
                pair.getValue().send("Mafff: Who will be checked tonight? Write !ID to check or !!ID to kill");
                final User cur = pair.getValue();
                detective = new PlayerThread(cur);
                detective.start();
            } else if (pair.getValue().role.equals(Role.DOC)) {
                pair.getValue().send("Mafff: Who will be heal? Write !ID");
                final User cur = pair.getValue();
                doc = new PlayerThread(cur);
                doc.start();
            } else if (pair.getValue().role.equals(Role.WHORE)) {
                pair.getValue().send("Mafff: whore? write !ID");
                final User cur = pair.getValue();
                whore = new PlayerThread(cur);
                whore.start();
            }
        }
        boolean docSuccess = false;

        boolean sunrise = false;

        do {
            sunrise = true;
            Date after = new Date();
            if (don != null) {
                if (don.isAlive())
                    sunrise = false;
            }
            if (detective != null) {
                if (detective.isAlive())
                    sunrise = false;
            }
            if (doc != null) {
                if (detective.isAlive())
                    sunrise = false;
            }
            if (whore != null) {
                if (whore.isAlive())
                    sunrise = false;
            }
            if (after.getTime()/1000 - before.getTime()/1000 >= 100) {
                sunrise = true;
                try {
                    don.interrupt();
                    detective.interrupt();
                    whore.interrupt();
                    doc.interrupt();
                } catch (NullPointerException ignored) {}
            }
        } while (!sunrise);

        sendToAll("Mafff: Night ended");

        for (Map.Entry<Integer, User> pair : players.entrySet()) {
            if (don != null) {
                if (pair.getValue().role.equals(Role.DON) && don.getVictim() != -1) {
                    pair.getValue().victim = players.get(don.getVictim());
                    pair.getValue().victim.isDead = 1;
                }
            }
            if (detective != null) {
                if (pair.getValue().role.equals(Role.DETECTIVE) && don.getVictim() != -1) {
                    pair.getValue().victim = players.get(detective.getVictim());
                    if (pair.getValue().move.charAt(1) == '!') {
                        pair.getValue().send("Mafff: " + pair.getValue().victim.username + " - "
                                + pair.getValue().victim.role.toString());
                        pair.getValue().victim = null;
                    } else {
                        pair.getValue().victim.isDead = 1;
                    }
                }
            }
            if (doc != null) {
                if (pair.getValue().role.equals(Role.DOC) && doc.getVictim() != -1) {
                    pair.getValue().victim = players.get(doc.getVictim());
                    if (pair.getValue().victim.isDead == 1) {
                        pair.getValue().victim.isDead = 2;
                        docSuccess = true;
                    }
                }
            }
            if (whore != null) {
                if (pair.getValue().role.equals(Role.WHORE) && whore.getVictim() != -1) {
                    pair.getValue().victim = players.get(whore.getVictim());
                    //do something
                }
            }
        }


        for (Map.Entry<Integer, User> pair : players.entrySet()) {
            if (pair.getValue().victim != null) {
                if (pair.getValue().role.equals(Role.DON)) {
                    if (pair.getValue().victim.isDead == 1) {
                        sendToAll(pair.getValue().victim.username + " (" + pair.getValue().victim.role
                                + ") " + " was killed by mafia");
                        players.remove(pair.getValue().victim.ID);
                    }
                    if (pair.getValue().victim.isDead == 2) {
                        sendToAll("Doc rescued from detective" + pair.getValue().victim.username);
                        pair.getValue().victim.isDead = 0;
                        pair.getValue().victim = null;
                        docSuccess = true;
                    }
                }
                if (pair.getValue().role.equals(Role.DETECTIVE)) {
                    if (pair.getValue().move.charAt(1) == '!') {
                        if (pair.getValue().isDead == 1) {
                            sendToAll(pair.getValue().victim.username + " (" + pair.getValue().victim.role
                                + ") " + " was killed by detective");
                            players.remove(pair.getValue().victim.ID);
                        } else if (pair.getValue().isDead == 2) {
                            sendToAll("Doc rescued from detective" + pair.getValue().victim.username);
                            docSuccess = true;
                            pair.getValue().victim.isDead = 0;
                            pair.getValue().victim = null;
                        }
                    }
                }
                if (pair.getValue().role.equals(Role.DOC)) {
                    if (!docSuccess) {
                        sendToAll("Doc overnight treated " + pair.getValue().victim.username);
                        pair.getValue().victim.isDead = 0;
                        pair.getValue().victim = null;
                    }
                }
            }
            pair.getValue().victim = null;
        }
    }

    private class User implements Runnable {
        Socket socket;
        BufferedReader br;
        BufferedWriter bw;
        String username = "unnamed";
        Role role;
        String move = "";
        int ID;
        int isDead = 0; //0 - live, 1 - dead, 2 - was healed
        int votes = 0;
        User victim = null;

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
                            send("#" + pair.getKey() + " " + pair.getValue().username);
                    }
                    send("\n");
                } else if (line.charAt(0) == '!' && stage.equals(Stage.NIGTH)) {
                    move = line;
                } else {
                        sendToAll(username + ": " + line);
                }
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
            if (players.containsKey(this.ID))
                players.remove(this.ID);
            if (!socket.isClosed()) {
                try {
                    socket.close();
                } catch (IOException ignored) {}
            }
        }

    }
}
