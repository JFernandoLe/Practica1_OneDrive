import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        rutaField = new JTextField(estaEnLocal ? carpeta.getAbsolutePath() + " >" : "drive");
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
        if (!command.isEmpty()) {
            if (estaEnLocal) {
                if (command.startsWith("PUT") || command.startsWith("MPUT")) {
                    sendFile(command);
                } else if (command.equalsIgnoreCase("LS")) {
                    txtArea.append("---------- Listando archivos y carpetas (LOCAL) ----------\n");
                    listLocalFiles();
                } else if (command.equalsIgnoreCase("PWD")) {
                    txtArea.append("Directorio actual: " + carpeta.getAbsolutePath() + "\n");
                } else if (command.startsWith("MKDIR")) {
                    String nombreCarpeta = command.substring(6).trim();
                    File nuevaCarpeta = new File(carpeta, nombreCarpeta);
                    if (nuevaCarpeta.mkdir()) {
                        txtArea.append("Carpeta creada: " + nuevaCarpeta.getAbsolutePath() + "\n");
                    } else {
                        txtArea.append("Error al crear la carpeta.\n");
                    }
                } else if (command.startsWith("DELETE")) {
                    String directorio = command.replaceAll("(?i)DELETE ", "");// unicamente el nombre
                    File archivo = new File(carpeta, directorio);
                    File miFichero = new File(carpeta.getAbsolutePath() + "\\" + directorio);
                    if(miFichero.exists()){
                        if(miFichero.delete()){
                            txtArea.append("\n200 " + miFichero.getName() + " se eliminó correctamente");
                        }else{
                            txtArea.append("502: El directorio no está vacío y no se puede eliminar");
                        }
                    }else{
                        txtArea.append(miFichero.getName() + " no existe");
                    }
                } else if (command.equalsIgnoreCase("CWD")) {
                    estaEnLocal = false;
                    rutaField.setText("drive >");
                    listaDeComandos.setText(comandosCarpetaServer);
                } else {
                    txtArea.append("Comando inválido en modo local.\n");
                }
            } else {
                if (command.startsWith("GET") || command.startsWith("MGET")) {
                    getFile(command);
                } else if (Arrays.asList("PWD", "LS", "MKDIR", "CD", "DELETE").stream().anyMatch(command::startsWith)) {
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
                } else if (command.equals("CWD")) {
                    estaEnLocal = true;
                    rutaField.setText(carpeta.getAbsolutePath() + " >");
                    listaDeComandos.setText(comandosCarpetaLocal);
                } else {
                    txtArea.append("Comando inválido en modo servidor.\n");
                }
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

    private void listLocalFiles() {
        List<String> resultados = listFiles(carpeta, ""); // Usamos carpeta como directorio base
        if (resultados != null && !resultados.isEmpty()) {
            for (String linea : resultados) {
                txtArea.append(linea + "\n");
            }
        } else {
            txtArea.append("Directorio vacío.\n");
        }
    }

    public static List<String> listFiles(File dir, String tabulacion) {
        List<String> resultados = new ArrayList<>();
        File[] files = dir.listFiles(); // Obtenemos archivos y directorios
        if (files != null) { // Verificamos que el directorio no esté vacío
            for (File file : files) {
                String resultado = tabulacion + file.getName(); // Solo mostramos el nombre del archivo o directorio
                if (file.isDirectory()) {
                    resultado += "\\"; // Si es un directorio, agregamos "\" al final para identificarlo
                }
                resultados.add(resultado); // Agregar a la lista
                if (file.isDirectory()) { // Si es un directorio, hacemos recursión
                    resultados.addAll(listFiles(file, tabulacion + "\t")); // Se agrega una tabulación extra
                }
            }
        }
        return resultados;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            File carpetaSeleccionada = seleccionarCarpetaLocal();
            new ClienteGUI(carpetaSeleccionada);
        });
    }
}
