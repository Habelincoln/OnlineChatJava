import java.io.IOException;
import java.net.*;
import java.util.*;

public class Server {

    public final static String HOST = "127.0.0.1";
    public final static int PORT = 7808;

    private ServerSocket server;
    private final List<ClientSocket> connectedClients = new ArrayList<>();
    private int nextClientId = 1;
    private Map<Integer, String> clientNames = new HashMap<>();

    public Server() {
        try {
            server = new ServerSocket(PORT);
            System.out.println("Server started.");
            System.out.println("HOST: " + HOST);
            System.out.println("PORT: " + PORT);

            // client accepter thread
            new Thread(() -> {
                while (true) {
                    try {
                        ClientSocket newClient = new ClientSocket(server.accept());
                        int clientId = nextClientId++;
                        newClient.setID(clientId);
                        System.out.println("Accepted a client: " + clientId);
                        synchronized (connectedClients) {
                            connectedClients.add(newClient);
                        }
                        newClient.sendObject("[Server] Welcome: Client " + clientId);
                        broadcastClientList();
                        for (int key : clientNames.keySet()) {
                            newClient.sendObject("808:RENAME:" + key + ":" + clientNames.get(key));
                        }
                        broadcastMessage("[Server] Client " + clientId + " connected.", newClient);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                }
            }, "Client Accepter Thread").start();

            // thread to handle messages from clients and disconnects
            new Thread(() -> {
                while (true) {
                    List<ClientSocket> disconnected = new ArrayList<>();
                    List<ClientSocket> clientsSnapshot;
                    synchronized (connectedClients) {
                        clientsSnapshot = new ArrayList<>(connectedClients);
                    }
                    for (ClientSocket client : clientsSnapshot) {
                        try {
                            Object obj = client.receiveObject();
                            if (obj instanceof String msg && msg.length() > 0) {
                                if (msg.startsWith("808:RENAME:")) {
                                    broadcastMessage(msg, client);
                                    clientNames.put(client.getID(), msg.split(":")[3]);
                                } else if (msg.equals("808:REVERT")) {
                                    clientNames.remove(client.getID());
                                } else if (msg.startsWith("808:WHISPER:")) {
                                    int targetID = Integer.parseInt(msg.split(":")[2]);
                                    String whisperedMsg = msg.split(":")[3];
                                    whisperMsg(targetID, whisperedMsg, client);
                                }
                                    else {
                                    if (!clientNames.containsKey(client.getID())) {

                                        broadcastMessage("Client " + client.getID() + ": " + msg, client);

                                    } else {
                                        String name = clientNames.get(client.getID());
                                        broadcastMessage(name + " (" + client.getID() + ")" + ": " + msg, client);
                                    }
                                }
                            } else if (obj instanceof byte[] image) {
                                sendImage(image, client);
                                System.out.println("Received image from client " + client.getID());
                            }
                        } catch (Exception e) {
                            System.out.println("Client " + client.getID() + " disconnected.");
                            disconnected.add(client);
                        }
                    }
                    if (!disconnected.isEmpty()) {
                        synchronized (connectedClients) {
                            connectedClients.removeAll(disconnected);
                        }
                        for (ClientSocket client : disconnected) {
                            broadcastMessage("[Server] Client " + client.getID() + " disconnected.", null);
                        }
                        broadcastClientList();
                    }
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                }
            }, "Message Handler Thread").start();

        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    private void broadcastClientList() {
        HashMap<Integer, String> clientMap = new HashMap<>();
        synchronized (connectedClients) {
            for (ClientSocket client : connectedClients) {
                clientMap.put(client.getID(), "Client " + client.getID());
            }
            for (ClientSocket client : connectedClients) {
                try {
                    client.sendObject(clientMap);
                } catch (IOException e) {
                    e.printStackTrace(System.err);
                    }
            }
        }
    }

    private void broadcastMessage(String msg, ClientSocket except) {
        synchronized (connectedClients) {
            for (ClientSocket client : connectedClients) {
                if (client != except) {
                    try {
                        client.sendObject(msg);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
    }

    private void sendImage(byte[] image, ClientSocket except) {
        synchronized (connectedClients) {
            for (ClientSocket client : connectedClients) {
                if (client != except) {
                    try {
                        client.sendObject("image: " + except.getID());
                        client.sendObject(image);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
    }

    private void whisperMsg(int targetID, String msg, ClientSocket sendingClient) {
        synchronized (connectedClients) {
            for (ClientSocket client : connectedClients) {
                if (client.getID() == targetID) {
                    try {
                        client.sendObject("808:WHISPER:" + sendingClient.getID() + ":" + msg);
                    } catch (IOException e) {
                        e.printStackTrace(System.err);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        new Server();
    }
}