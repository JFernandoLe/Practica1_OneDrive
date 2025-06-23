import java.awt.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.util.List;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ClienteGUI {
    JTextField txtComando;
    JTextArea txtArea;
    SocketChannel socketChannel;
    BufferedReader reader;
    PrintWriter writer;
    File carpetaCliente;
    boolean estaEnLocal = true;
    String ipServidor = "127.0.0.1";
    JTextField campoRuta;
    JTextArea listaDeComandos;

    String comandosCarpetaLocal = "CARPETA LOCAL:\nPUT, MPUT, DELETE, LS, MKDIR, CD, PWD, CWD, QUIT";
    String comandosCarpetaServer = "SERVIDOR:\nGET, MGET, DELETE, LS, MKDIR, CD, PWD, CWD, QUIT";

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

        JScrollPane scroll = new JScrollPane(txtArea);
        ventana.add(scroll, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        campoRuta = new JTextField(carpetaCliente.getAbsolutePath() + " >");
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

        btnEnviar.addActionListener(e -> enviarComando());

        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.setVisible(true);

        try {
            socketChannel = SocketChannel.open();
            socketChannel.connect(new InetSocketAddress(ipServidor, 21));
            socketChannel.configureBlocking(true);

            InputStream in = socketChannel.socket().getInputStream();
            OutputStream out = socketChannel.socket().getOutputStream();

            reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1"));
            writer = new PrintWriter(new OutputStreamWriter(out, "ISO-8859-1"), true);

            txtArea.append(reader.readLine() + "\n");

        } catch (IOException ex) {
            txtArea.append("Error al conectar con servidor\n");
            ex.printStackTrace();
        }
    }

    private void enviarComando() {
        String comando = txtComando.getText();
        if (comando == null || comando.isEmpty()) {
            txtArea.append("\nIngrese un comando.");
            return;
        }

        if (estaEnLocal) {
            if (comando.startsWith("PUT")) {
                enviarPUT(comando);
            } else if (comando.startsWith("DELETE")) {
                borrarArchivoLocal(comando);
            } else if (comando.startsWith("LS")) {
                listarLocal();
            } else if (comando.startsWith("PWD")) {
                txtArea.append("\n" + carpetaCliente.getAbsolutePath());
            } else if (comando.startsWith("MKDIR")) {
                crearCarpetaLocal(comando);
            } else if (comando.startsWith("CD")) {
                cambiarDirectorioLocal(comando);
            } else if (comando.startsWith("CWD")) {
                estaEnLocal = false;
                writer.println(comando);
                try {
                    campoRuta.setText(reader.readLine() + " >");
                } catch (IOException ex) {
                    txtArea.append("\nError al leer la ruta del servidor.");
                    ex.printStackTrace();
                }
                listaDeComandos.setText(comandosCarpetaServer);
            } else if (comando.startsWith("QUIT")) {
                writer.println(comando);
                cerrarConexion();
            }
        } else {
            writer.println(comando);

            if (comando.startsWith("GET")) {
                recibirArchivoGET();
            } else if (comando.startsWith("LS")) {
                leerListado();
            } else if (comando.startsWith("PWD")) {
                leerRespuesta();
            } else if (comando.startsWith("MKDIR")) {
                leerRespuesta();
            } else if (comando.startsWith("DELETE")) {
                leerRespuesta();
            } else if (comando.startsWith("CWD")) {
                estaEnLocal = true;
                campoRuta.setText(carpetaCliente.getAbsolutePath() + " >");
                listaDeComandos.setText(comandosCarpetaLocal);
            } else if (comando.startsWith("QUIT")) {
                cerrarConexion();
            }
        }
    }

    private void enviarPUT(String comando) {
        try {
            String rutaRelativa = comando.substring(4).trim();
            File archivo = new File(carpetaCliente, rutaRelativa);
            if (!archivo.exists()) {
                txtArea.append("\nArchivo no encontrado.");
                return;
            }

            writer.println(comando);
            if (!reader.readLine().equals("Listo")) return;

            SocketChannel socketDatos = SocketChannel.open();
            socketDatos.connect(new InetSocketAddress(ipServidor, 5000));

            DataOutputStream dos = new DataOutputStream(socketDatos.socket().getOutputStream());
            DataInputStream dis = new DataInputStream(new FileInputStream(archivo));

            dos.writeUTF(archivo.getName());
            dos.writeLong(archivo.length());

            byte[] buffer = new byte[4096];
            long enviados = 0;
            int l;
            while ((l = dis.read(buffer)) != -1) {
                dos.write(buffer, 0, l);
                enviados += l;
            }

            txtArea.append("\nArchivo enviado: " + archivo.getName());

            dis.close();
            dos.close();
            socketDatos.close();

        } catch (Exception e) {
            txtArea.append("\nError en PUT.");
            e.printStackTrace();
        }
    }

    private void recibirArchivoGET() {
        try {
            String respuesta = reader.readLine();
            if (!respuesta.equals("Listo")) {
                txtArea.append("\nArchivo no encontrado.");
                return;
            }

            SocketChannel socketDatos = SocketChannel.open();
            socketDatos.connect(new InetSocketAddress(ipServidor, 5000));

            DataInputStream dis = new DataInputStream(socketDatos.socket().getInputStream());
            String nombreArchivo = dis.readUTF();
            long tam = dis.readLong();

            writer.println("ListoParaRecibir");

            File archivoDestino = new File(carpetaCliente, nombreArchivo);
            DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoDestino));

            byte[] buffer = new byte[4096];
            long recibidos = 0;
            int l;
            while (recibidos < tam && (l = dis.read(buffer)) != -1) {
                dos.write(buffer, 0, l);
                recibidos += l;
            }

            txtArea.append("\nArchivo recibido: " + nombreArchivo);

            dis.close();
            dos.close();
            socketDatos.close();

        } catch (Exception e) {
            txtArea.append("\nError en GET.");
            e.printStackTrace();
        }
    }

    private void cerrarConexion() {
        try {
            String msg1 = reader.readLine();
            txtArea.append("\n" + msg1);
            String msg2 = reader.readLine();

            reader.close();
            writer.close();
            socketChannel.close();

            txtArea.append("\nConexión cerrada.");
            JOptionPane.showMessageDialog(null, "Conexión cerrada. Salida.");
            System.exit(0);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void leerRespuesta() {
        try {
            txtArea.append("\n" + reader.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void leerListado() {
        try {
            boolean fin = false;
            while (!fin) {
                String linea = reader.readLine();
                if (linea.equals("Finalizado")) fin = true;
                else txtArea.append("\n" + linea);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void borrarArchivoLocal(String comando) {
        String nombre = comando.substring(7).trim();
        File archivo = new File(carpetaCliente, nombre);
        if (archivo.exists() && archivo.delete()) {
            txtArea.append("\nArchivo eliminado.");
        } else {
            txtArea.append("\nNo se pudo eliminar.");
        }
    }

    private void crearCarpetaLocal(String comando) {
        String nombre = comando.substring(6).trim();
        File dir = new File(carpetaCliente, nombre);
        if (dir.mkdir()) {
            txtArea.append("\nCarpeta creada.");
        } else {
            txtArea.append("\nNo se pudo crear la carpeta.");
        }
    }

    private void cambiarDirectorioLocal(String comando) {
        String nuevaRuta = comando.substring(3).trim();
        File nueva;

        if (nuevaRuta.equals("..")) {
            nueva = carpetaCliente.getParentFile();
        } else {
            nueva = new File(carpetaCliente, nuevaRuta);
        }

        if (nueva != null && nueva.exists() && nueva.isDirectory()) {
            carpetaCliente = nueva;
            campoRuta.setText(carpetaCliente.getAbsolutePath() + " >");
            txtArea.append("\nRuta cambiada.");
        } else {
            txtArea.append("\nNo se pudo cambiar de ruta.");
        }
    }

    private void listarLocal() {
        List<String> resultados = listFiles(carpetaCliente, "");
        if (resultados != null && !resultados.isEmpty()) {
            for (String linea : resultados) {
                txtArea.append("\n" + linea);
            }
        } else {
            txtArea.append("\nDirectorio vacío.");
        }
    }

    public static List<String> listFiles(File dir, String tab) {
        List<String> resultados = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String r = tab + file.getName();
                if (file.isDirectory()) r += "\\";
                resultados.add(r);
                if (file.isDirectory()) {
                    resultados.addAll(listFiles(file, tab + "\t"));
                }
            }
        }
        return resultados;
    }

    public static File seleccionarCarpeta() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Seleccione carpeta local");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = chooser.showOpenDialog(null);
        if (result == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        } else {
            JOptionPane.showMessageDialog(null, "No se seleccionó carpeta.");
            System.exit(0);
            return null;
        }
    }

    public static void main(String[] args) {
        File carpetaCliente = seleccionarCarpeta();
        new ClienteGUI(carpetaCliente);
    }
}