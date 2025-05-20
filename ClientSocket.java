import java.io.*;
import java.net.*;

public class ClientSocket {

    private Socket client;
    private DataInputStream fromClient; 
    private DataOutputStream toClient;
    private int id;


    public ClientSocket(Socket socket) throws IOException {
        client = socket;
        
        
            fromClient = new DataInputStream(socket.getInputStream());
            toClient = new DataOutputStream(socket.getOutputStream());

           
            client.setSoTimeout(50);
    }
    
    public String receive() throws IOException {
        try {
            return fromClient.readUTF();
        } catch (SocketTimeoutException e) {
            return null; // return null on timeout instead of throwing ex
        }
    }
    
    public void send(String msg) throws IOException {
        
        if (msg.length() > 0 && msg.length() < 251) {
           
                toClient.writeUTF(msg);
        }
    }

    public boolean isConnected () {
        return !client.isClosed();
    }

    public void setID(int id) { 
        this.id = id;
    }

    public int getID() {
        return id;
    }

}

