import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Paths;
 
public class Client {

    final static String hostConfigPath = "C:\\GitHub\\OnlineChatPrototype\\Host.config";
    final static String portConfigPath = "C:\\GitHub\\OnlineChatPrototype\\Port.config";
            
    private Socket client;
    private int selfID;

    private DataInputStream fromServer; 
    private DataOutputStream toServer;

    private JFrame window;
    private JTextArea chat;
    private JScrollPane scroller;
    private JTextField input;
    private JTextArea clientList;
    private JScrollPane listScroller;

    private volatile boolean attemptingReconnect = false;
    private volatile boolean windowOpen = true;

    private ClientGraphics graphics;

   
   public Client() {
    
    input = new JTextField();
    chat = new JTextArea(31, 71);
    window = new JFrame("Chat");
    scroller =  new JScrollPane(chat, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    listScroller =  new JScrollPane(clientList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    clientList = new JTextArea(31, 9);

    //  graphics = new ClientGraphics(chat, clientList, input); temporarily removed
    try {
    chat.setText("[System] Connecting to: " + new String(Files.readAllBytes(Paths.get(hostConfigPath))) + "...\n");
    } catch (IOException e) {
        e.printStackTrace(System.err);
    }
    clientList.setText("Connected Clients: \n ______________\n You\n -----\n");

    input.addKeyListener(new KeyListener() {
       @Override
       public void keyTyped(KeyEvent e){}
       @Override
       public void keyPressed(KeyEvent e){}
       @Override
       public void keyReleased(KeyEvent e) {
           
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            String message = input.getText();
            if (message.length() > 0 && message.length() < 251) {
            try {
                
            send(message);

            } catch (IOException ex){
                 ex.printStackTrace(System.err);
                }
            chat.append("You: " + input.getText() + "\n");
            input.setText("");
            
            }
      
        } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (client != null) {
                if (!client.isClosed() ) {
                } else {
                chat.setText("[System] No server connection. \n");
                }
            } else {
                chat.setText("[System] No server connection. Connecting... \n");
            }
        }
    
    }
    });


    chat.setEditable(false);
    clientList.setEditable(false);
    
    chat.addKeyListener(new KeyListener() {

        @Override
        public void keyTyped(KeyEvent e){}
        @Override
        public void keyPressed(KeyEvent e){}
        @Override
        public void keyReleased(KeyEvent e){

            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                if (!client.isClosed()) {
                    chat.setText("Welcome to the server! \n");
                    } else {
                        chat.setText("[System] No server connection. \n");
                    }
            }

        }

    });

    
    window.setVisible(true);
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); 
    window.setLocationRelativeTo(null);
    window.setResizable(false);
    window.setSize(1000,600);

    // window.add(chat, BorderLayout.PAGE_START);
    window.add(input, BorderLayout.PAGE_END);
    window.add(scroller);
    window.add(clientList, BorderLayout.WEST);
    // window.add(listScroller);
    

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
            fromServer = new DataInputStream(client.getInputStream());
            toServer = new DataOutputStream(client.getOutputStream());
            attemptingReconnect = false;
            
            Thread serverConnectThread = new Thread(new Runnable() {
                @Override
                public void run() {
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
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                        handleServerDisconnect();
                    }
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
            if (client != null) {
            if (!client.isClosed()) {
                fromServer.close();
                toServer.close();
                client.close();
            }
        }
        } catch (IOException ex) {
            ex.printStackTrace(System.err);
        }

        if (!attemptingReconnect && windowOpen) {
            attemptingReconnect = true;
            Thread reconnectThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (attemptingReconnect && windowOpen) {
                        try {
                            Thread.sleep(1000);
                            if (client != null) {
                                if (!chat.getText().contains("[System] Attempting to reconnect...\n")) {
                            chat.append("[System] Attempting to reconnect...\n");
                                }
                            } else if (client == null) {
                                if (!chat.getText().contains("[System] Server connection failed. Waiting for server...\n")) {
                                    chat.append("[System] Server connection failed. Waiting for server...\n");
                                        }
                            }
                            connectToServer();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            });
            reconnectThread.start();
        }
    }

public synchronized void receive () throws IOException {
    try {
        String msg = fromServer.readUTF();
        if (!msg.contains("null")) {
            if (msg.contains("CLIENT_LIST:")) {
                clientList.setText("Connected Clients: \n ______________\n");
                clientList.append("You ( " + selfID + ")\n");
                clientList.append(msg.substring(12));

            } else if (msg.contains("WELCOME: Client")) {
                selfID = Integer.parseInt(msg.substring(16));
                System.out.println("my ID: " + selfID);
                chat.append(msg);
            
            } else {
            chat.append(msg + "\n");
            }
        }
    } catch (SocketException e) {
        handleServerDisconnect();
        throw e;
    }
}

public void send (String msg) throws IOException {
        if (client != null && !client.isClosed()) {
            toServer.writeUTF(msg);
        }
}
   
    public static void main(String[]args) {
    
        new Client();


    }
}
