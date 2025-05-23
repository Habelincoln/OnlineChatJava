import java.io.*;
import java.net.*;

public class ClientSocket {

    final private Socket client;
    final private ObjectInputStream fromClient;
    final private ObjectOutputStream toClient;
    private int id;

    public ClientSocket(Socket socket) throws IOException {
        client = socket;
        toClient = new ObjectOutputStream(client.getOutputStream());
        toClient.flush();
        fromClient = new ObjectInputStream(client.getInputStream());
        client.setSoTimeout(50);
    }

    public Object receiveObject() throws IOException, ClassNotFoundException {
        try {
            return fromClient.readObject();
        } catch (SocketTimeoutException e) {
            return null;
        }
    }

    public void sendObject(Object obj) throws IOException {
        toClient.writeObject(obj);
        toClient.flush();
    }

    public boolean isConnected() {
        return !client.isClosed();
    }

    public void setID(int id) {
        this.id = id;
    }

    public int getID() {
        return id;
    }
}