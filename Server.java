import java.io.IOException;
import java.net.*;
import java.util.*;


public class Server {
        
        public final static String HOST = "173.70.37.213"; // for online use
        //  public final static String HOST = "127.0.0.1"; //for local use

        // public final static int PORT = 7808; //for wireless
           public final static int PORT = 7809; //for wired
        
        private ServerSocket server;

    private ArrayList<ClientSocket> connectedClients = new ArrayList<>();

    private Thread clientAccepterThread;

    public Server () throws InterruptedException {

        try {

            server = new ServerSocket(Server.PORT);
            System.out.println("Server started.");
            System.out.println("HOST: " + HOST);
            System.out.println("PORT: " + PORT);
            //start accepting clients
            (clientAccepterThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    //accept clients forever
                    System.out.println("Client Accepter Thread started.");
                    while (true) { 
                        try {
                            
                            //accept new client
                            ClientSocket newClient = new ClientSocket(server.accept());
                            System.out.println("Accepted a client: " + newClient);
                            connectedClients.add(newClient);
                            newClient.send("Welcome to the server!");
                            int j = connectedClients.indexOf(newClient);
                            for (int i = 0; i < connectedClients.size(); i++) {
                                ClientSocket client = connectedClients.get(i);
                                if (i != j) {
                                    try {
                                        
                                        client.send("[Server] Client " + (connectedClients.indexOf(newClient) + 1) + " connected.");

                                    } catch (IOException e) {
                                    }
                                }
                            }

                        } catch (IOException ex) {
                            ex.printStackTrace(System.err);
                        }
                        
                    }
                }
            }, "Client Accepter Thread")).start();
            
            while (true) { 
                try {
                    Thread.sleep(1);
                ArrayList<Integer> disconnectedClients = new ArrayList<>();

                //check for disconnected clients and send/receive msgs
                for (int i = 0; i < connectedClients.size(); i++) {
                    ClientSocket client = connectedClients.get(i);
                    try {
                    if (client.isConnected()) {
                        
                        String message = "Client " + (i + 1)+ ": " + client.receive();
                        Thread.sleep(1);
                        
                        
                        
                        //send out msgs
                                
                            for (int j = 0; j < connectedClients.size(); j++) {
                                if (i != j) {
                                    
                                    connectedClients.get(j).send(message);
                                    
                                    
                                    Thread.sleep(1);
                                }
                            } 
                        

                    }
                 } catch (IOException ex) {
                        System.out.println("Client " + (i + 1) + " disconnected.");
                        disconnectedClients.add(i);
                        for (int j = 0; j < connectedClients.size(); j++) {
                            if (i != j) {
                                
                                connectedClients.get(j).send("[Server] Client " + (i + 1) + " disconnected.");
                                
                                
                                Thread.sleep(1);
                            }
                        } 
        
                    }


                }
                //remove all disconnected clients from live clients array
            for (int i : disconnectedClients) {
                connectedClients.remove(i);
                System.out.println("Removed disconnected client: Client " + (i + 1));
                }
            
            } catch (Exception e){}
            
            
            } 
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
            
           
           

}
    public static void main(String[]args) throws InterruptedException {
        System.out.println("Host: " + HOST);
        System.out.println("Port: " + PORT);

        new Server();
    }

    

}

