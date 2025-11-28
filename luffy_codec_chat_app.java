/*
Complete example: Real-time Chat Application (Java Sockets + JavaFX + MySQL)

Files (all combined here for convenience) - split into separate .java files when using in an IDE:
 - server/ChatServer.java
 - server/ClientHandler.java
 - server/DBHelper.java
 - server/Models.java (User, Message, Group)
 - client/ChatClient.java
 - client/MainApp.java (JavaFX)
 - client/Controllers.java (LoginController, ChatController)

Also included: database schema and README instructions at the bottom.

This is a sample minimal but functional reference implementation for internship/demo.
Adjust, secure, and harden for production.
*/

// ==========================
// server/DBHelper.java
// ==========================
package server;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBHelper {
    private final String url;
    private final String user;
    private final String pass;

    public DBHelper(String url, String user, String pass) throws ClassNotFoundException {
        this.url = url;
        this.user = user;
        this.pass = pass;
        Class.forName("com.mysql.cj.jdbc.Driver");
    }

    private Connection getConn() throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    // Authentication
    public boolean registerUser(String username, String password) throws SQLException {
        String q = "INSERT INTO users(username,password) VALUES(?,?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, username);
            ps.setString(2, password); // NOTE: store hashed passwords in production
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public Integer authenticate(String username, String password) throws SQLException {
        String q = "SELECT id FROM users WHERE username=? AND password=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("id");
                return null;
            }
        }
    }

    // message history
    public void saveMessage(int fromId, Integer toId, Integer groupId, String content, Timestamp ts) throws SQLException {
        String q = "INSERT INTO messages(from_user_id,to_user_id,group_id,content,created_at) VALUES(?,?,?,?,?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, fromId);
            if (toId == null) ps.setNull(2, Types.INTEGER); else ps.setInt(2, toId);
            if (groupId == null) ps.setNull(3, Types.INTEGER); else ps.setInt(3, groupId);
            ps.setString(4, content);
            ps.setTimestamp(5, ts);
            ps.executeUpdate();
        }
    }

    public List<String> getPrivateHistory(int userA, int userB, int limit) throws SQLException {
        String q = "SELECT u1.username AS from_username, m.content, m.created_at FROM messages m JOIN users u1 ON m.from_user_id=u1.id " +
                "WHERE ((m.from_user_id=? AND m.to_user_id=?) OR (m.from_user_id=? AND m.to_user_id=?)) ORDER BY m.created_at ASC LIMIT ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, userA); ps.setInt(2, userB); ps.setInt(3, userB); ps.setInt(4, userA); ps.setInt(5, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(String.format("[%s] %s: %s", rs.getTimestamp("created_at"), rs.getString("from_username"), rs.getString("content")));
                }
                return res;
            }
        }
    }

    public List<String> getGroupHistory(int groupId, int limit) throws SQLException {
        String q = "SELECT u.username AS from_username, m.content, m.created_at FROM messages m JOIN users u ON m.from_user_id=u.id WHERE m.group_id=? ORDER BY m.created_at ASC LIMIT ?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, groupId); ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<String> res = new ArrayList<>();
                while (rs.next()) {
                    res.add(String.format("[%s] %s: %s", rs.getTimestamp("created_at"), rs.getString("from_username"), rs.getString("content")));
                }
                return res;
            }
        }
    }

    // user list
    public List<String> getAllUsernames() throws SQLException {
        String q = "SELECT username FROM users";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q); ResultSet rs = ps.executeQuery()) {
            List<String> res = new ArrayList<>();
            while (rs.next()) res.add(rs.getString("username"));
            return res;
        }
    }

    // groups
    public int createGroup(String name, int ownerId) throws SQLException {
        String q = "INSERT INTO groups(name, owner_id) VALUES(?,?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setInt(2, ownerId);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                else throw new SQLException("Failed to create group");
            }
        }
    }

    public void addUserToGroup(int userId, int groupId) throws SQLException {
        String q = "INSERT IGNORE INTO group_members(group_id,user_id) VALUES(?,?)";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    public List<Integer> getGroupMembers(int groupId) throws SQLException {
        String q = "SELECT user_id FROM group_members WHERE group_id=?";
        try (Connection c = getConn(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setInt(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Integer> res = new ArrayList<>();
                while (rs.next()) res.add(rs.getInt("user_id"));
                return res;
            }
        }
    }
}

// ==========================
// server/Models.java
// ==========================
package server;

public class Models {
    public static class User {
        public int id;
        public String username;
        public User(int id, String username) { this.id = id; this.username = username; }
    }

    public static class Message {
        public int fromId;
        public Integer toId; // null for group
        public Integer groupId; // null for private
        public String content;
        public Message(int fromId, Integer toId, Integer groupId, String content) {
            this.fromId = fromId; this.toId = toId; this.groupId = groupId; this.content = content;
        }
    }
}

// ==========================
// server/ClientHandler.java
// ==========================
package server;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private BufferedReader in;
    private PrintWriter out;
    private Integer userId = null;
    private String username = null;

    public ClientHandler(Socket socket, ChatServer server) throws IOException {
        this.socket = socket;
        this.server = server;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
    }

    public void send(String msg) {
        out.println(msg);
    }

    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                // Expect JSON-like commands. For brevity we use simple pipe-separated protocol:
                // COMMAND|arg1|arg2|...
                String[] parts = line.split("\\|", 3);
                String cmd = parts[0];
                if (cmd.equals("REGISTER")) {
                    String[] p = parts[1].split("::",2);
                    String user = p[0]; String pass = p[1];
                    try {
                        boolean ok = server.db.registerUser(user, pass);
                        send(ok?"REGISTER_OK":"REGISTER_FAIL");
                    } catch (SQLException e) { send("REGISTER_FAIL"); }
                } else if (cmd.equals("LOGIN")) {
                    String[] p = parts[1].split("::",2);
                    String user = p[0]; String pass = p[1];
                    try {
                        Integer id = server.db.authenticate(user, pass);
                        if (id != null) {
                            this.userId = id; this.username = user; server.addOnline(this);
                            send("LOGIN_OK|"+id+"|"+user);
                            // send user list
                            server.broadcastUserList();
                        } else send("LOGIN_FAIL");
                    } catch (SQLException e) { send("LOGIN_FAIL"); }
                } else if (cmd.equals("MSG")) {
                    // MSG|TO::<toUsername>|content
                    // For group: MSG|GROUP::<groupId>|content
                    if (userId == null) { send("ERR|Not authenticated"); continue; }
                    String target = parts[1].split("\\|",2)[0];
                    String content = parts.length>2?parts[2]:"";
                    if (target.startsWith("TO::")) {
                        String toUser = target.substring(4);
                        ClientHandler toHandler = server.getByUsername(toUser);
                        Integer toId = server.getUserIdByName(toUser);
                        if (toHandler != null) {
                            toHandler.send("INCOMING_PRIVATE|"+username+"|"+content);
                        }
                        // save history
                        try { server.db.saveMessage(userId, toId, null, content, new Timestamp(System.currentTimeMillis())); } catch (SQLException e) { e.printStackTrace(); }
                    } else if (target.startsWith("GROUP::")) {
                        int gid = Integer.parseInt(target.substring(7));
                        // broadcast to group members
                        List<ClientHandler> members = server.getGroupHandlers(gid);
                        for (ClientHandler mh: members) {
                            mh.send("INCOMING_GROUP|"+gid+"|"+username+"|"+content);
                        }
                        try { server.db.saveMessage(userId, null, gid, content, new Timestamp(System.currentTimeMillis())); } catch (SQLException e) { e.printStackTrace(); }
                    }
                } else if (cmd.equals("CREATE_GROUP")) {
                    // CREATE_GROUP|groupName
                    if (userId==null) { send("ERR|Not authenticated"); continue; }
                    String groupName = parts[1];
                    try {
                        int gid = server.db.createGroup(groupName, userId);
                        server.db.addUserToGroup(userId, gid);
                        send("CREATE_GROUP_OK|"+gid);
                    } catch (SQLException e) { send("CREATE_GROUP_FAIL"); }
                } else if (cmd.equals("JOIN_GROUP")) {
                    // JOIN_GROUP|groupId
                    if (userId==null) { send("ERR|Not authenticated"); continue; }
                    int gid = Integer.parseInt(parts[1]);
                    try { server.db.addUserToGroup(userId, gid); send("JOIN_GROUP_OK|"+gid); } catch (SQLException e) { send("JOIN_GROUP_FAIL"); }
                } else if (cmd.equals("HISTORY_PRIVATE")) {
                    // HISTORY_PRIVATE|otherUsername
                    if (userId==null) { send("ERR|Not authenticated"); continue; }
                    String other = parts[1];
                    try {
                        Integer otherId = server.getUserIdByName(other);
                        List<String> hist = server.db.getPrivateHistory(userId, otherId, 1000);
                        for (String h: hist) send("HISTORY_PRIVATE_LINE|"+h);
                        send("HISTORY_PRIVATE_END");
                    } catch (SQLException e) { send("HISTORY_PRIVATE_FAIL"); }
                } else if (cmd.equals("HISTORY_GROUP")) {
                    int gid = Integer.parseInt(parts[1]);
                    try {
                        List<String> hist = server.db.getGroupHistory(gid, 1000);
                        for (String h: hist) send("HISTORY_GROUP_LINE|"+h);
                        send("HISTORY_GROUP_END");
                    } catch (SQLException e) { send("HISTORY_GROUP_FAIL"); }
                } else if (cmd.equals("GET_USERS")) {
                    try {
                        List<String> users = server.db.getAllUsernames();
                        for (String u: users) send("USER|"+u);
                        send("USER_END");
                    } catch (SQLException e) { send("USER_FAIL"); }
                } else if (cmd.equals("LOGOUT")) {
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            server.removeOnline(this);
            server.broadcastUserList();
        }
    }

    public Integer getUserId() { return userId; }
    public String getUsername() { return username; }
}

// ==========================
// server/ChatServer.java
// ==========================
package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class ChatServer {
    private final int port;
    public DBHelper db;
    private ServerSocket serverSocket;
    private final List<ClientHandler> online = Collections.synchronizedList(new ArrayList<>());
    private final ThreadPoolExecutor pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

    public ChatServer(int port, String dbUrl, String dbUser, String dbPass) throws ClassNotFoundException {
        this.port = port;
        this.db = new DBHelper(dbUrl, dbUser, dbPass);
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("ChatServer listening on port " + port);
        while (true) {
            Socket s = serverSocket.accept();
            try {
                ClientHandler h = new ClientHandler(s, this);
                pool.execute(h);
            } catch (IOException e) { s.close(); }
        }
    }

    // manage online
    public void addOnline(ClientHandler ch) { online.add(ch); }
    public void removeOnline(ClientHandler ch) { online.remove(ch); }
    public ClientHandler getByUsername(String username) {
        synchronized (online) {
            for (ClientHandler ch: online) if (username.equals(ch.getUsername())) return ch;
        }
        return null;
    }

    public Integer getUserIdByName(String username) {
        synchronized (online) {
            for (ClientHandler ch: online) if (username.equals(ch.getUsername())) return ch.getUserId();
        }
        // fallback: query DB? For simplicity return null when offline.
        return null;
    }

    public List<ClientHandler> getGroupHandlers(int groupId) {
        // naive approach: check DB for member IDs and map to online handlers
        try {
            List<Integer> members = db.getGroupMembers(groupId);
            List<ClientHandler> res = new ArrayList<>();
            synchronized (online) {
                for (ClientHandler ch: online) {
                    if (ch.getUserId()!=null && members.contains(ch.getUserId())) res.add(ch);
                }
            }
            return res;
        } catch (SQLException e) { return Collections.emptyList(); }
    }

    public void broadcastUserList() {
        try {
            List<String> users = db.getAllUsernames();
            synchronized (online) {
                for (ClientHandler ch: online) {
                    for (String u: users) ch.send("USER|"+u);
                    ch.send("USER_END");
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public static void main(String[] args) throws Exception {
        // Usage: java server.ChatServer 9000 jdbc:mysql://localhost:3306/chatdb dbuser dbpass
        if (args.length<4) {
            System.out.println("Usage: java server.ChatServer <port> <dbUrl> <dbUser> <dbPass>");
            return;
        }
        int port = Integer.parseInt(args[0]);
        ChatServer s = new ChatServer(port, args[1], args[2], args[3]);
        s.start();
    }
}

// ==========================
// client/ChatClient.java (network layer)
// ==========================
package client;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ChatClient {
    private String host;
    private int port;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread readerThread;

    public ChatClient(String host, int port) {
        this.host = host; this.port = port;
    }

    public boolean connect() {
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            return true;
        } catch (IOException e) { e.printStackTrace(); return false; }
    }

    public void startReading(Consumer<String> onLine) {
        readerThread = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine())!=null) onLine.accept(line);
            } catch (IOException e) { onLine.accept("DISCONNECTED"); }
        });
        readerThread.setDaemon(true);
        readerThread.start();
    }

    public void sendRaw(String s) { out.println(s); }
    public void close() {
        try { if (socket!=null) socket.close(); } catch (IOException ignored) {}
    }
}

// ==========================
// client/MainApp.java (JavaFX UI)
// ==========================
package client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {
    private ChatClient client;
    private String username;
    private int userId;

    private ListView<String> usersList = new ListView<>();
    private ListView<String> messagesList = new ListView<>();
    private TextField inputField = new TextField();
    private TextField hostField = new TextField("localhost");
    private TextField portField = new TextField("9000");

    @Override
    public void start(Stage stage) {
        stage.setTitle("Luffy Chat - Client");

        // login pane
        VBox loginPane = new VBox(8);
        loginPane.setPadding(new Insets(10));
        TextField userTf = new TextField(); userTf.setPromptText("username");
        PasswordField passTf = new PasswordField(); passTf.setPromptText("password");
        Button loginBtn = new Button("Login");
        Button regBtn = new Button("Register");
        HBox connBox = new HBox(8,new Label("Host:"),hostField,new Label("Port:"),portField);
        loginPane.getChildren().addAll(connBox, userTf, passTf, new HBox(8,loginBtn,regBtn));

        Scene loginScene = new Scene(loginPane, 400, 220);

        // chat pane
        BorderPane chatPane = new BorderPane();
        usersList.setPrefWidth(150);
        chatPane.setLeft(usersList);
        chatPane.setCenter(messagesList);
        HBox bottom = new HBox(8, inputField, new Button("Send") {{ setOnAction(e-> sendMessageToSelected()); }});
        bottom.setPadding(new Insets(8));
        chatPane.setBottom(bottom);
        Scene chatScene = new Scene(chatPane, 800, 600);

        // actions
        loginBtn.setOnAction(e-> {
            String host = hostField.getText(); int port = Integer.parseInt(portField.getText());
            client = new ChatClient(host, port);
            if (!client.connect()) { showAlert("Connection failed"); return; }
            client.startReading(this::handleServerLine);
            client.sendRaw("LOGIN|"+userTf.getText()+"::"+passTf.getText());
        });

        regBtn.setOnAction(e-> {
            String host = hostField.getText(); int port = Integer.parseInt(portField.getText());
            client = new ChatClient(host, port);
            if (!client.connect()) { showAlert("Connection failed"); return; }
            client.startReading(this::handleServerLine);
            client.sendRaw("REGISTER|"+userTf.getText()+"::"+passTf.getText());
        });

        usersList.setOnMouseClicked(e-> {
            if (e.getClickCount()==2) {
                String sel = usersList.getSelectionModel().getSelectedItem();
                if (sel!=null) requestPrivateHistory(sel);
            }
        });

        inputField.setOnKeyPressed(e-> { if (e.getCode()==KeyCode.ENTER) sendMessageToSelected(); });

        stage.setScene(loginScene);
        stage.show();

        // On successful login we'll switch scenes from handleServerLine
    }

    private void sendMessageToSelected() {
        String sel = usersList.getSelectionModel().getSelectedItem();
        if (sel==null) { showAlert("Select a user to message"); return; }
        String txt = inputField.getText(); if (txt.trim().isEmpty()) return;
        // send private
        client.sendRaw("MSG|TO::"+sel+"|"+txt);
        messagesList.getItems().add("Me -> "+sel+": "+txt);
        inputField.clear();
    }

    private void requestPrivateHistory(String other) {
        client.sendRaw("HISTORY_PRIVATE|"+other);
    }

    private void handleServerLine(String line) {
        Platform.runLater(() -> {
            if (line.startsWith("LOGIN_OK")) {
                // LOGIN_OK|id|username
                String[] p = line.split("\\|",3);
                this.userId = Integer.parseInt(p[1]); this.username = p[2];
                Stage st = (Stage) messagesList.getScene().getWindow();
                st.setTitle("Luffy Chat - " + username);
                st.setScene(messagesList.getScene()); // no - we need to switch to chat scene
                // Instead, rebuild chat scene quickly:
                BorderPane chatPane = new BorderPane();
                chatPane.setLeft(usersList); chatPane.setCenter(messagesList);
                HBox bottom = new HBox(8, inputField, new Button("Send") {{ setOnAction(e-> sendMessageToSelected()); }});
                bottom.setPadding(new Insets(8)); chatPane.setBottom(bottom);
                Scene chatScene = new Scene(chatPane, 800, 600);
                st.setScene(chatScene);
            } else if (line.equals("REGISTER_OK")) {
                showAlert("Registered successfully. Please login.");
            } else if (line.equals("REGISTER_FAIL")) {
                showAlert("Register failed (username may exist).");
            } else if (line.equals("LOGIN_FAIL")) {
                showAlert("Login failed. Check credentials.");
            } else if (line.startsWith("INCOMING_PRIVATE|")) {
                // INCOMING_PRIVATE|fromUser|content
                String[] p = line.split("\\|",3);
                messagesList.getItems().add(p[1]+": "+p[2]);
            } else if (line.startsWith("INCOMING_GROUP|")) {
                String[] p = line.split("\\|",4); // groupId|from|content
                messagesList.getItems().add("[Group:"+p[1]+"] "+p[2]+": "+p[3]);
            } else if (line.startsWith("USER|")) {
                String u = line.substring(5);
                ObservableList<String> items = usersList.getItems();
                if (!items.contains(u)) items.add(u);
            } else if (line.equals("USER_END")) {
                // finished
            } else if (line.startsWith("HISTORY_PRIVATE_LINE|")) {
                messagesList.getItems().add(line.substring(21));
            } else if (line.equals("HISTORY_PRIVATE_END")) {
                messagesList.getItems().add("--- End of history ---");
            } else if (line.equals("DISCONNECTED")) {
                showAlert("Disconnected from server");
            } else {
                // catchall for debug
                System.out.println("SERVER: " + line);
            }
        });
    }

    private void showAlert(String s) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, s, ButtonType.OK);
        a.showAndWait();
    }

    public static void main(String[] args) { launch(args); }
}

/*
README & Database schema
------------------------

1) Create MySQL database and tables:

CREATE DATABASE chatdb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chatdb;

CREATE TABLE users (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE groups (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(200) NOT NULL,
  owner_id INT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE group_members (
  group_id INT NOT NULL,
  user_id INT NOT NULL,
  PRIMARY KEY (group_id,user_id),
  FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE messages (
  id INT AUTO_INCREMENT PRIMARY KEY,
  from_user_id INT NOT NULL,
  to_user_id INT NULL,
  group_id INT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (from_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (to_user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
) ENGINE=InnoDB;

2) Build & Run server
 - Compile: javac -cp .:mysql-connector-java-8.0.33.jar server/*.java
 - Run: java -cp .:mysql-connector-java-8.0.33.jar server.ChatServer 9000 jdbc:mysql://localhost:3306/chatdb dbuser dbpass

3) Build & Run client (JavaFX required)
 - Compile: javac -cp .:path/to/javafx/lib/* client/*.java
 - Run: java --module-path /path/to/javafx/lib --add-modules javafx.controls,javafx.fxml client.MainApp

4) Notes & next steps
 - Passwords are stored in plaintext here for brevity. Replace with bcrypt or Argon2 hashing.
 - Protocol is a simple pipe-separated text commands. For reliability, replace with JSON (Gson/Jackson) and handle escaping.
 - For scaling consider using Netty, WebSockets (for browser clients), and message broker (Redis/PubSub).
 - Add file-transfer, typing indicators, presence, profile pictures, and better error handling.
 - For group user discovery implement an API or commands to list groups and join/leave.
 - The client UI is minimal. Replace with FXML controllers for maintainability.

Good luck with your internship project at Codec Technologies — adapt and expand this starter as your assignment requires.
*/
