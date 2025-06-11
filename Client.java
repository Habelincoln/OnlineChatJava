import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import javax.imageio.*;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

//TODO: make separate changed clients list for self renames so those dont get reset when using resetName command, as those should be the new default name (maybe make it server side)
public class Client implements ActionListener {

    final static String hostConfigPath = "C:\\GitHub\\OnlineChatPrototype\\Host.config";
    final static String portConfigPath = "C:\\GitHub\\OnlineChatPrototype\\Port.config";

    private Socket client;
    private int selfID;
    private String selfName = "You";

    private ObjectInputStream fromServer;
    private ObjectOutputStream toServer;

    private JFrame window;
    private JTextPane chat;
    private JScrollPane scroller;
    private JTextField input;
    private JTextArea clientList;
    private volatile boolean attemptingReconnect = false;
    private volatile boolean windowOpen = true;
    private volatile boolean isDarkMode = false;
    private volatile boolean deleteconfirmation = false;

    private HashMap<Integer, String> changedClients = new HashMap<>();
    private HashMap<Integer, String> tempMap = new HashMap<>();

    private final List<File> sentImages = new ArrayList<>();

    public Client() {
        input = new JTextField();
        chat = new JTextPane();
        chat.setEditable(false);
        window = new JFrame("Chat");
        scroller = new JScrollPane(chat, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        clientList = new JTextArea(31, 9);
        
        clientList.setLineWrap(true);
        
        chat.setFont(new Font("ARIAL", Font.PLAIN, 15));
        clientList.setFont(new Font("ARIAL", Font.PLAIN, 15));
        clientList.setText("Client List");
        input.setFont(new Font("ARIAL", Font.PLAIN, 15));



        try {
            append("[System] Use /help to view all commands.\n");
            append("\n[System] Connecting to: " + new String(Files.readAllBytes(Paths.get(hostConfigPath))) + "...\n");
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
                        append("You" + ": " + input.getText() + "\n");
                        }
                        input.setText("");
                    }
                }
            }
        });

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
                // Delete temp images folder and its contents
                File tempImagesDir = new File("Saves", "current_session_images");
                if (tempImagesDir.exists() && tempImagesDir.isDirectory()) {
                    File[] files = tempImagesDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            f.delete();
                        }
                    }
                    tempImagesDir.delete();
                }
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
                        append("\n[System] Connected to server!\n");
                    }
                    while (!client.isClosed()) {
                        receive();
                    }
                } catch (SocketException | EOFException e) {
                    append("\n[System] Server connection lost.\n");
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
            if (client != null && !client.isClosed() && fromServer != null && toServer != null) {
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
            if (!msg.startsWith("image: ")) {
                if (msg.contains("[Server] Welcome: Client")) {
                    selfID = Integer.parseInt(msg.substring(24).trim());
                    append(msg + "\n");
                } else if (msg.startsWith("808:RENAME:")) {
                    //  format is "808:RENAME:<id>:<newName>"
                    String[] parts = msg.split(":", 4);
                    if (parts.length == 4) {
                        try {
                            int sendingClient = Integer.parseInt(parts[2].trim());
                            String newName = parts[3].trim() + " (" + parts[2] + ")";
                            changedClients.put(sendingClient, newName);
                            updateClientList(changedClients, false);
                            append("\n[System] Client " + sendingClient + " has changed its name to: " + newName + "\n");
                        } catch (NumberFormatException e) {
                            append("\n[System] Error parsing client ID in rename message: " + msg + "\n");
                        }
                    } else {
                        append("\n[System] Malformed rename message: " + msg + "\n");
                    }
                } else if (msg.startsWith("808:WHISPER:")) {
                    int sendingClient = Integer.parseInt(msg.split(":")[2]);
                    String whisperedMsg = msg.split(":")[3];
                    String senderName = changedClients.getOrDefault(sendingClient, "Client " + sendingClient);
                    append("\n[ " + senderName + "  ->  You " + "]: " + whisperedMsg + "\n");

                } else {
                    int firstSpace = msg.indexOf(' ');
                    int colonIndex = msg.indexOf(':');
                    if (firstSpace != -1 && colonIndex != -1 && colonIndex > firstSpace) {
                        String clientIdStr = msg.substring(firstSpace + 1, colonIndex).trim();
                        try {
                            int clientId = Integer.parseInt(clientIdStr);
                            if (changedClients.containsKey(clientId)) {
                                String newName = changedClients.get(clientId);
                                msg = msg.replaceFirst("Client " + clientIdStr + ":", newName + ":");
                            }
                        } catch (NumberFormatException ignored) {
                            // not a client message, ignore
                        }
                    }
                    // if theres 2 '('s in the msg, remove the second '(' and everything after it until the next ')'
                    int firstParen = msg.indexOf('(');
                    int secondParen = msg.indexOf('(', firstParen + 1);
                    if (firstParen != -1 && secondParen != -1) {
                        int closeParen = msg.indexOf(')', secondParen);
                        if (closeParen != -1) {
                            msg = msg.substring(0, secondParen) + msg.substring(closeParen + 1);
                        }
                    }
                    append(msg + "\n");
                }
            } else {
                int imageSendingClient = Integer.parseInt(msg.substring(7));
                append("\n Client " + imageSendingClient + ": \n");
            }
        } else if (obj instanceof HashMap) {
            @SuppressWarnings("unchecked")
            HashMap<Integer, String> clientMap = (HashMap<Integer, String>) obj;
            updateClientList(clientMap, true);
            tempMap = clientMap;
        } else if (obj instanceof byte[] imageBytes) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));

            File tempImagesDir = new File("Saves", "current_session_images");
            if (!tempImagesDir.exists()) tempImagesDir.mkdirs();
            // make folder hidden for windows
            try {
                java.nio.file.Files.setAttribute(tempImagesDir.toPath(), "dos:hidden", true);
            } catch (IOException ignore) {}
            File receivedFile = new File(tempImagesDir, "received_" + System.currentTimeMillis() + ".png");
            try (FileOutputStream fos = new FileOutputStream(receivedFile)) {
                fos.write(imageBytes);
                fos.flush();
                // set file as hidden for windows
                try {
                    java.nio.file.Files.setAttribute(receivedFile.toPath(), "dos:hidden", true);
                } catch (IOException ignore) {}
                sentImages.add(receivedFile);
            } catch (IOException e) {
                e.printStackTrace(System.err);
            }


            JLabel imageLabel = new JLabel(new ImageIcon(image));
            Image scaledImage = image.getScaledInstance(400, 400, Image.SCALE_SMOOTH);
            imageLabel.setIcon(new ImageIcon(scaledImage));
            StyledDocument doc = chat.getStyledDocument();
            try {
                append("\n");
                chat.setCaretPosition(doc.getLength());
                chat.insertComponent(imageLabel);
                doc.insertString(doc.getLength(), "\n", null);
            } catch (BadLocationException ex) {
                ex.printStackTrace(System.err);
            }
        }
    }
    private void updateClientList(HashMap<Integer, String> clientMap, boolean isNewList) {
        if (isNewList) {
            clientList.setText("Clients: \n ____________\n");
            clientList.append(selfName + " (You)\n");
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
            clientList.setText("Clients: \n ____________\n");
            clientList.append(selfName + " (You)\n");
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
        } else if (msg.length() >=8 && msg.toLowerCase().startsWith("/rename")) {
            int firstSpace = msg.indexOf(' ', 7);
            int secondSpace = msg.indexOf(' ', firstSpace + 1);
            int clientToChange = Integer.parseInt(msg.substring(firstSpace + 1, secondSpace));
            String newName = msg.substring(secondSpace + 1) + " (" + clientToChange + ")";
            
            if (clientToChange == selfID) {
                append("\n[System] Error: You cannot change your own name.\n");

            } else if (!tempMap.containsKey(clientToChange)) {
                append("\n[System] Error: Client " + clientToChange + " does not exist.\n");

            } else {
                changedClients.put(clientToChange, newName);
                updateClientList(changedClients, false);
                append("\n[System] Client " + clientToChange + "'s name has been changed to: " + newName + "\n");
            }

        } else if (msg.length() >=9 && msg.toLowerCase().startsWith("/darkmode")) {

            if (isDarkMode){setDarkMode(false);}
            else {setDarkMode(true);}

        } else if (msg.length() >= 5 && msg.toLowerCase().startsWith("/help")) {
            append("\n");
            append("[System] Commands:\n");
            append("[System] /help - View all commands.\n");
            append("[System] /rename <clientID> <newName> - Change the name of a client.\n");
            append("[System] /darkMode (or CTRL + D) - Toggle dark mode.\n");
            append("[System] /clear - Clear chat.\n");
            append("[System] /resetName - Reset all names.\n");
            append("[System] /resetName <clientID> - Reset one client's name.\n");
            append("[System] /copy - Copy chat log to clipboard.\n");
            append("[System] /exit - Exit the program.\n");
            append("[System] /save - Save chat log to files.\n");
            append("[System] /saveWithImages - Save chat log and images to files.\n");
            append("[System] /save <logName> - Save chat log to files with custom name.\n");
            append("[System] /saveWithImages <logName> - Save chat log and images to files with custom name.\n");
            append("[System] /viewLogs - View all saved logs.\n");
            append("[System] /load <logName> - Load a specific log.\n");
            append("[System] /load - Load the latest default named chat log.\n");
            append("[System] /deleteLog <logName> - Delete a specific log.\n");
            append("[System] /deleteLogs - Delete all chat logs.\n");
            append("[System] /image - Send an image.\n");
            append("[System] /self <newName> - Change your default display name for others.\n");
            append("[System] /resetSelf - Reset your display name to the default.\n");
            append("[System] /whisper <Target ID or Name> <Message> - Send a message to one person\n");



            
        } else if (msg.length() >= 6 && msg.toLowerCase().startsWith("/clear")) {
            sentImages.clear();
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
        } else if (msg.toLowerCase().startsWith("/resetname")) {
            String[] parts = msg.trim().split(" "); // split by whitespace
            int clientToChange = -1;

            if (parts.length < 2) {
                changedClients.clear();
                updateClientList(changedClients, false);
            } else if (parts.length > 2) {
                append("\n[System] Error: \"" + msg + "\" is not a valid command.\n");
            } else {
                try {
                    clientToChange = Integer.parseInt(parts[1]);

                    if (clientToChange == selfID) {
                        append("\n[System] Error: You cannot change your own name.\n");
                    } else {
                        changedClients.remove(clientToChange);
                        updateClientList(changedClients, false);
                        append("\n[System] Client " + clientToChange + "'s name has been reset.\n");
                    }

                } catch (NumberFormatException e) {
                    append("\n[System] Error: \"" + parts[1] + "\" is not a valid number.\n");
                }
            }

        } else if(msg.toLowerCase().startsWith("/copy")) {

            StringSelection stringSelection = new StringSelection(chat.getText());
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, null);
            append("\n[System] Chat copied to clipboard.\n");
        } else if (msg.toLowerCase().startsWith("/exit")) {
            append("\n[System] Exiting...\n");
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
                String baseName = "chat_log_" + System.currentTimeMillis();
                File saveDir = new File("Saves", baseName);
                if (!saveDir.exists()) saveDir.mkdirs();
                File saveFile = new File(saveDir, baseName + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                    writer.write(chat.getText());
                }
                saveSentImagesWithLogName(saveFile.getPath());
                append("\n[System] Chat and images saved to " + saveFile.getPath() + "\n");
            } catch (IOException e) {
                append("\n[System] Error saving chat log.\n");
            }
        } else if (msg.toLowerCase().startsWith("/save ")) {
            String[] parts = msg.split(" ");
            if (parts.length == 2) {
                String baseName = parts[1];
                if (baseName.toLowerCase().endsWith(".txt")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                File saveDir = new File("Saves", baseName);
                if (!saveDir.exists()) saveDir.mkdirs();
                File saveFile = new File(saveDir, baseName + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                    writer.write(chat.getText());
                }
                saveSentImagesWithLogName(saveFile.getPath());
                append("\n[System] Chat and images saved to " + saveFile.getPath() + "\n");
            } else {
                append("\n[System] Error: Invalid command format. Use /save <filename>\n");
            }
        } else if (msg.toLowerCase().equals("/savewithimages")) {
            try {
                String baseName = "chat_log_" + System.currentTimeMillis();
                File saveDir = new File("Saves", baseName);
                if (!saveDir.exists()) saveDir.mkdirs();
                File saveFile = new File(saveDir, baseName + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                    writer.write(chat.getText());
                }
                saveSentImagesWithLogName(saveFile.getPath());
                append("\n[System] Chat and images saved to " + saveFile.getPath() + "\n");
            } catch (IOException e) {
                append("\n[System] Error saving chat log.\n");
            }
        } else if (msg.toLowerCase().startsWith("/savewithimages")) {
            String[] parts = msg.split(" ");
            if (parts.length == 2) {
                String baseName = parts[1];
                if (baseName.toLowerCase().endsWith(".txt")) {
                    baseName = baseName.substring(0, baseName.length() - 4);
                }
                File saveDir = new File("Saves", baseName);
                if (!saveDir.exists()) saveDir.mkdirs();
                File saveFile = new File(saveDir, baseName + ".txt");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(saveFile))) {
                    writer.write(chat.getText());
                }
                saveSentImagesWithLogName(saveFile.getPath());
                append("\n[System] Chat and images saved to " + saveFile.getPath() + "\n");
            } else {
                append("\n[System] Error: Invalid command format. Use /saveWithImages <filename>\n");
            }
        }
            
            else if (msg.toLowerCase().equals("/load")) {
                
                File saveDir = new File("Saves");
                if (!saveDir.exists() || !saveDir.isDirectory()) {
                    append("\n[System] No save directory found.\n");
                } else {
                    File[] files = saveDir.listFiles((dir, name) -> name.startsWith("chat_log_") && name.endsWith(".txt"));
                    if (files == null || files.length == 0) {
                        append("\n[System] No chat logs found.\n");
                    } else {
                        sentImages.clear();
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
                            append(sb.toString());
                            
                        } catch (IOException e) {
                            append("\n[System] Error loading chat log.\n");
                        }
                    }
                }
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
                        append("\n[System] Error: File \"" + fileName + "\" does not exist.\n");
                    } else {
                        try (BufferedReader reader = new BufferedReader(new FileReader(loadFile))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            chat.setText("[System] Loaded chat from " + loadFile.getPath() + "\n\n");
                            append(sb.toString());
                        } catch (IOException e) {
                            append("\n[System] Error loading chat log.\n");
                        }
                    }
                } else {
                    append("\n[System] Error: Invalid command format. Use /load <filename> or /load to load latest default log\n");
                }
                chat.setCaretPosition(chat.getDocument().getLength());
            }
            
            
            
            
            else if (msg.toLowerCase().equals("/viewlogs")) {
                File saveDir = new File("Saves");
                if (!saveDir.exists() || !saveDir.isDirectory()) {
                    append("\n[System] No save directory found.\n");
                } else {
                    String[] files = saveDir.list();
                    if (files != null && files.length > 0) {
                        append("\n[System] Available logs:\n");
                        for (String file : files) {
                            append(file + "\n");
                        }
                    } else {
                        append("\n[System] No logs available.\n");
                    }
                }
                chat.setCaretPosition(chat.getDocument().getLength());
            }

            else if (msg.toLowerCase().startsWith("/deletelog ")) {
                String[] parts = msg.split(" ");
                if (parts.length != 2) {
                    append("\n[System] Error: Invalid command format. Use /deletelog <filename.txt> or use /deleteLogs to delete all logs. \n");
                    chat.setCaretPosition(chat.getDocument().getLength());
                } else {
                    String fileName = parts[1];
                    if (!fileName.toLowerCase().endsWith(".txt")) {
                        fileName += ".txt";
                    }
                    File fileToDelete = new File("Saves", fileName);
                    if (!fileToDelete.exists() || !fileToDelete.isFile()) {
                        append("\n[System] Error: File \"" + fileName + "\" does not exist.\n");
                    } else {
                        if (fileToDelete.delete()) {
                            append("\n[System] Deleted: " + fileName + "\n");
                        } else {
                            append("\n[System] Failed to delete: " + fileName + "\n");
                        }
                    }
                    chat.setCaretPosition(chat.getDocument().getLength());
                }
            }
            
            else if (msg.toLowerCase().equals("/deletelogs")) {
                if (!deleteconfirmation) {
                    append("\n[System] Are you sure you want to permanently delete all chat logs?\n");
                    append("\n[System] Retype command to confirm.\n");
                    chat.setCaretPosition(chat.getDocument().getLength());
                    deleteconfirmation = true;
                } else {
                    String[] files = new File("Saves").list();
                    if (files != null) {
                        for (String file : files) {
                            File f = new File("Saves/" + file);
                            if (f.delete()) {
                                append("\n[System] Deleted: " + file + "\n");
                                chat.setCaretPosition(chat.getDocument().getLength());
                            } else {
                                append("\n[System] Failed to delete: " + file + "\n");
                                chat.setCaretPosition(chat.getDocument().getLength());
                            }
                        }
                    } else {
                        append("\n[System] No files found.\n");
                        chat.setCaretPosition(chat.getDocument().getLength());
                    }
                    deleteconfirmation = false;
                }
            }

            else if (msg.toLowerCase().equalsIgnoreCase("/image")) {
                if (fromServer != null) {
                    FileDialog fileDialog = new FileDialog(window, "Select an image to send", FileDialog.LOAD);
                    fileDialog.setVisible(true);
                    String directory = fileDialog.getDirectory();
                    String filename = fileDialog.getFile();
                    if (filename != null && directory != null) {
                        File selectedFile = new File(directory, filename);
                        BufferedImage image = ImageIO.read(selectedFile);
                        if (image != null) {
                            sendImage(image, selectedFile);
                            append("\n" + selfName + ":\n");
                            JLabel imageLabel = new JLabel(new ImageIcon(image));
                            Image scaledImage = image.getScaledInstance(400, 400, Image.SCALE_SMOOTH);
                            imageLabel.setIcon(new ImageIcon(scaledImage));
                            StyledDocument doc = chat.getStyledDocument();
                            try {
                                chat.setCaretPosition(doc.getLength());
                                chat.insertComponent(imageLabel);
                                doc.insertString(doc.getLength(), "\n", null);
                                chat.setCaretPosition(doc.getLength());
                            } catch (BadLocationException ex) {
                                ex.printStackTrace(System.err);
                            }
                        } else {
                            append("\n[System] Error: Not an image.\n");
                        }
                    } else {
                        append("\n[System] Image sending cancelled.\n");
                    }
                } else {
                    append("\n[System] Error: Cannot choose an image while disconnected from server.\n");
                }
            } 
            
            else if (msg.toLowerCase().startsWith("/self ")) {
                String newName = msg.substring(6).trim();
                send("808:RENAME:" + selfID + ":" + newName);
                selfName = newName + " (" + selfID + ")";
                changedClients.put(selfID, selfName);
                updateClientList(changedClients, false);
                append("\n[System] Your display name has been changed to: " + newName + "\n");
                
        }

        else if (msg.toLowerCase().equals("/resetself")) {
                String newName = "Client " + selfID;
                send("808:RENAME:" + selfID + ":" + newName);
                send("808:REVERT");
                selfName = newName;
                changedClients.put(selfID, selfName);
                updateClientList(changedClients, false);
                append("\n[System] Your display name has been reverted to: " + newName + "\n");
                
        } else if (msg.toLowerCase().startsWith("/whisper ")) {
            String[] parts = msg.split(" ", 3);
            if (parts.length < 3) {
                append("\n[System] Error: Invalid whisper command format. Use /whisper <Target ID or Name> <Message>\n");
            } else {
                String target = parts[1];
                String message = parts[2];
                int targetID = -1;
                try {
                    targetID = Integer.parseInt(target);
                } catch (NumberFormatException e) {
                    // not a number, check if it's a name
                    for (var entry : tempMap.entrySet()) {
                        String name = entry.getValue();
                        // also check changedClients for renamed clients
                        if (changedClients.containsKey(entry.getKey())) {
                            name = changedClients.get(entry.getKey());
                        }
                        
                        if (name.equalsIgnoreCase(target) || name.toLowerCase().startsWith(target.toLowerCase() + " ")) {
                            targetID = entry.getKey();
                            break;
                        }
                    }
                }

                if (targetID == -1) {
                    
                    append("\n[System] Error:  \"" + target + "\" not found.\n");

                } else if (!tempMap.containsKey(targetID)) {
                    append("\n[System] Error: \"" + target + "\" is not connected.\n");
                } else if (targetID == selfID) {
                    append("\n[System] Error: You cannot whisper to yourself.\n");

                } else {
                    toServer.writeObject("808:WHISPER:" + targetID + ":" + message);
                    toServer.flush();
                    String displayName;
                    displayName = changedClients.getOrDefault(targetID, "Client " + targetID);
                    append("\n[ You -> " + displayName + " ]: " + message + "\n");
                }
            }
        }







                
                else if (msg.length() >= 1 && msg.substring(0,1).equals("/")) {
            append("\n[System] Error: \"" + msg + "\" is not a valid command.\n");
        } else {
            if (client != null && !client.isClosed()) {
                
            toServer.writeObject(msg);
            toServer.flush();
            }
        }
    }

    private void append(String text) {
        try {
            StyledDocument doc = chat.getStyledDocument();
            doc.insertString(doc.getLength(), text, null);
            chat.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
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

    private void sendImage(BufferedImage image, File imageAsFile) {
        try {
            
            sentImages.add(imageAsFile);
                //send out image
                byte[] imageBytes;
                try (ByteArrayOutputStream imageOutput = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "png", imageOutput);
                    imageOutput.flush();
                    imageBytes = imageOutput.toByteArray();
                }
            toServer.writeObject(imageBytes);
            toServer.flush();
        } catch (IOException e) {
            e.printStackTrace(System.err);
            append("\n[System] Error sending image.\n");
        }
        
    }

    private void saveSentImagesWithLogName(String logFilePath) {
        if (sentImages.isEmpty()) return;
        File logFile = new File(logFilePath);
        File parentDir = logFile.getParentFile();
        String baseName = logFile.getName();
        if (baseName.toLowerCase().endsWith(".txt")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        File imagesDir = new File(parentDir, baseName + "_images");
        imagesDir.mkdirs();
        int count = 1;
        for (File img : sentImages) {
            File dest = new File(imagesDir, "image_" + (count++) + ".png");
            try (
                FileInputStream in = new FileInputStream(img);
                FileOutputStream out = new FileOutputStream(dest)
            ) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.getFD().sync(); 
            } catch (IOException e) {
                append("\n[System] Error saving image: " + img.getName() + "\n");
            }
        }
    }

    public static void main(String[] args) {
        new Client();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //ignore
    }
}