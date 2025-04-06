import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class ClienteGUI {
    String rojo = "\u001B[31m";
    String verde = "\u001B[32m";
    String azul = "\u001B[34m";
    String reset = "\u001B[0m";
    Scanner scanner = new Scanner(System.in);
    private JTextArea txtArea;
    private JTextField txtComando;
    private PrintWriter out;
    private BufferedReader in;
    private Socket c1; // Almacena el socket
    private File carpeta; // Carpeta local seleccionada
    private boolean estaEnLocal = true;
    private JTextArea listaDeComandos;
    private JTextField rutaField;
    private final File raizLocal;
    String comandosCarpetaLocal = "Comandos aceptados:\n" +
            "CWD - Cambiar de carpeta local a servidor o viceversa\n" +
            "PWD - Directorio actual de la carpeta local\n" +
            "LS - Listar archivos en la carpeta local\n" +
            "MKDIR <nombre> - Crear directorio en la carpeta local\n" +
            "CD <ruta> - Cambiar directorio en el servidor\n" +
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
            "GET <archivo> - Descargar archivo desde el servidor a la carpeta local\n" +
            "MGET <archivos> - Descargar múltiples archivos desde el servidor a la carpeta local\n" +
            "QUIT - Salir del servidor FTP";

    public ClienteGUI(File carpetaSeleccionada) {
        this.carpeta = carpetaSeleccionada;
        this.raizLocal = carpetaSeleccionada;
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
        txtComando = new JTextField();
        JButton btnEnviar = new JButton("Enviar");
        panel.add(rutaField, BorderLayout.WEST);
        panel.add(txtComando, BorderLayout.CENTER);
        panel.add(btnEnviar, BorderLayout.EAST);
        frame.add(panel, BorderLayout.SOUTH);

        listaDeComandos = new JTextArea(comandosCarpetaLocal);
        listaDeComandos.setEditable(false);
        frame.add(new JScrollPane(listaDeComandos), BorderLayout.EAST);

        btnEnviar.addActionListener(e -> sendCommand());

        frame.setVisible(true);
        try {
            c1 = new Socket("127.0.0.1", 5000); // Hacemos la conexión al socket
            in = new BufferedReader(new InputStreamReader(c1.getInputStream(), "ISO-8859-1"));
            out = new PrintWriter(new OutputStreamWriter(c1.getOutputStream(), "ISO-8859-1"));
            txtArea.append(in.readLine() + "\n");
        } catch (IOException e) {
            txtArea.append("Error al conectar con el servidor\n");
        }
    }

    private void sendCommand() {
        String comando = txtComando.getText();
        if (!comando.isEmpty()) {
            // Si nos encontramos en la carpeta local
            if (estaEnLocal) {
                // Comando PUT
                if (comando.toUpperCase().startsWith("PUT")) {
                    String nombreArchivo = comando.replaceAll("(?i)PUT ", "").trim(); // Elimina "PUT" y espacios extra
                    File rutaArchivo = new File(carpeta + "\\" + nombreArchivo);
                    if (!rutaArchivo.isFile()) {
                        System.out.println(azul + "550 No se encontró el directorio" + reset);

                    } else {
                        out.println(comando); // Enviar comando al servidor
                        out.flush(); // Se envia de inmediato
                        try {
                            System.out.println(azul + in.readLine() + reset); // Obtenemos la respuesta del comando
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            int puerto2 = 5001;
                            Socket c2 = new Socket("127.0.0.1", puerto2);
                            System.out.println(azul + "Conexion con el socket de archivos");
                            String nombre = rutaArchivo.getName();
                            String path = rutaArchivo.getAbsolutePath();
                            long tam = rutaArchivo.length();
                            System.out.println("Preparandose para enviar archivo: " + path + " de " + tam + " bytes\n");
                            DataOutputStream dos = new DataOutputStream(c2.getOutputStream());
                            DataInputStream dis = new DataInputStream(new FileInputStream(path));
                            dos.writeUTF(nombre);
                            dos.flush();
                            dos.writeLong(tam);
                            dos.flush();
                            long enviados = 0;
                            int l = 0, porcentaje = 0;
                            while (enviados < tam) {
                                byte[] b = new byte[3500];
                                l = dis.read(b);
                                dos.write(b, 0, l);
                                dos.flush();
                                enviados = enviados + l;
                                porcentaje = (int) ((enviados * 100) / tam);
                                System.out.println("\rEnviado el " + porcentaje + "% del archivo");
                            }
                            System.out.println("\nArchivo enviado" + reset);
                            dis.close();
                            dos.close();
                            c2.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                } else if (comando.toUpperCase().startsWith("MPUT")) {
                    String texto = comando.replaceAll("(?i)MPUT ", "").trim(); // Elimina "MPUT" y espacios extra
                    String[] archivos = texto.split(","); // Divide los nombres de archivos por comas
                    for (String archivo : archivos) {
                        File f = new File(carpeta + "\\" + archivo.trim());
                        if (!f.isFile()) {
                            System.out.println(azul + "550 No se encontró el archivo: " + archivo + reset);
                        } else {
                            out.println("PUT " + archivo.trim()); // Enviar comando al servidor
                            out.flush();
                            try {
                                System.out.println(azul + in.readLine() + reset); // Obtenemos la respuesta del
                                                                                  // comando
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            try {
                                int pto = 20;
                                Socket c2 = new Socket("127.0.0.1", pto);
                                System.out.println(azul + "Conexión con el socket de archivos establecida");

                                String nombre = f.getName();
                                String path = f.getAbsolutePath();
                                long tam = f.length();
                                System.out
                                        .println("Preparándose para enviar archivo: " + path + " de " + tam + " bytes");

                                DataOutputStream dos = new DataOutputStream(c2.getOutputStream());
                                DataInputStream dis = new DataInputStream(new FileInputStream(path));

                                dos.writeUTF(nombre);
                                dos.flush();
                                dos.writeLong(tam);
                                dos.flush();

                                long enviados = 0;
                                int l = 0, porcentaje = 0;
                                byte[] buffer = new byte[3500];

                                while (enviados < tam) {
                                    l = dis.read(buffer);
                                    dos.write(buffer, 0, l);
                                    dos.flush();
                                    enviados += l;
                                    porcentaje = (int) ((enviados * 100) / tam);
                                }
                                System.out.println("Archivo " + nombre + " enviado con éxito\n" + reset);
                                dis.close();
                                dos.close();
                                c2.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else if (comando.equalsIgnoreCase("LS")) {
                    txtArea.append("---------- Listando archivos y carpetas (LOCAL) ----------\n");
                    listLocalFiles();
                } else if (comando.equalsIgnoreCase("PWD")) {
                    txtArea.append("Directorio actual: " + carpeta.getAbsolutePath() + "\n");
                } else if (comando.startsWith("MKDIR")) {
                    String nombreCarpeta = comando.substring(6).trim();
                    File nuevaCarpeta = new File(carpeta, nombreCarpeta);
                    if (nuevaCarpeta.mkdir()) {
                        txtArea.append("Carpeta creada: " + nuevaCarpeta.getAbsolutePath() + "\n");
                    } else {
                        txtArea.append("Error al crear la carpeta.\n");
                    }
                } else if (comando.startsWith("DELETE")) {
                    String directorio = comando.replaceAll("(?i)DELETE ", "");// unicamente el nombre
                    File archivo = new File(carpeta, directorio);
                    File miFichero = new File(carpeta.getAbsolutePath() + "\\" + directorio);
                    if (miFichero.exists()) {
                        if (miFichero.delete()) {
                            txtArea.append("\n200 " + miFichero.getName() + " se eliminó correctamente");
                        } else {
                            txtArea.append("502: El directorio no está vacío y no se puede eliminar");
                        }
                    } else {
                        txtArea.append(miFichero.getName() + " no existe");
                    }
                } else if (comando.equalsIgnoreCase("CWD")) {
                    estaEnLocal = false;
                    rutaField.setText("drive >");
                    listaDeComandos.setText(comandosCarpetaServer);
                } else if (comando.compareToIgnoreCase("QUIT") == 0) {
                    // Cerramos la conexion
                    System.out.println("Cerrando sesion...");
                    out.close();
                    System.exit(0);
                } else if (comando.toUpperCase().startsWith("CD")) {
                    String nuevaRuta = comando.substring(3).trim();
                    File nuevaCarpeta = new File(carpeta, nuevaRuta);

                    try {
                        File canonicalNueva = nuevaCarpeta.getCanonicalFile();
                        File canonicalRaiz = raizLocal.getCanonicalFile();

                        if (canonicalNueva.exists() && canonicalNueva.isDirectory()
                                && canonicalNueva.getPath().startsWith(canonicalRaiz.getPath())) {
                            carpeta = canonicalNueva;
                            rutaField.setText(carpeta.getAbsolutePath() + " >");
                            txtArea.append("Directorio cambiado a: " + carpeta.getAbsolutePath() + "\n");
                        } else {
                            txtArea.append("No se puede acceder a esa carpeta. Está fuera del directorio raíz.\n");
                        }
                    } catch (IOException e) {
                        txtArea.append("Error al cambiar de directorio.\n");
                        e.printStackTrace();
                    }
                } else {
                    txtArea.append("Comando inválido en modo local.\n");
                }
                // Si estamos en la carpeta del server
            } else if (Arrays.asList("CWD", "PWD", "LS", "MKDIR", "CD", "DELETE", "GET", "MGET", "QUIT")
                    .stream().anyMatch(c -> comando.toUpperCase().startsWith(c))) {

                new SwingWorker<Void, String>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        // Enviar el comando al servidor
                        out.println(comando);
                        out.flush();

                        if (comando.toUpperCase().startsWith("GET")) {
                            String nombreArchivo = comando.replaceAll("(?i)GET ", "").trim();

                            // Esperar la respuesta inicial del servidor
                            String respuestaInicial = in.readLine();
                            publish(respuestaInicial);
                            if (!respuestaInicial.startsWith("150")) {
                                publish("Error: El servidor no está listo para enviar el archivo");
                                return null;
                            }

                            // Configurar conexión de datos
                            try (Socket dataSocket = new Socket("127.0.0.1", 5001);
                                    DataInputStream dis = new DataInputStream(dataSocket.getInputStream());
                                    FileOutputStream fos = new FileOutputStream(new File(carpeta, nombreArchivo))) {

                                // Recibir metadatos
                                String nombreRecibido = dis.readUTF();
                                long tamano = dis.readLong();
                                publish("Recibiendo: " + nombreRecibido + " (" + tamano + " bytes)");

                                // Recibir datos
                                byte[] buffer = new byte[4096];
                                long recibidos = 0;
                                int bytesLeidos;
                                while (recibidos < tamano && (bytesLeidos = dis.read(buffer)) != -1) {
                                    fos.write(buffer, 0, bytesLeidos);
                                    recibidos += bytesLeidos;
                                    int porcentaje = (int) ((recibidos * 100) / tamano);
                                    publish("Recibido: " + porcentaje + "%");
                                }

                                // Leer la confirmación final del servidor
                                String finalResponse = in.readLine();
                                publish(finalResponse.startsWith("226") ? "Descarga completada"
                                        : "Error: " + finalResponse);
                            } catch (Exception e) {
                                publish("Error en transferencia: " + e.getMessage());
                                e.printStackTrace();
                            }
                        } else if (comando.toUpperCase().startsWith("MGET")) {
                            // Obtener los nombres de archivos; se asume que están separados por espacios
                            String archivosStr = comando.replaceAll("(?i)MGET ", "").trim();
                            String[] listaArchivos = archivosStr.split("\\s+");

                            // Procesar cada archivo en secuencia
                            for (String nombreArchivo : listaArchivos) {
                                publish("Iniciando descarga de: " + nombreArchivo);

                                // Esperar la respuesta inicial del servidor para el archivo actual
                                String respuestaInicial = in.readLine();
                                publish(respuestaInicial);
                                if (!respuestaInicial.startsWith("150")) {
                                    publish("Error: El servidor no está listo para enviar el archivo " + nombreArchivo);
                                    continue; // Saltamos este archivo y pasamos al siguiente
                                }

                                // Conexión de datos para el archivo actual
                                try (Socket dataSocket = new Socket("127.0.0.1", 5001);
                                        DataInputStream dis = new DataInputStream(dataSocket.getInputStream());
                                        FileOutputStream fos = new FileOutputStream(new File(carpeta, nombreArchivo))) {

                                    // Recibir metadatos
                                    String nombreRecibido = dis.readUTF();
                                    long tamano = dis.readLong();
                                    publish("Recibiendo: " + nombreRecibido + " (" + tamano + " bytes)");

                                    // Recibir datos
                                    byte[] buffer = new byte[4096];
                                    long recibidos = 0;
                                    int bytesLeidos;
                                    while (recibidos < tamano && (bytesLeidos = dis.read(buffer)) != -1) {
                                        fos.write(buffer, 0, bytesLeidos);
                                        recibidos += bytesLeidos;
                                        int porcentaje = (int) ((recibidos * 100) / tamano);
                                        publish("Recibido " + nombreRecibido + ": " + porcentaje + "%");
                                    }

                                    // Leer la confirmación final del servidor para este archivo
                                    String finalResponse = in.readLine();
                                    publish(finalResponse.startsWith("226")
                                            ? "Descarga de " + nombreRecibido + " completada"
                                            : "Error: " + finalResponse);
                                } catch (Exception e) {
                                    publish("Error en transferencia del archivo " + nombreArchivo + ": "
                                            + e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        } else if (comando.equalsIgnoreCase("CWD")) {
                            // Aquí va la lógica para "CWD", que se ejecutará si el comando es CWD
                            // Actualiza la variable 'estaEnLocal' y las interfaces correspondientes
                            estaEnLocal = true;
                            rutaField.setText(carpeta.getAbsolutePath() + " >");
                            listaDeComandos.setText(comandosCarpetaLocal);
                        } else {
                            // Procesar otros comandos, leyendo las respuestas hasta "END_LIST" si es
                            // necesario
                            String line;
                            while ((line = in.readLine()) != null) {
                                if (line.equals("END_LIST"))
                                    break;
                                publish(line);
                            }
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
                        txtComando.setText("");
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
