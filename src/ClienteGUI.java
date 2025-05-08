
import java.awt.BorderLayout;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.text.DefaultCaret;

public class ClienteGUI {
    private boolean estaEnLocal = true;
    JTextField txtComando;
    JTextArea txtArea;
    BufferedReader reader;
    PrintWriter writer;
    File carpetaCliente;
    String ipServidor = "127.0.0.1";
    Socket clienteSocket;
    Socket socketEnvioDatos;
    JTextField campoRuta;
    JTextArea listaDeComandos;

    String comandosCarpetaLocal = "CARPETA LOCAL - MENU PRINCIPAL\n" +
            "CWD - Cambiar de carpeta local a servidor o viceversa\n" +
            "PWD - Directorio actual de la carpeta local\n" +
            "LS - Listar archivos en la carpeta local\n" +
            "MKDIR <nombre> - Crear directorio en la carpeta local\n" +
            "CD <ruta> - Cambiar directorio en el servidor\n" +
            "DELETE <nombre> - Eliminar archivo en la carpeta local\n" +
            "PUT <archivo> - Subir archivo desde la carpeta local al servidor\n" +
            "MPUT <archivos> - Subir múltiples archivos desde la carpeta local al servidor\n" +
            "QUIT - Salir de la aplicación";

    String comandosCarpetaServer = "SERVIDOR - MENU PRINCIPAL\n" +
            "CWD - Cambiar de carpeta servidor a local o viceversa\n" +
            "PWD - Directorio actual del servidor\n" +
            "LS - Listar archivos en el servidor\n" +
            "MKDIR <nombre> - Crear directorio en el servidor\n" +
            "CD <ruta> - Cambiar directorio en el servidor\n" +
            "DELETE <nombre> - Eliminar archivo en el servidor\n" +
            "GET <archivo> - Descargar archivo desde el servidor a la carpeta local\n" +
            "MGET <archivos> - Descargar múltiples archivos desde el servidor a la carpeta local\n" +
            "QUIT - Salir del servidor FTP";
    Path carpetaClienteAux = Paths.get("");
    // String carpetaCliente = carpetaClienteAux.toAbsolutePath().toString();

    public ClienteGUI(File carpetaCliente) {
        this.carpetaCliente = carpetaCliente;
        JFrame ventana = new JFrame("EscomDrive");
        ventana.setSize(800, 600);
        ventana.setLayout(new BorderLayout());
        txtArea = new JTextArea();
        txtArea.setEditable(false);
        txtArea.setLineWrap(true);
        txtArea.setWrapStyleWord(true);
        DefaultCaret caret = (DefaultCaret) txtArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        JScrollPane barraDesplazamiento = new JScrollPane(txtArea);
        ventana.add(barraDesplazamiento, BorderLayout.CENTER);
        JPanel panel = new JPanel(new BorderLayout());
        campoRuta = new JTextField();
        if (estaEnLocal == true) {
            campoRuta.setText(carpetaCliente.toString() + "  >");
        }
        campoRuta.setEditable(false);
        txtComando = new JTextField();
        JButton btnEnviar = new JButton("Enviar");
        panel.add(campoRuta, BorderLayout.WEST);
        panel.add(txtComando, BorderLayout.CENTER);
        panel.add(btnEnviar, BorderLayout.EAST);
        ventana.add(panel, BorderLayout.SOUTH);

        listaDeComandos = new JTextArea(comandosCarpetaLocal);
        listaDeComandos.setEditable(false);
        ventana.add(new JScrollPane(listaDeComandos), BorderLayout.EAST);

        btnEnviar.addActionListener(e -> {
            e.getSource();
            enviarComandoAServidor();
        });

        ventana.setVisible(true);
        // Conexion con el servidor
        int puertoFTP = 21;
        try {
            clienteSocket = new Socket(ipServidor, puertoFTP);
            reader = new BufferedReader(new InputStreamReader(clienteSocket.getInputStream(), "ISO-8859-1"));
            writer = new PrintWriter(new OutputStreamWriter(clienteSocket.getOutputStream(), "ISO-8859-1"));
            // Obtenemos la respuesta del servidor después de conectarnos
            txtArea.append(reader.readLine());
        } catch (IOException e) {
            txtArea.append("Error al conectar con el servidor\n");
        }

    }

    private void enviarComandoAServidor() {
        String comando = txtComando.getText();
        // Si el comando no es null y no está vacio
        if (comando != null && !comando.isEmpty()) {
            if (estaEnLocal) {
                    if (comando.toUpperCase().startsWith("PUT")) {
                        String rutaRelativa = comando.substring(4).trim();
                        File rutaArchivoODirectorio = new File(carpetaCliente, rutaRelativa);
                        if (!rutaArchivoODirectorio.exists()) {
                            txtArea.append("\n550 No se encontró el archivo o carpeta");
                        } else {
                            // Si se encontró el archivo o carpeta, vamos a enviarlo
                            writer.println(comando);
                            writer.flush();

                            try {
                                String respuesta = reader.readLine();
                                if (respuesta.equals("Listo")) {
                                    try {
                                        int puertoEnvioDatos = 5000;
                                        socketEnvioDatos = new Socket(ipServidor, puertoEnvioDatos);
                                        txtArea.append("\nConectado al socket de archivos");
                                        File archivoAEnviar;
                                        if (rutaArchivoODirectorio.isDirectory()) {
                                            // Comprimir carpeta
                                            archivoAEnviar = new File(".zip");
                                            comprimirCarpeta(rutaArchivoODirectorio, archivoAEnviar);
                                        } else {
                                            archivoAEnviar = rutaArchivoODirectorio;
                                        }
                                        String nombre = archivoAEnviar.getName();
                                        String path = archivoAEnviar.getAbsolutePath();
                                        long tam = archivoAEnviar.length();

                                        System.out.println(
                                                "Preparandose para enviar archivo: " + path + " de " + tam + " bytes\n");
                                        DataOutputStream dos = new DataOutputStream(socketEnvioDatos.getOutputStream());
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
                                            enviados += l;
                                            porcentaje = (int) ((enviados * 100) / tam);
                                            txtArea.append("\nEnviado el " + porcentaje + "% del archivo");
                                        }
                                        txtArea.append("\nArchivo enviado");
                                        dis.close();
                                        dos.close();
                                        socketEnvioDatos.close();

                                        // Si fue una carpeta, eliminar el .zip temporal
                                        if (rutaArchivoODirectorio.isDirectory()) {
                                            archivoAEnviar.delete();
                                        }

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                if (comando.toUpperCase().startsWith("MPUT")) {
                    // Extraer rutas (omitiendo "MPUT " y separando por espacios)
                    String rutasArg = comando.substring(5).trim();
                    String[] rutas = rutasArg.split("\\s+");

                    // Enviar comando MPUT al servidor
                    writer.println(comando);
                    writer.flush();

                    // Espera la respuesta del servidor
                    try {
                        String respuesta = reader.readLine();
                        if (respuesta.equals("Listo")) {
                            // Enviar el número de archivos/carpeta a enviar
                            writer.println(rutas.length);
                            writer.flush();

                            for (String rutaRelativa : rutas) {
                                File rutaArchivoODirectorio = new File(carpetaCliente, rutaRelativa);
                                if (!rutaArchivoODirectorio.exists()) {
                                    txtArea.append("\n550 No se encontró " + rutaRelativa);
                                    continue; // O bien, se podría abortar todo el proceso
                                }
                                // Se prepara el archivo a enviar (comprimir si es directorio)
                                File archivoAEnviar;
                                if (rutaArchivoODirectorio.isDirectory()) {
                                    archivoAEnviar = new File(".zip");
                                    comprimirCarpeta(rutaArchivoODirectorio, archivoAEnviar);
                                } else {
                                    archivoAEnviar = rutaArchivoODirectorio;
                                }

                                // Abrir socket para el envío de datos para este archivo
                                try {
                                    int puertoEnvioDatos = 5000;
                                    Socket socketEnvioDatos = new Socket(ipServidor, puertoEnvioDatos);
                                    txtArea.append("\nConectado al socket de archivos para: " + rutaRelativa);

                                    DataOutputStream dos = new DataOutputStream(socketEnvioDatos.getOutputStream());
                                    DataInputStream disArchivo = new DataInputStream(
                                            new FileInputStream(archivoAEnviar.getAbsolutePath()));

                                    String nombreArchivo = archivoAEnviar.getName();
                                    long tam = archivoAEnviar.length();

                                    // Se envía el nombre y tamaño del archivo
                                    dos.writeUTF(nombreArchivo);
                                    dos.flush();
                                    dos.writeLong(tam);
                                    dos.flush();

                                  
                                    long enviados = 0;
                                    int l = 0, porcentaje = 0;
                                    while (enviados < tam) {
                                        byte[] b = new byte[3500];
                                        l = disArchivo.read(b);
                                        dos.write(b, 0, l);
                                        dos.flush();
                                        enviados += l;
                                        porcentaje = (int) ((enviados * 100) / tam);
                                        txtArea.append("\nEnviado " + porcentaje + "% de " + rutaRelativa);
                                    }
                                    txtArea.append("\nArchivo enviado: " + rutaRelativa);

                                    
                                    disArchivo.close();
                                    dos.close();
                                    socketEnvioDatos.close();

                                    if (rutaArchivoODirectorio.isDirectory()) {
                                        archivoAEnviar.delete();
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                else if (comando.toUpperCase().startsWith("MKDIR")) {
                    String nombreCarpeta = comando.substring(6).trim();
                    File nuevaCarpeta = new File(carpetaCliente, nombreCarpeta);
                    if (nuevaCarpeta.mkdir()) {
                        txtArea.append("\nCarpeta creada: " + nuevaCarpeta.getAbsolutePath() + "\n");
                    } else {
                        txtArea.append("Error al crear la carpeta.\n");
                    }
                } else if (comando.toUpperCase().startsWith("DELETE")) {
                    String nombre = comando.substring(7).trim();
                    File carpetaAEliminar = new File(carpetaCliente, nombre);
                    if (carpetaAEliminar.exists()) {
                        // Intentar eliminar; recuerda que delete() no elimina directorios que no estén
                        // vacíos
                        if (carpetaAEliminar.delete()) {
                            txtArea.append("\n200 " + carpetaAEliminar.getName() + " se eliminó correctamente");
                        } else {
                            txtArea.append("\n502: El directorio no está vacío y no se puede eliminar");
                        }
                    } else {
                        txtArea.append("\n" + carpetaAEliminar.getName() + " no existe");
                    }
                } else if (comando.toUpperCase().startsWith("PWD")) {
                    txtArea.append("\nDirectorio actual: " + carpetaCliente.getAbsolutePath() + ">");
                } else if (comando.toUpperCase().startsWith("CD")) {
                    String nuevaRutaRelativa = comando.substring(3).trim();

                    File nuevaRuta;

                    // Si es CD .. entonces ve al padre
                    if (nuevaRutaRelativa.equals("..")) {
                        nuevaRuta = carpetaCliente.getParentFile();
                    } else {
                        nuevaRuta = new File(carpetaCliente, nuevaRutaRelativa).getAbsoluteFile();
                    }

                    if (nuevaRuta != null && nuevaRuta.exists() && nuevaRuta.isDirectory()) {
                        carpetaCliente = nuevaRuta;
                        txtArea.append("\nRuta cambiada a: " + carpetaCliente.getAbsolutePath() + "\n");
                        campoRuta.setText(carpetaCliente + " >");
                    } else {
                        txtArea.append("\nNo se pudo cambiar a la ruta" + "\n");
                    }
                } else if (comando.toUpperCase().startsWith("QUIT")) {

                    writer.println(comando);
                    writer.flush();
                    try {
                        String msg1 = reader.readLine(); // "226: Cerrando la conexión de datos."
                        txtArea.append("\n" + msg1);
                        String msg2 = reader.readLine(); // "Listo"

                        if (msg2 != null && msg2.equals("Listo")) {
                            // Ahora sí, cierro streams y sockets del lado del cliente
                            writer.close();
                            reader.close();
                            clienteSocket.close();
                            txtArea.append("\nSe ha cerrado la conexión exitosamente");
                            JOptionPane.showMessageDialog(null,
                                    "Se ha cerrado la conexión exitosamente, saliendo de la aplicación");
                            System.exit(0);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (comando.equalsIgnoreCase("LS")) {
                    txtArea.append("\n---------- Listando archivos y carpetas (LOCAL) ----------\n");
                    listLocalFiles();
                } else if (comando.equalsIgnoreCase("CWD")) {
                    estaEnLocal = false;
                    try {
                        writer.println(comando);
                        writer.flush();
                        campoRuta.setText(reader.readLine() + " >");
                        listaDeComandos.setText(comandosCarpetaServer);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            } else { // En modo servidor
                if (comando.toUpperCase().startsWith("GET")) {
                    writer.println(comando);
                    writer.flush();

                    try {
                        String respuesta = reader.readLine();
                        // txtArea.append("La respuesta es |" + respuesta + "|");
                        if (respuesta.equals("Listo")) {
                            // Conecta al socket de datos en el puerto 5000
                            Socket socketRecibirDatos = new Socket(ipServidor, 5000);
                            DataInputStream dis = new DataInputStream(socketRecibirDatos.getInputStream());
                            // Primero, recibe los metadatos: nombre del archivo y tamaño
                            String nombreArchivo = dis.readUTF();
                            long tam = dis.readLong();

                            // Una vez recibidos los metadatos, envía la confirmación "ListoParaRecibir"
                            writer.println("ListoParaRecibir");
                            writer.flush();

                            File archivoDestino = new File(carpetaCliente + "/" + nombreArchivo);
                            DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoDestino));
                            long recibidos = 0;
                            int l = 0, porcentaje = 0;
                            byte[] b;
                            // txtArea.append("El tamaño es " + tam);
                            while (recibidos < tam) {
                                b = new byte[3500];
                                l = dis.read(b);
                                dos.write(b, 0, l);
                                dos.flush();
                                recibidos += l;
                                porcentaje = (int) ((recibidos * 100) / tam);
                                txtArea.append("\nRecibido el " + porcentaje + "% del archivo");
                            }
                            txtArea.append("\nTerminado");
                            dos.close();
                            dis.close();
                            socketRecibirDatos.close();
                            System.out.println("\nArchivo recibido...");
                            // Si se recibió un ZIP, se procede a descomprimirlo
                            if (nombreArchivo.endsWith(".zip")) {
                                File carpetaZipDestino = new File(
                                        carpetaCliente + "/" + nombreArchivo.replace(".zip", ""));
                                carpetaZipDestino.mkdirs();
                                descomprimirZip(archivoDestino, carpetaZipDestino);
                                archivoDestino.delete(); // Opcional: borrar el ZIP tras descomprimir
                            }
                        } else {
                            txtArea.append("No se encontró el archivo o la respuesta fue errónea");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (comando.toUpperCase().startsWith("MGET")) {
                    // Envía el comando MGET con las rutas separadas por espacios
                    writer.println(comando);
                    writer.flush();

                    try {
                        
                        String respuestaInicial = reader.readLine();
                        if (respuestaInicial.equals("Listo")) {
                        
                            int totalArchivos = Integer.parseInt(reader.readLine());
                            txtArea.append("\nSe recibirán " + totalArchivos + " archivo(s) o carpeta(s).");

                            for (int i = 0; i < totalArchivos; i++) {
                        
                                String status = reader.readLine();
                                if (status.equals("550")) {
                                    txtArea.append("\nArchivo/carpeta no encontrado en el servidor.");
                        
                                    continue;
                                }

                                String statusConexion = reader.readLine();
                                if (!statusConexion.equals("Listo")) {
                                    txtArea.append("\nError en la sincronización de la transferencia.");
                                    continue;
                                }


                                Socket socketRecibirDatos = new Socket(ipServidor, 5000);
                                DataInputStream dis = new DataInputStream(socketRecibirDatos.getInputStream());


                                String nombreArchivo = dis.readUTF();
                                long tam = dis.readLong();


                                writer.println("ListoParaRecibir");
                                writer.flush();

                                File archivoDestino = new File(carpetaCliente + "/" + nombreArchivo);
                                DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoDestino));

                                long recibidos = 0;
                                int l = 0, porcentaje = 0;
                                byte[] b;
                                while (recibidos < tam) {
                                    b = new byte[3500];
                                    l = dis.read(b);
                                    dos.write(b, 0, l);
                                    dos.flush();
                                    recibidos += l;
                                    porcentaje = (int) ((recibidos * 100) / tam);
                                    txtArea.append("\nRecibido " + porcentaje + "% del archivo: " + nombreArchivo);
                                }
                                txtArea.append("\nTransferencia completa: " + nombreArchivo);
                                dos.close();
                                dis.close();
                                socketRecibirDatos.close();

                                // Si se recibe un archivo ZIP, se asume que era una carpeta: se procede a
                                // descomprimirlo
                                if (nombreArchivo.endsWith(".zip")) {
                                    File carpetaZipDestino = new File(
                                            carpetaCliente + "/" + nombreArchivo.replace(".zip", ""));
                                    carpetaZipDestino.mkdirs();
                                    descomprimirZip(archivoDestino, carpetaZipDestino);
                                    archivoDestino.delete();
                                }
                            }
                        } else {
                            txtArea.append("Error: no se recibió respuesta adecuada del servidor.");
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (comando.startsWith("MKDIR")) {
                    writer.println(comando);
                    writer.flush();
                    try {
                        txtArea.append("\n" + reader.readLine());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (comando.startsWith("DELETE")) {
                    writer.println(comando);
                    writer.flush();
                    try {
                        txtArea.append("\n" + reader.readLine());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else if (comando.toUpperCase().startsWith("PWD")) {
                    writer.println(comando);
                    writer.flush();
                    try {
                        txtArea.append((reader.readLine()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (comando.toUpperCase().startsWith("CD")) {
                    writer.println(comando);
                    writer.flush();
                    try {
                        txtArea.append(reader.readLine());
                        campoRuta.setText(reader.readLine() + " >");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                } else if (comando.equalsIgnoreCase("LS")) {
                    writer.println(comando);
                    writer.flush();
                    boolean banderaLSServidor = true;
                    String cadenaAux;
                    while (banderaLSServidor) {
                        try {
                            // Leer la respuesta del servidor
                            cadenaAux = reader.readLine();
                            if (cadenaAux != null) {
                                // Actualizar el área de texto con el contenido recibido
                                txtArea.append("\n" + cadenaAux);

                                // Verificar si el servidor ha enviado "Finalizado"
                                if (cadenaAux.equals("Finalizado")) {
                                    banderaLSServidor = false; // Salir del ciclo
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                else if (comando.equalsIgnoreCase("CWD")) {
                    // Aquí va la lógica para "CWD", que se ejecutará si el comando es CWD
                    // Actualiza la variable 'estaEnLocal' y las interfaces correspondientes
                    estaEnLocal = true;
                    campoRuta.setText(carpetaCliente.getAbsolutePath() + " >");
                    listaDeComandos.setText(comandosCarpetaLocal);
                }
            }
        } else {
            txtArea.append("\nIngrese un comando por favor");
        }
    }

    public static void main(String[] args) {
        File carpetaCliente = seleccionarCarpeta();
        new ClienteGUI(carpetaCliente);
    }

    private static File seleccionarCarpeta() {
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

    // Método para comprimir carpeta
    public static void comprimirCarpeta(File carpeta, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
                ZipOutputStream zos = new ZipOutputStream(fos)) {
            comprimir(carpeta, carpeta.getName(), zos);
        }
    }

    private static void comprimir(File file, String nombre, ZipOutputStream zos) throws IOException {
        if (file.isDirectory()) {
            File[] archivos = file.listFiles();
            if (archivos != null) {
                for (File archivo : archivos) {
                    comprimir(archivo, nombre + "/" + archivo.getName(), zos);
                }
            }
        } else {
            try (FileInputStream fis = new FileInputStream(file)) {
                ZipEntry zipEntry = new ZipEntry(nombre);
                zos.putNextEntry(zipEntry);
                byte[] buffer = new byte[4096];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
        }
    }

    private void listLocalFiles() {
        List<String> resultados = listFiles(carpetaCliente, ""); // Usamos carpeta como directorio base
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

    public static void descomprimirZip(File archivoZip, File destino) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archivoZip))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File nuevoArchivo = new File(destino, entry.getName());
                if (entry.isDirectory()) {
                    nuevoArchivo.mkdirs();
                } else {
                    nuevoArchivo.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(nuevoArchivo)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
}
