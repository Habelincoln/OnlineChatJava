import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import javax.swing.*;
public class Client implements ActionListener {

    final static String hostConfigPath = "C:\\GitHub\\OnlineChatPrototype\\Host.config";
    final static String portConfigPath = "C:\\GitHub\\OnlineChatPrototype\\Port.config";

    private Socket client;
    private int selfID;

    private ObjectInputStream fromServer;
    private ObjectOutputStream toServer;

    private JFrame window;
    private JTextArea chat;
    private JScrollPane scroller;
    private JTextField input;
    private JTextArea clientList;
    private volatile boolean attemptingReconnect = false;
    private volatile boolean windowOpen = true;
    private volatile boolean isDarkMode = false;
    private volatile boolean deleteconfirmation = false;

    private HashMap<Integer, String> changedClients = new HashMap<>();
    private HashMap<Integer, String> tempMap = new HashMap<>();

    public Client() {
        input = new JTextField();
        chat = new JTextArea(31, 71);
        window = new JFrame("Chat");
        scroller = new JScrollPane(chat, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        clientList = new JTextArea(31, 9);
        chat.setLineWrap(true);
        chat.setWrapStyleWord(true);
        chat.setFont(new Font("ARIAL", Font.PLAIN, 15));
        clientList.setFont(new Font("ARIAL", Font.PLAIN, 15));
        clientList.setText("Client List");
        input.setFont(new Font("ARIAL", Font.PLAIN, 15));



        try {
            chat.append("[System] Use /help to view all commands.\n");
            chat.append("\n[System] Connecting to: " + new String(Files.readAllBytes(Paths.get(hostConfigPath))) + "...\n");
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }

        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    String message = input.getText();
                    if (message.length() > 0 && message.length() < 251) {
                        try {
                            send(message);
                        } catch (IOException ex) {
                            ex.printStackTrace(System.err);
                        }
                        if (!message.substring(0,1).equals("/")) {
                        chat.append("You: " + input.getText() + "\n");
                        }
                        chat.setCaretPosition(chat.getDocument().getLength());
                        input.setText("");
                    }
                }
            }
        });

        chat.setEditable(false);
        clientList.setEditable(false);
        input.requestFocusInWindow();
        
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setLocationRelativeTo(null);
        window.setResizable(false);
        window.setSize(1000, 600);

        window.add(input, BorderLayout.PAGE_END);
        window.add(scroller);
        window.add(clientList, BorderLayout.WEST);

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                windowOpen = false;
                attemptingReconnect = false;
                try {
                    if (client != null && !client.isClosed()) {
                        client.close();
                    }
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
                System.exit(0);
            }
        });
        input.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                    if (chat.getBackground() == Color.BLACK) {
                        setDarkMode(false);
                    } else {
                        setDarkMode(true);
                    }
                }
            }
        });

        chat.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                    if (chat.getBackground() == Color.BLACK) {
                        setDarkMode(false);
                    } else {
                        setDarkMode(true);
                    }
                }
            }
        });

        chat.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
            input.requestFocusInWindow();
            }
        });

        clientList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
            input.requestFocusInWindow();
            }
        });

        clientList.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
                    if (chat.getBackground() == Color.BLACK) {
                        setDarkMode(false);
                    } else {
                        setDarkMode(true);
                    }
                }
            }
        });

        try {
            connectToServer();
        } catch (Exception e) {
            handleServerDisconnect();
        }
    }

    private void connectToServer() {
        try {
            final String HOST = new String(Files.readAllBytes(Paths.get(hostConfigPath)));
            final String portString = new String(Files.readAllBytes(Paths.get(portConfigPath)));
            final int PORT = Integer.parseInt(portString);
            client = new Socket(HOST, PORT);
            toServer = new ObjectOutputStream(client.getOutputStream());
            toServer.flush();
            fromServer = new ObjectInputStream(client.getInputStream());
            attemptingReconnect = false;

            Thread serverConnectThread = new Thread(() -> {
                try {
                    if (client.isConnected()) {
                        chat.append("\n[System] Connected to server!\n");
                    }
                    while (!client.isClosed()) {
                        receive();
                    }
                } catch (SocketException e) {
                    chat.append("\n[System] Server connection lost.\n");
                    chat.setCaretPosition(chat.getDocument().getLength());
                    clientList.setText("Client List");
                    handleServerDisconnect();
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace(System.err);
                    handleServerDisconnect();
                }
            });
            serverConnectThread.start();

        } catch (IOException e) {
            if (!windowOpen) {
                System.exit(0);
            }
            handleServerDisconnect();
        }
    }

    private void handleServerDisconnect() {
        try {
            if (client != null && !client.isClosed()) {
                fromServer.close();
                toServer.close();
                client.close();
                clientList.setText("");
            }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }

        if (!attemptingReconnect && windowOpen) {
            attemptingReconnect = true;
            Thread reconnectThread = new Thread(() -> {
                while (attemptingReconnect && windowOpen) {
                    try {
                        Thread.sleep(1000);
                        connectToServer();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            reconnectThread.start();
        }
    }

    public synchronized void receive() throws IOException, ClassNotFoundException {
        Object obj = fromServer.readObject();
        if (obj instanceof String msg) {
            if (msg.contains("[Server] Welcome: Client")) {
                selfID = Integer.parseInt(msg.substring(24).trim());
                chat.append(msg + "\n");
                chat.setCaretPosition(chat.getDocument().getLength());
            } else {
                int firstSpace = msg.indexOf(' ');
                int colonIndex = msg.indexOf(':');
                if (firstSpace != -1 && colonIndex != -1 && colonIndex > firstSpace) {
                    String clientIdStr = msg.substring(firstSpace + 1, colonIndex).trim();
                    try {
                        int clientId = Integer.parseInt(clientIdStr);
                        if (changedClients.containsKey(clientId)) {
                            String newName = changedClients.get(clientId);
                            String oldPrefix = msg.substring(0, colonIndex + 1);
                            msg = msg.replaceFirst("Client " + clientIdStr + ":", newName + ":");
                        }
                    } catch (NumberFormatException ignored) {
                        // Not a client message, ignore
                    }
                }
                chat.append(msg + "\n");
                chat.setCaretPosition(chat.getDocument().getLength());
            }
        } else if (obj instanceof HashMap) {
            @SuppressWarnings("unchecked")
            HashMap<Integer, String> clientMap = (HashMap<Integer, String>) obj;
            updateClientList(clientMap, true);
            tempMap = clientMap;
        }
    }
    private void updateClientList(HashMap<Integer, String> clientMap, boolean isNewList) {
        if (isNewList) {
            clientList.setText("Connected Clients: \n ______________\n");
            clientList.append("You (" + selfID + ")\n");
            for (var entry : clientMap.entrySet()) {
                if (entry.getKey() != selfID) {
                    if (changedClients.containsKey(entry.getKey())) {
                        clientList.append(changedClients.get(entry.getKey()) + "\n");
                    } else {
                    clientList.append(entry.getValue() + "\n");
                 }
                
               }
            }
        } else {
            clientList.setText("Connected Clients: \n ______________\n");
            clientList.append("You (" + selfID + ")\n");
            for (var entry : tempMap.entrySet()) {
                if (entry.getKey() != selfID) {
                    if (changedClients.containsKey(entry.getKey())) {
                        clientList.append(changedClients.get(entry.getKey()) + "\n");
                    } else {
                    clientList.append(entry.getValue() + "\n");
                 }
                
               }
            }

        }
        
    }

    public void send(String msg) throws IOException {  
        
        if (msg.length() == 0) {
            //don't send
        } else if (msg.length() >=8 && msg.substring(0,8).toLowerCase().equals("/setname")) {
            int firstSpace = msg.indexOf(' ', 8);
            int secondSpace = msg.indexOf(' ', firstSpace + 1);
            int clientToChange = Integer.parseInt(msg.substring(firstSpace + 1, secondSpace));
            String newName = msg.substring(secondSpace + 1) + " (" + clientToChange + ")";
            
            if (clientToChange == selfID) {
                chat.append("\n[System] Error: You cannot change your own name.\n");

            } else if (!tempMap.containsKey(clientToChange)) {
                chat.append("\n[System] Error: Client " + clientToChange + " does not exist.\n");

            } else {
                changedClients.put(clientToChange, newName);
                updateClientList(changedClients, false);
                chat.append("\n[System] Client " + clientToChange + "'s name has been changed to: " + newName + "\n");
                chat.setCaretPosition(chat.getDocument().getLength());
            }

        } else if (msg.length() >=9 && msg.toLowerCase().startsWith("/darkmode")) {

            if (isDarkMode){setDarkMode(false);}
            else {setDarkMode(true);}

        } else if (msg.length() >= 5 && msg.toLowerCase().startsWith("/help")) {
            chat.append("\n");
            chat.append("[System] Commands:\n");
            chat.append("[System] /help - View all commands.\n");
            chat.append("[System] /setName <clientID> <newName> - Change the name of a client.\n");
            chat.append("[System] /darkMode (or CTRL + D) - Toggle dark mode.\n");
            chat.append("[System] /clear - Clear chat.\n");
            chat.append("[System] /resetName - Reset all names.\n");
            chat.append("[System] /resetName <clientID> - Reset one client's name.\n");
            chat.append("[System] /copy - Copy chat log to clipboard.\n");
            chat.append("[System] /exit - Exit the program.\n");
            chat.append("[System] /save - Save chat log to files.\n");
            chat.append("[System] /save <logName> - Save chat log to files with custom name.\n");
            chat.append("[System] /viewLogs - View all saved logs.\n");
            chat.append("[System] /load <logName> - Load a specific log.\n");
            chat.append("[System] /load - Load the latest default named chat log.\n");
            chat.append("[System] /deleteLog <logName> - Delete a specific log.\n");
            chat.append("[System] /deleteLogs - Delete all chat logs.\n");
            
            chat.setCaretPosition(chat.getDocument().getLength());
        } else if (msg.length() >= 6 && msg.toLowerCase().startsWith("/clear")) {
            StringBuilder sb = new StringBuilder();
            String[] lines = chat.getText().split("\n");
            boolean foundWelcome = false;
            boolean foundConnected = false;
            for (String line : lines) {
                if (line.contains("[System] Use /help to view all commands.")) {
                    sb.append(line).append("\n");
                    foundWelcome = true;
                } else if (line.contains("\n[System] Connecting to:")) {
                    if (client != null && client.isConnected() && !client.isClosed()) {
                        sb.append("\n[System] Connected to server!\n");
                        foundConnected = true;
                    } else {
                        sb.append(line).append("\n");
                        foundConnected = true;
                    }
                } else if (line.contains("\n[System] Connected to server!")) {
                    sb.append(line).append("\n");
                    foundConnected = true;
                }
            }
            if (!foundWelcome) {
                sb.append("[System] Use /help to view all commands.\n");
            }
            if (!foundConnected) {
                if (client != null && client.isConnected() && !client.isClosed()) {
                    sb.append("\n[System] Connected to server!\n");
                } else {
                    try {
                        String host = new String(Files.readAllBytes(Paths.get(hostConfigPath)));
                        sb.append("\n[System] Connecting to: " + host + "...\n");
                    } catch (IOException e) {
                        sb.append("\n[System] Connecting to: ...\n");
                    }
                }
            }
            chat.setText(sb.toString());
            chat.setCaretPosition(chat.getDocument().getLength());
        } else if (msg.toLowerCase().startsWith("/resetname")) {
            String[] parts = msg.trim().split(" "); // split by whitespace
            int clientToChange = -1;

            if (parts.length < 2) {
                changedClients.clear();
                updateClientList(changedClients, false);
            } else if (parts.length > 2) {
                chat.append("\n[System] Error: \"" + msg + "\" is not a valid command.\n");
            } else {
                try {
                    clientToChange = Integer.parseInt(parts[1]);

                    if (clientToChange == selfID) {
                        chat.append("\n[System] Error: You cannot change your own name.\n");
                    } else {
                        changedClients.remove(clientToChange);
                        updateClientList(changedClients, false);
                        chat.append("\n[System] Client " + clientToChange + "'s name has been reset.\n");
                    }

                } catch (NumberFormatException e) {
                    chat.append("\n[System] Error: \"" + parts[1] + "\" is not a valid number.\n");
                }
            }

            chat.setCaretPosition(chat.getDocument().getLength());
        } else if(msg.toLowerCase().startsWith("/copy")) {

            StringSelection stringSelection = new StringSelection(chat.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            chat.append("\n[System] Chat copied to clipboard.\n");
            chat.setCaretPosition(chat.getDocument().getLength());
        } else if (msg.toLowerCase().startsWith("/exit")) {
            chat.append("\n[System] Exiting...\n");
            chat.setCaretPosition(chat.getDocument().getLength());
            windowOpen = false;
            attemptingReconnect = false;
            try {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace(System.err);

            }

         } else if (msg.toLowerCase().equals("/save")) {
                    try {
                    File saveDir = new File("Saves");
                    if (!saveDir.exists()) {
                        saveDir.mkdirs();
                    }
                    String fileName = "chat_log_" + System.currentTimeMillis() + ".txt";
                    File saveFile = new File(saveDir, fileName);
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                        writer.write(chat.getText());
                    }
                    Thread.sleep(100);
                    chat.append("\n[System] Chat saved to " + saveFile.getPath() + "\n");
                    } catch (IOException | InterruptedException e) {
                    chat.append("\n[System] Error saving chat log.\n");
                    }
                    chat.setCaretPosition(chat.getDocument().getLength());

            } else if (msg.toLowerCase().startsWith("/save")) {
                String[] parts = msg.split(" ");
                if (parts.length == 2) {
                    String fileName = parts[1];
                    if (!fileName.toLowerCase().endsWith(".txt")) {
                        fileName += ".txt";
                    }
                    File saveDir = new File("Saves");
                    if (!saveDir.exists()) {
                        saveDir.mkdirs();
                    }
                    File saveFile = new File(saveDir, fileName);
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                        writer.write(chat.getText());
                    }
                    chat.append("\n[System] Chat saved to " + saveFile.getPath() + "\n");
                } else {
                    chat.append("\n[System] Error: Invalid command format. Use /save <filename>\n");
                }
            }
            
            else if (msg.toLowerCase().equals("/load")) {
                
                File saveDir = new File("Saves");
                if (!saveDir.exists() || !saveDir.isDirectory()) {
                    chat.append("\n[System] No save directory found.\n");
                } else {
                    File[] files = saveDir.listFiles((dir, name) -> name.startsWith("chat_log_") && name.endsWith(".txt"));
                    if (files == null || files.length == 0) {
                        chat.append("\n[System] No chat logs found.\n");
                    } else {
                        File latest = files[0];
                        for (File f : files) {
                            if (f.lastModified() > latest.lastModified()) {
                                latest = f;
                            }
                        }
                        try (BufferedReader reader = new BufferedReader(new FileReader(latest))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            chat.setText("[System] Loaded chat from " + latest.getPath() + "\n\n");
                            chat.append(sb.toString());
                            
                        } catch (IOException e) {
                            chat.append("\n[System] Error loading chat log.\n");
                        }
                    }
                }
                chat.setCaretPosition(chat.getDocument().getLength());
            } else if (msg.toLowerCase().startsWith("/load ")) {
                String[] parts = msg.split(" ", 2);
                if (parts.length == 2) {
                    String fileName = parts[1].trim();
                    if (!fileName.toLowerCase().endsWith(".txt")) {
                        fileName += ".txt";
                    }
                    File saveDir = new File("Saves");
                    File loadFile = new File(saveDir, fileName);
                    if (!loadFile.exists() || !loadFile.isFile()) {
                        chat.append("\n[System] Error: File \"" + fileName + "\" does not exist.\n");
                    } else {
                        try (BufferedReader reader = new BufferedReader(new FileReader(loadFile))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            chat.setText("[System] Loaded chat from " + loadFile.getPath() + "\n\n");
                            chat.append(sb.toString());
                        } catch (IOException e) {
                            chat.append("\n[System] Error loading chat log.\n");
                        }
                    }
                } else {
                    chat.append("\n[System] Error: Invalid command format. Use /load <filename> or /load to load latest default log\n");
                }
                chat.setCaretPosition(chat.getDocument().getLength());
            }
            
            
            
            
            else if (msg.toLowerCase().equals("/viewlogs")) {
                File saveDir = new File("Saves");
                if (!saveDir.exists() || !saveDir.isDirectory()) {
                    chat.append("\n[System] No save directory found.\n");
                } else {
                    String[] files = saveDir.list();
                    if (files != null && files.length > 0) {
                        chat.append("\n[System] Available logs:\n");
                        for (String file : files) {
                            chat.append(file + "\n");
                        }
                    } else {
                        chat.append("\n[System] No logs available.\n");
                    }
                }
                chat.setCaretPosition(chat.getDocument().getLength());
            }

            else if (msg.toLowerCase().startsWith("/deletelog ")) {
                String[] parts = msg.split(" ");
                if (parts.length != 2) {
                    chat.append("\n[System] Error: Invalid command format. Use /deletelog <filename.txt> or use /deleteLogs to delete all logs. \n");
                    chat.setCaretPosition(chat.getDocument().getLength());
                } else {
                    String fileName = parts[1];
                    if (!fileName.toLowerCase().endsWith(".txt")) {
                        fileName += ".txt";
                    }
                    File fileToDelete = new File("Saves", fileName);
                    if (!fileToDelete.exists() || !fileToDelete.isFile()) {
                        chat.append("\n[System] Error: File \"" + fileName + "\" does not exist.\n");
                    } else {
                        if (fileToDelete.delete()) {
                            chat.append("\n[System] Deleted: " + fileName + "\n");
                        } else {
                            chat.append("\n[System] Failed to delete: " + fileName + "\n");
                        }
                    }
                    chat.setCaretPosition(chat.getDocument().getLength());
                }
            }
            
            else if (msg.toLowerCase().equals("/deletelogs")) {
                if (!deleteconfirmation) {
                    chat.append("\n[System] Are you sure you want to permanently delete all chat logs?\n");
                    chat.append("\n[System] Retype command to confirm.\n");
                    chat.setCaretPosition(chat.getDocument().getLength());
                    deleteconfirmation = true;
                } else {
                    String[] files = new File("Saves").list();
                    if (files != null) {
                        for (String file : files) {
                            File f = new File("Saves/" + file);
                            if (f.delete()) {
                                chat.append("\n[System] Deleted: " + file + "\n");
                                chat.setCaretPosition(chat.getDocument().getLength());
                            } else {
                                chat.append("\n[System] Failed to delete: " + file + "\n");
                                chat.setCaretPosition(chat.getDocument().getLength());
                            }
                        }
                    } else {
                        chat.append("\n[System] No files found.\n");
                        chat.setCaretPosition(chat.getDocument().getLength());
                    }
                    deleteconfirmation = false;
                }
            }
                
                
                
                
                
                
                
                
                
                
                
                
                else if (msg.length() >= 1 && msg.substring(0,1).equals("/")) {
            chat.append("\n[System] Error: \"" + msg + "\" is not a valid command.\n");
            chat.setCaretPosition(chat.getDocument().getLength());
        } else {
            if (client != null && !client.isClosed()) {
                
            toServer.writeObject(msg);
            toServer.flush();
            }
        }
    }

    private void setDarkMode(boolean dark) {
        Color bg = dark ? Color.BLACK : Color.WHITE;
        Color fg = dark ? Color.WHITE : Color.BLACK;
        
        chat.setBackground(bg);
        chat.setForeground(fg);
        scroller.getViewport().setBackground(bg);
        
        clientList.setBackground(bg);
        clientList.setForeground(fg);
        
        input.setBackground(bg);
        input.setForeground(fg);
        isDarkMode = dark;
    }

    public static void main(String[] args) {
        new Client();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //ignore
    }
}