import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class ClienteGUI {
    private JTextArea textArea;
    private JTextField commandField;
    private PrintWriter writer;
    private BufferedReader reader;
    private Socket socket; // Store the socket here

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
                "PUT <archivo> - Subir archivo\n" +
                "MPUT <archivos> - Subir múltiples archivos\n" +
                "GET <archivo> - Descargar archivo\n" +
                "MGET <archivos> - Descargar múltiples archivos\n" +
                "QUIT - Salir";
        JTextArea commandList = new JTextArea(commands);
        commandList.setEditable(false);
        frame.add(new JScrollPane(commandList), BorderLayout.EAST);

        sendButton.addActionListener(e -> sendCommand());
        commandField.addActionListener(e -> sendCommand());

        frame.setVisible(true);
        connectToServer();
        // Print the current directory
        printCurrentDirectory();
    }

    private void printCurrentDirectory() {
        String currentDirectory = System.getProperty("user.dir");
        textArea.append("Directorio actual del cliente: " + currentDirectory + "\n");
    }

    private void connectToServer() {
        try {
            socket = new Socket("127.0.0.1", 21); // Initialize the socket
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "ISO-8859-1"), true);
            textArea.append(reader.readLine() + "\n");
        } catch (IOException e) {
            textArea.append("Error al conectar con el servidor\n");
        }
    }



    private void sendFile(String command) {
        new Thread(() -> {
            String[] parts = command.split(" ");
            String commandType = parts[0];
            String[] fileNames = Arrays.copyOfRange(parts, 1, parts.length);

            for (String fileName : fileNames) {
                File file = new File(fileName);
                if (file.exists() && file.isFile()) {
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                        writer.println(commandType + " " + file.getName());
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            writer.write(new String(buffer, 0, bytesRead));
                        }
                        writer.flush();
                        textArea.append("Archivo " + fileName + " enviado correctamente.\n");
                    } catch (IOException e) {
                        textArea.append("Error al enviar el archivo " + fileName + "\n");
                    }
                } else {
                    textArea.append("El archivo " + fileName + " no existe.\n");
                }
            }
        }).start();
    }

    private void getFile(String command) {
        new Thread(() -> {
            String[] parts = command.split(" ");
            String commandType = parts[0];
            String[] fileNames = Arrays.copyOfRange(parts, 1, parts.length);

            for (String fileName : fileNames) {
                writer.println(commandType + " " + fileName);
                writer.flush();
                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fileName))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    InputStream inputStream = socket.getInputStream(); // Use the socket here
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    textArea.append("Archivo " + fileName + " descargado correctamente.\n");
                } catch (IOException e) {
                    textArea.append("Error al descargar el archivo " + fileName + "\n");
                }
            }
        }).start();
    }

    private void sendCommand() {
        String command = commandField.getText();
        if (!command.isEmpty()) {
            if (command.startsWith("PUT")) {
                JFileChooser fileChooser = new JFileChooser();
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();
                    sendFile("PUT " + selectedFile.getAbsolutePath());
                }
            } else if (command.startsWith("MPUT")) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setMultiSelectionEnabled(true);
                int returnValue = fileChooser.showOpenDialog(null);
                if (returnValue == JFileChooser.APPROVE_OPTION) {
                    File[] selectedFiles = fileChooser.getSelectedFiles();
                    StringBuilder commandBuilder = new StringBuilder("MPUT");
                    for (File file : selectedFiles) {
                        commandBuilder.append(" ").append(file.getAbsolutePath());
                    }
                    sendFile(commandBuilder.toString());
                }
            } else if (command.startsWith("GET") || command.startsWith("MGET")) {
                getFile(command);
            } else {
                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        writer.println(command);
                        writer.flush();
                        if (command.equalsIgnoreCase("LS")) {
                            String line;
                            while (!(line = reader.readLine()).equals("END_LIST")) {
                                publish(line);
                            }
                        } else {
                            publish(reader.readLine());
                        }
                        return null;
                    }

                    @Override
                    protected void process(java.util.List<String> chunks) {
                        for (String line : chunks) {
                            textArea.append(line + "\n");
                        }
                    }

                    @Override
                    protected void done() {
                        if (command.equalsIgnoreCase("QUIT")) {
                            System.exit(0);
                        }
                        commandField.setText("");
                    }
                }.execute();
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ClienteGUI::new);
    }
}