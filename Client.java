import java.awt.*;
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
        try {
            chat.setText("Press CTRL + D to toggle dark mode.\n");
            chat.append("Use /setName <ID> <New Name> to change a client's name.\n");
            chat.append("[System] Connecting to: " + new String(Files.readAllBytes(Paths.get(hostConfigPath))) + "...\n");
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
                        chat.append("[System] Connected to server!\n");
                    }
                    while (!client.isClosed()) {
                        receive();
                    }
                } catch (SocketException e) {
                    chat.append("[System] Server connection lost.\n");
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
                int key = entry.getKey();
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
        if (client != null && !client.isClosed()) {
            
            if (msg.length() >=8 && msg.substring(0,8).equals("/setName")) {
                int firstSpace = msg.indexOf(' ', 8);
                int secondSpace = msg.indexOf(' ', firstSpace + 1);
                int clientToChange = Integer.parseInt(msg.substring(firstSpace + 1, secondSpace));
                String newName = msg.substring(secondSpace + 1) + " (" + clientToChange + ")";
                if (clientToChange == selfID) {
                    chat.append("[System] Error: You cannot change your own name.\n");
                } else {
                    changedClients.put(clientToChange, newName);
                    updateClientList(changedClients, false);
                    chat.append("[System] Client " + clientToChange + "'s name has been changed to: " + newName + "\n");
                }
            } else {
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
    }

    public static void main(String[] args) {
        new Client();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        //ignore
    }
}