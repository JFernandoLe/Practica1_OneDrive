import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.Socket;

public class ClienteGUI {
    private JTextArea textArea;
    private JTextField commandField;
    private PrintWriter writer;
    private BufferedReader reader;

    public ClienteGUI() {
        JFrame frame = new JFrame("Cliente FTP");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        textArea = new JTextArea();
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        commandField = new JTextField();
        JButton sendButton = new JButton("Enviar");
        panel.add(commandField, BorderLayout.CENTER);
        panel.add(sendButton, BorderLayout.EAST);
        frame.add(panel, BorderLayout.SOUTH);

        String commands = "Comandos aceptados:\n" +
                "PWD - Directorio actual\n" +
                "LS - Listar archivos\n" +
                "MKDIR <nombre> - Crear directorio\n" +
                "CD <ruta> - Cambiar directorio\n" +
                "DELETE <nombre> - Eliminar archivo\n" +
                "QUIT - Salir";
        JTextArea commandList = new JTextArea(commands);
        commandList.setEditable(false);
        frame.add(new JScrollPane(commandList), BorderLayout.EAST);

        sendButton.addActionListener(e -> sendCommand());
        commandField.addActionListener(e -> sendCommand());

        frame.setVisible(true);
        connectToServer();
    }

    private void connectToServer() {
        try {
            Socket socket = new Socket("127.0.0.1", 21);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "ISO-8859-1"), true);
            textArea.append(reader.readLine() + "\n");
        } catch (IOException e) {
            textArea.append("Error al conectar con el servidor\n");
        }
    }

    private void sendCommand() {
        String command = commandField.getText();
        if (!command.isEmpty()) {
            writer.println(command);
            try {
                if (command.equalsIgnoreCase("LS")) {
                    String line;
                    while (!(line = reader.readLine()).equals("END_LIST")) {
                        textArea.append(line + "\n");
                    }
                } else {
                    textArea.append(reader.readLine() + "\n");
                }
                if (command.equalsIgnoreCase("QUIT")) {
                    System.exit(0);
                }
            } catch (IOException e) {
                textArea.append("Error en la comunicación con el servidor\n");
            }
            commandField.setText("");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClienteGUI::new);
    }
}
