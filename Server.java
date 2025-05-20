import java.io.IOException;
import java.net.*;
import java.util.*;


public class Server {
        
        // public final static String HOST = "173.70.40.145"; // for online use
         public final static String HOST = "127.0.0.1"; //for local use

        public final static int PORT = 7808; //for wireless
        //    public final static int PORT = 7809; //for wired
        
        private ServerSocket server;

    private ArrayList<ClientSocket> connectedClients = new ArrayList<>();

    private int nextClientId = 1;
    private Map<Integer, ClientSocket> clientMap = new HashMap<>();

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
                            int clientId = nextClientId++;
                            clientMap.put(clientId, newClient);
                            newClient.setID(clientId);
                            System.out.println("Accepted a client: " + newClient);
                            connectedClients.add(newClient);
                            newClient.send("WELCOME: Client " + clientId);
                            
                            int j = connectedClients.indexOf(newClient);
                            for (int i = 0; i < connectedClients.size(); i++) {
                                ClientSocket client = connectedClients.get(i);
                                if (i != j) {
                                    try {
                                        
                                        client.send("[Server] Client " + client.getID() + " connected.");
                                        broadcastClientList();

                                    } catch (IOException e) {
                                        e.printStackTrace(System.err);
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
                        
                        String message = "Client " + client.getID() + ": " + client.receive();
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
                        System.out.println("Client " + client.getID() + " disconnected.");
                        disconnectedClients.add(i);
                        for (int j = 0; j < connectedClients.size(); j++) {
                            if (i != j) {
                                
                                connectedClients.get(j).send("[Server] Client " + client.getID() + " has disconnected.");
                                
                                
                                Thread.sleep(1);
                            }
                        } 
                        //remove all disconnected clients from live clients array
                        for (int m : disconnectedClients) {
                            clientMap.remove(connectedClients.get(m).getID());
                            connectedClients.remove(m);
                            System.out.println("Removed disconnected client: Client " + client.getID());
                            
    
                        }   
                    }
                            

                }
                
            
            
            } catch (Exception e){}
            
            
            } 
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
            
           
           

    }

    private void broadcastClientList() throws IOException {
        for (ClientSocket client : connectedClients) {
            int clientId = client.getID();
            StringBuilder sb = new StringBuilder("CLIENT_LIST:");
            for (ClientSocket other : connectedClients) {
                if (other.getID() != clientId) {
                    sb.append("Client ").append(other.getID()).append("\n");
                }
            }
            client.send(sb.toString());
        }
    }

    public static void main(String[]args) throws InterruptedException {

        new Server();
    }

    

}

