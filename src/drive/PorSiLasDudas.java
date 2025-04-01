import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Arrays;

public class ClienteGUI {
    private JTextArea txtArea;
    private JTextField commandField;
    private PrintWriter writer;
    private BufferedReader reader;
    private Socket socket; // Almacena el socket
    private File carpeta; // Carpeta local seleccionada
    private boolean estaEnLocal = true;
    private JTextArea listaDeComandos;
    private JTextField rutaField;
    String comandosCarpetaLocal = "Comandos aceptados:\n" +
            "CWD - Cambiar de carpeta local a servidor o viceversa\n" +
            "PWD - Directorio actual de la carpeta local\n" +
            "LS - Listar archivos en la carpeta local\n" +
            "MKDIR <nombre> - Crear directorio en la carpeta local\n" +
            "DELETE <nombre> - Eliminar archivo en la carpeta local\n" +
            "PUT <archivo> - Subir archivo desde la carpeta local al servidor\n" +
            "MPUT <archivos> - Subir múltiples archivos desde la carpeta local al servidor\n" +
            "GET <archivo> - Descargar archivo del servidor a la carpeta local\n" +
            "MGET <archivos> - Descargar múltiples archivos del servidor a la carpeta local\n" +
            "QUIT - Salir de la aplicación";

    String comandosCarpetaServer = "Comandos aceptados:\n" +
            "CWD - Cambiar de carpeta servidor a local o viceversa\n" +
            "PWD - Directorio actual del servidor\n" +
            "LS - Listar archivos en el servidor\n" +
            "MKDIR <nombre> - Crear directorio en el servidor\n" +
            "CD <ruta> - Cambiar directorio en el servidor\n" +
            "DELETE <nombre> - Eliminar archivo en el servidor\n" +
            "PUT <archivo> - Subir archivo desde la carpeta local al servidor\n" +
            "MPUT <archivos> - Subir múltiples archivos desde la carpeta local al servidor\n" +
            "GET <archivo> - Descargar archivo desde el servidor a la carpeta local\n" +
            "MGET <archivos> - Descargar múltiples archivos desde el servidor a la carpeta local\n" +
            "QUIT - Salir del servidor FTP";

            public ClienteGUI(File carpetaSeleccionada) {
                this.carpeta = carpetaSeleccionada;
                JFrame frame = new JFrame("ESCOMDRIVE");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(800, 600);
                frame.setLayout(new BorderLayout());
        
                txtArea = new JTextArea();
                txtArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(txtArea);
                frame.add(scrollPane, BorderLayout.CENTER);
        
                JPanel panel = new JPanel(new BorderLayout());
                rutaField = new JTextField(estaEnLocal ? carpeta.getAbsolutePath() : "drive");
                rutaField.setEditable(false);
                commandField = new JTextField();
                JButton btnEnviar = new JButton("Enviar");
                panel.add(rutaField, BorderLayout.WEST);
                panel.add(commandField, BorderLayout.CENTER);
                panel.add(btnEnviar, BorderLayout.EAST);
                frame.add(panel, BorderLayout.SOUTH);
        
                listaDeComandos = new JTextArea(comandosCarpetaLocal);
                listaDeComandos.setEditable(false);
                frame.add(new JScrollPane(listaDeComandos), BorderLayout.EAST);
        
                btnEnviar.addActionListener(e -> sendCommand());
        
                frame.setVisible(true);
                connectToServer();
                           // Mostrar carpeta local seleccionada en la GUI
        // txtArea.append("Carpeta local seleccionada: " + carpeta.getAbsolutePath() +
        // "\n");
        // Se puede listar el contenido inicial de la carpeta local
        // printLocalDirectory();
            }
    /*
     * private void printLocalDirectory() {
     * File[] files = carpeta.listFiles();
     * txtArea.append("Contenido de la carpeta local:\n");
     * if (files != null) {
     * for (File file : files) {
     * txtArea.append(file.getName() + (file.isDirectory() ? " [DIR]" : "") + "\n");
     * }
     * } else {
     * txtArea.append("La carpeta está vacía o no se pudo acceder.\n");
     * }
     * }
     */

    private void connectToServer() {
        try {
            socket = new Socket("127.0.0.1", 21); // Conectar al servidor
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ISO-8859-1"));
            writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "ISO-8859-1"), true);
            txtArea.append(reader.readLine() + "\n");
        } catch (IOException e) {
            txtArea.append("Error al conectar con el servidor\n");
        }
    }

    private void sendFile(String command) {
        new Thread(() -> {
            String[] parts = command.split(" ");
            String commandType = parts[0];
            // Para PUT/MPUT, los archivos se buscarán dentro de la carpeta local
            for (int i = 1; i < parts.length; i++) {
                // Se asume que el nombre del archivo es relativo a la carpeta local
                File file = new File(carpeta, parts[i]);
                if (file.exists() && file.isFile()) {
                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                        writer.println(commandType + " " + file.getName());
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = bis.read(buffer)) != -1) {
                            writer.write(new String(buffer, 0, bytesRead));
                        }
                        writer.flush();
                        txtArea.append("Archivo " + file.getName() + " enviado correctamente.\n");
                    } catch (IOException e) {
                        txtArea.append("Error al enviar el archivo " + file.getName() + "\n");
                    }
                } else {
                    txtArea.append("El archivo " + file.getName() + " no existe en la carpeta local.\n");
                }
            }
        }).start();
    }

    private void getFile(String command) {
        new Thread(() -> {
            String[] parts = command.split(" ");
            String commandType = parts[0];
            for (int i = 1; i < parts.length; i++) {
                writer.println(commandType + " " + parts[i]);
                writer.flush();
                try (BufferedOutputStream bos = new BufferedOutputStream(
                        new FileOutputStream(new File(carpeta, parts[i])))) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    InputStream inputStream = socket.getInputStream();
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                    }
                    txtArea.append("Archivo " + parts[i] + " descargado correctamente.\n");
                } catch (IOException e) {
                    txtArea.append("Error al descargar el archivo " + parts[i] + "\n");
                }
            }
        }).start();
    }

    private void sendCommand() {
        String command = commandField.getText();
        System.out.println("El comando recibido es " + command);
        if (!command.isEmpty()) {
            if (command.startsWith("PUT")) {
                // Para PUT se puede permitir elegir el archivo dentro de la carpeta local
                // Por ejemplo, si se ingresa "PUT archivo.txt", se buscará "archivo.txt" dentro
                // de localFolder
                sendFile(command);
            } else if (command.startsWith("MPUT")) {
                sendFile(command);
            } else if (command.startsWith("GET") || command.startsWith("MGET")) {
                getFile(command);
            } else if (command.equals("CWD")) {
                estaEnLocal = !estaEnLocal;
                rutaField.setText(estaEnLocal ? carpeta.getAbsolutePath() : "drive");
                listaDeComandos.setText(estaEnLocal ? comandosCarpetaLocal : comandosCarpetaServer);
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
                            txtArea.append(line + "\n");
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

    // Método para seleccionar la carpeta local
    private static File seleccionarCarpetaLocal() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccione la carpeta local antes de iniciar");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int returnValue = chooser.showOpenDialog(null);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        } else {
            // Si el usuario cancela, se puede salir o establecer una carpeta por defecto
            JOptionPane.showMessageDialog(null, "No se seleccionó carpeta. Saliendo de la aplicación.");
            System.exit(0);
        }
        return null; // Nunca llegará aquí, pero es necesario para compilar
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            File carpetaSeleccionada = seleccionarCarpetaLocal();
            new ClienteGUI(carpetaSeleccionada);
        });
    }
}
