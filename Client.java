import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.swing.*;


public class Client {
   
    private Socket client;

   private DataInputStream fromServer; 
   private DataOutputStream toServer;

    private JFrame window;
    private JTextArea chat;

    
    private JTextField input;

   
   public Client(){
    
    input = new JTextField();
    chat = new JTextArea();
    window = new JFrame("Chat");

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
            chat.setText("Welcome to the server!" + "\n");
        }

    }
    });

    chat.setEditable(false);

    window.setVisible(true);
    window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    window.setLocationRelativeTo(null);
    window.setResizable(false);
    window.setSize(800,600);

    window.add(chat, BorderLayout.PAGE_START);
    window.add(input, BorderLayout.PAGE_END);
     
    try {
        client = new Socket(Server.HOST, Server.PORT);
        client.setSoTimeout(30000); // Set timeout to 30 seconds
        fromServer = new DataInputStream(client.getInputStream());
        toServer = new DataOutputStream(client.getOutputStream());
        
        
           //constantly check for messages
           
           Thread serverConnectThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                //get inital welcome to server msg
                    if (client.isConnected()){
                    
                    recieve();
                 } else {   
                     System.err.println("Could not connect");
                 }
                 //in its own thread, constantly check for msgs
                 while (!client.isClosed()) {
                    
                        recieve();
                        
                    
                }
            }  catch (IOException e) {
                e.printStackTrace(System.err);  
            }
        }
           });
           serverConnectThread.start();

    } catch (IOException e) {
        e.printStackTrace(System.err);
    }
   }

public synchronized void recieve () throws IOException {

    String msg = fromServer.readUTF();
    if (!msg.contains("null")) {
    chat.append(msg + "\n");
    }
}

public void send (String msg) throws IOException {
       
            toServer.writeUTF(msg);
}
   
    public static void main(String[]args){
    
        new Client();


    }
}
