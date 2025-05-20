import java.awt.*;
import javax.swing.*;
public class ClientGraphics extends Client {
    
    private JTextArea chat;
    private JTextArea clientList;
    private JTextField input;
    
    public ClientGraphics(JTextArea chat, JTextArea clientList, JTextField input) {
        this.chat = chat;
        this.clientList = clientList;
        this.input = input;

        buildUIPanel();
    }

    private void buildUIPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setPreferredSize(new Dimension(800, 600));

        chat.setEditable(false);
        clientList.setEditable(false);
        input.setPreferredSize(new Dimension(800, 30));

        JScrollPane chatScroller = new JScrollPane(chat);
        JScrollPane listScroller = new JScrollPane(clientList);

        panel.add(chatScroller, BorderLayout.CENTER);
        panel.add(listScroller, BorderLayout.EAST);
        panel.add(input, BorderLayout.SOUTH);

        JFrame frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.add(panel);
        frame.setVisible(true);
    }
}
