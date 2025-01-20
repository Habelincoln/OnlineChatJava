import java.io.*;
import java.net.*;

public class ClientSocket {

    private Socket client;
   private DataInputStream fromClient; 
   private DataOutputStream toClient;



    public ClientSocket(Socket socket ) throws IOException {
        client = socket;
        
        
            fromClient = new DataInputStream(socket.getInputStream());
            toClient = new DataOutputStream(socket.getOutputStream());

            // Increase timeout to 1 second
            client.setSoTimeout(50);
    }
    
    public String recieve () throws IOException {
        try {
            return fromClient.readUTF();
        } catch (SocketTimeoutException e) {
            return null; // Return null on timeout instead of throwing
        }
    }
    
    public void send (String msg) throws IOException {
        
        if (msg.length() > 0 && msg.length() < 251) {
           
                toClient.writeUTF(msg);
        }
    }

    public boolean isConnected () {
        return !client.isClosed();
    }

}

