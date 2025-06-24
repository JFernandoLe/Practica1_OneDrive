import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.zip.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.*;

public class ClienteNIO {
    private static final String SERVER = "localhost";
    private static final int PUERTO_CONTROL = 21;
    private static final int PUERTO_DATOS = 5000;
    private static final int BUFFER_SIZE = 3500;
    
    private Selector selector;
    private SocketChannel controlChannel;
    private SocketChannel dataChannel;
    private Path currentDir;
    private ByteBuffer buffer;
    private ClientState state;
    
    // Componentes GUI
    private JFrame ventana;
    private JTextArea txtArea;
    private JTextField txtComando;
    private JTextField campoRuta;
    private JTextArea listaDeComandos;
    private boolean estaEnLocal = true;
    
    private String comandosCarpetaLocal = "CARPETA LOCAL - MENU PRINCIPAL\n" +
        "CWD - Cambiar de carpeta local a servidor o viceversa\n" +
        "PWD - Directorio actual de la carpeta local\n" +
        "LS - Listar archivos en la carpeta local\n" +
        "MKDIR <nombre> - Crear directorio en la carpeta local\n" +
        "CD <ruta> - Cambiar directorio en el servidor\n" +
        "DELETE <nombre> - Eliminar archivo en la carpeta local\n" +
        "PUT <archivo> - Subir archivo desde la carpeta local al servidor\n" +
        "MPUT <archivos> - Subir múltiples archivos desde la carpeta local al servidor\n" +
        "QUIT - Salir de la aplicación";

    private String comandosCarpetaServer = "SERVIDOR - MENU PRINCIPAL\n" +
        "CWD - Cambiar de carpeta servidor a local o viceversa\n" +
        "PWD - Directorio actual del servidor\n" +
        "LS - Listar archivos en el servidor\n" +
        "MKDIR <nombre> - Crear directorio en el servidor\n" +
        "CD <ruta> - Cambiar directorio en el servidor\n" +
        "DELETE <nombre> - Eliminar archivo en el servidor\n" +
        "GET <archivo> - Descargar archivo desde el servidor a la carpeta local\n" +
        "MGET <archivos> - Descargar múltiples archivos desde el servidor a la carpeta local\n" +
        "QUIT - Salir del servidor FTP";

    public ClienteNIO(File carpetaInicial) {
        this.currentDir = carpetaInicial.toPath();
        this.buffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.state = new ClientState();
        
        // Configurar GUI
        configurarGUI();
        
        // Conectar al servidor
        conectarServidor();
    }

    private void configurarGUI() {
        ventana = new JFrame("EscomDrive NIO");
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
        campoRuta = new JTextField(currentDir.toString() + " >");
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
        
        ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrarConexion();
                System.exit(0);
            }
        });
        
        ventana.setVisible(true);
    }

    private void conectarServidor() {
        try {
            selector = Selector.open();
            
            // Canal de control
            controlChannel = SocketChannel.open();
            controlChannel.configureBlocking(false);
            controlChannel.connect(new InetSocketAddress(SERVER, PUERTO_CONTROL));
            controlChannel.register(selector, SelectionKey.OP_CONNECT);
            
            new Thread(this::runClient).start();
        } catch (IOException e) {
            mostrarError("Error al conectar con el servidor: " + e.getMessage());
        }
    }

    private void runClient() {
        try {
            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    
                    if (!key.isValid()) continue;
                    
                    if (key.isConnectable()) {
                        handleConnect(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }
            }
        } catch (IOException e) {
            mostrarError("Error en el cliente: " + e.getMessage());
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        if (channel == controlChannel) {
            if (channel.finishConnect()) {
                channel.register(selector, SelectionKey.OP_READ);
                mostrarMensaje("Conectado al servidor FTP");
            }
        } else if (channel == dataChannel) {
            if (channel.finishConnect()) {
                channel.register(selector, SelectionKey.OP_WRITE);
                iniciarTransferencia();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        
        if (channel == controlChannel) {
            buffer.clear();
            int bytesRead = channel.read(buffer);
            
            if (bytesRead == -1) {
                channel.close();
                mostrarMensaje("Desconectado del servidor");
                return;
            }
            
            buffer.flip();
            String respuesta = StandardCharsets.ISO_8859_1.decode(buffer).toString();
            mostrarMensaje(respuesta);
            
            // Procesar respuestas especiales del servidor
            if (respuesta.startsWith("150")) {
                // Preparar transferencia de datos
                conectarCanalDatos();
            }
        } else if (channel == dataChannel) {
            // Procesar datos recibidos
            recibirDatos();
        }
    }

    private void handleWrite(SelectionKey key) throws IOException {
        if (state.tieneDatosParaEnviar()) {
            SocketChannel channel = (SocketChannel) key.channel();
            ByteBuffer data = state.obtenerDatosParaEnviar();
            channel.write(data);
            
            if (!data.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
                
                if (state.transferenciaCompletada()) {
                    finalizarTransferencia();
                }
            }
        }
    }

    private void enviarComando() {
        String comando = txtComando.getText().trim();
        if (comando.isEmpty()) return;
        
        txtComando.setText("");
        
        if (estaEnLocal) {
            procesarComandoLocal(comando);
        } else {
            enviarComandoServidor(comando);
        }
    }

    private void procesarComandoLocal(String comando) {
        if (comando.equalsIgnoreCase("LS")) {
            listarArchivosLocales();
        } else if (comando.toUpperCase().startsWith("CD")) {
            cambiarDirectorioLocal(comando.substring(3).trim());
        } else if (comando.equalsIgnoreCase("PWD")) {
            mostrarMensaje("Directorio actual: " + currentDir);
        } else if (comando.equalsIgnoreCase("CWD")) {
            cambiarModoRemoto();
        } else {
            mostrarMensaje("Comando local no reconocido");
        }
    }

    private void enviarComandoServidor(String comando) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap((comando + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
            state.agregarDatos(buffer);
            controlChannel.register(selector, SelectionKey.OP_WRITE);
            
            if (comando.toUpperCase().startsWith("PUT") || comando.toUpperCase().startsWith("MPUT")) {
                prepararEnvioArchivos(comando);
            } else if (comando.toUpperCase().startsWith("GET") || comando.toUpperCase().startsWith("MGET")) {
                state.operacion = comando.toUpperCase().startsWith("GET") ? 
                    ClientState.Operacion.DESCARGAR_ARCHIVO : ClientState.Operacion.DESCARGAR_MULTIPLES;
            }
        } catch (IOException e) {
            mostrarError("Error al enviar comando: " + e.getMessage());
        }
    }

    private void prepararEnvioArchivos(String comando) {
        String[] partes = comando.split("\\s+", 2);
        String ruta = partes.length > 1 ? partes[1] : "";
        
        if (comando.toUpperCase().startsWith("PUT")) {
            state.operacion = ClientState.Operacion.SUBIR_ARCHIVO;
            state.archivosParaEnviar.add(currentDir.resolve(ruta).toFile());
        } else { // MPUT
            state.operacion = ClientState.Operacion.SUBIR_MULTIPLES;
            for (String nombre : ruta.split("\\s+")) {
                state.archivosParaEnviar.add(currentDir.resolve(nombre).toFile());
            }
        }
    }

    private void conectarCanalDatos() {
        try {
            dataChannel = SocketChannel.open();
            dataChannel.configureBlocking(false);
            dataChannel.connect(new InetSocketAddress(SERVER, PUERTO_DATOS));
            dataChannel.register(selector, SelectionKey.OP_CONNECT);
        } catch (IOException e) {
            mostrarError("Error al conectar canal de datos: " + e.getMessage());
        }
    }

    private void iniciarTransferencia() {
        try {
            switch (state.operacion) {
                case SUBIR_ARCHIVO:
                case SUBIR_MULTIPLES:
                    enviarArchivo();
                    break;
                case DESCARGAR_ARCHIVO:
                case DESCARGAR_MULTIPLES:
                    recibirArchivo();
                    break;
            }
        } catch (IOException e) {
            mostrarError("Error en transferencia: " + e.getMessage());
        }
    }

    private void enviarArchivo() throws IOException {
        if (state.archivosParaEnviar.isEmpty()) return;
        
        File archivo = state.archivosParaEnviar.get(0);
        String nombre = archivo.getName();
        long tam = archivo.length();
        
        ByteBuffer header = ByteBuffer.allocate(128);
        header.put(nombre.getBytes(StandardCharsets.UTF_8));
        header.putLong(tam);
        header.flip();
        
        state.agregarDatos(header);
        state.archivoActual = new FileInputStream(archivo).getChannel();
    }

        private void recibirArchivo() {
        try {
            // Configurar el archivo de destino
            String nombreArchivo = "archivo_recibido"; // Nombre temporal
            if (state.operacion == ClientState.Operacion.DESCARGAR_ARCHIVO) {
                nombreArchivo = state.archivosParaDescargar.get(0).getName();
            }
            
            state.archivoDestino = new File(currentDir.toFile(), nombreArchivo);
            state.archivoActual = new FileOutputStream(state.archivoDestino).getChannel();
            state.bytesTransferidos = 0;
            
            // Solicitar el tamaño del archivo al servidor (si es necesario)
            if (state.totalBytes == 0) {
                ByteBuffer buffer = ByteBuffer.wrap("SIZE\r\n".getBytes(StandardCharsets.ISO_8859_1));
                state.agregarDatos(buffer);
                controlChannel.register(selector, SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            mostrarError("Error al preparar recepción: " + e.getMessage());
        }
        }

        private void recibirDatos() {
        try {
            buffer.clear();
            int bytesRead = dataChannel.read(buffer);
            
            if (bytesRead == -1) {
                // Fin de la transferencia
                finalizarRecepcion();
                return;
            }
            
            buffer.flip();
            state.bytesTransferidos += bytesRead;
            
            // Escribir en el archivo
            while (buffer.hasRemaining()) {
                state.archivoActual.write(buffer);
            }
            
            // Mostrar progreso
            if (state.totalBytes > 0) {
                int porcentaje = (int) ((state.bytesTransferidos * 100) / state.totalBytes);
                mostrarMensaje("Recibido: " + porcentaje + "% (" + 
                            state.bytesTransferidos + "/" + state.totalBytes + " bytes)");
            }
            
            // Manejar archivos ZIP automáticamente
            if (state.bytesTransferidos == state.totalBytes && 
                state.archivoDestino.getName().endsWith(".zip")) {
                descomprimirArchivoRecibido(state.archivoDestino);
            }
        } catch (IOException e) {
            mostrarError("Error al recibir datos: " + e.getMessage());
        }
    }

    private void finalizarRecepcion() {
    try {
        if (state.archivoActual != null) {
            state.archivoActual.close();
        }
        
        mostrarMensaje("Archivo recibido: " + state.archivoDestino.getName());
        state.reset();
        
        // Si hay más archivos en MGET, continuar
        if (state.operacion == ClientState.Operacion.DESCARGAR_MULTIPLES && 
            !state.archivosParaDescargar.isEmpty()) {
            recibirArchivo();
        }
    } catch (IOException e) {
        mostrarError("Error al finalizar recepción: " + e.getMessage());
    }
}

private void descomprimirArchivoRecibido(File archivoZip) {
    try {
        File carpetaDestino = new File(archivoZip.getParent(), 
            archivoZip.getName().replace(".zip", ""));
        carpetaDestino.mkdirs();
        
        mostrarMensaje("Descomprimiendo " + archivoZip.getName() + "...");
        
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(archivoZip))) {
            ZipEntry entrada;
            byte[] buffer = new byte[BUFFER_SIZE];
            
            while ((entrada = zis.getNextEntry()) != null) {
                File archivoNuevo = new File(carpetaDestino, entrada.getName());
                
                if (entrada.isDirectory()) {
                    archivoNuevo.mkdirs();
                    continue;
                }
                
                // Crear directorios padres si no existen
                new File(archivoNuevo.getParent()).mkdirs();
                
                try (FileOutputStream fos = new FileOutputStream(archivoNuevo)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
        
        // Eliminar el ZIP después de descomprimir (opcional)
        archivoZip.delete();
        mostrarMensaje("Descompresión completada en: " + carpetaDestino.getPath());
    } catch (IOException e) {
        mostrarError("Error al descomprimir: " + e.getMessage());
    }
}

private void finalizarTransferencia() {
    try {
        if (dataChannel != null) {
            dataChannel.close();
            dataChannel = null;
        }
        state.reset();
        mostrarMensaje("Transferencia completada");
    } catch (IOException e) {
        mostrarError("Error al cerrar canal de datos: " + e.getMessage());
        }
    }

    private void listarArchivosLocales() {
        try {
            StringBuilder sb = new StringBuilder("Contenido de ").append(currentDir).append(":\n");
            Files.list(currentDir).forEach(path -> {
                sb.append(path.getFileName());
                if (Files.isDirectory(path)) sb.append("/");
                sb.append("\n");
            });
            mostrarMensaje(sb.toString());
        } catch (IOException e) {
            mostrarError("Error al listar archivos locales: " + e.getMessage());
        }
    }

    private void cambiarDirectorioLocal(String nuevaRuta) {
        Path nuevoDir;
        
        if (nuevaRuta.equals("..")) {
            nuevoDir = currentDir.getParent();
        } else {
            nuevoDir = currentDir.resolve(nuevaRuta);
        }
        
        if (nuevoDir != null && Files.isDirectory(nuevoDir)) {
            currentDir = nuevoDir;
            campoRuta.setText(currentDir + " >");
            mostrarMensaje("Directorio cambiado a: " + currentDir);
        } else {
            mostrarMensaje("No se pudo cambiar al directorio: " + nuevaRuta);
        }
    }

    private void cambiarModoRemoto() {
        estaEnLocal = false;
        listaDeComandos.setText(comandosCarpetaServer);
        enviarComandoServidor("PWD");
    }

    private void mostrarMensaje(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            txtArea.append(mensaje + "\n");
        });
    }

    private void mostrarError(String error) {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(ventana, error, "Error", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void cerrarConexion() {
        try {
            if (controlChannel != null) controlChannel.close();
            if (dataChannel != null) dataChannel.close();
            if (selector != null) selector.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class ClientState {
        enum Operacion { NINGUNA, SUBIR_ARCHIVO, SUBIR_MULTIPLES, DESCARGAR_ARCHIVO, DESCARGAR_MULTIPLES }
        
        public Operacion operacion = Operacion.NINGUNA;
        public java.util.List<File> archivosParaEnviar = new ArrayList<>();
        java.util.List<ByteBuffer> buffersParaEnviar = new ArrayList<>();
        FileChannel archivoActual;
        File archivoDestino;
        long bytesTransferidos = 0;
        long totalBytes = 0;
        List<File> archivosParaDescargar = new ArrayList<>();
    
        void agregarDatos(ByteBuffer buffer) {
            buffersParaEnviar.add(buffer);
        }

        ByteBuffer obtenerDatosParaEnviar() {
            return buffersParaEnviar.isEmpty() ? ByteBuffer.allocate(0) : buffersParaEnviar.get(0);
        }

        boolean tieneDatosParaEnviar() {
            return !buffersParaEnviar.isEmpty();
        }

        boolean transferenciaCompletada() {
            return buffersParaEnviar.isEmpty() && 
                  (operacion == Operacion.NINGUNA || bytesTransferidos >= totalBytes);
        }

        void reset() {
            operacion = Operacion.NINGUNA;
            archivosParaEnviar.clear();
            buffersParaEnviar.clear();
            archivoActual = null;
            archivoDestino = null;
            bytesTransferidos = 0;
            totalBytes = 0;
        }
        
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Seleccione la carpeta local antes de iniciar");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                new ClienteNIO(chooser.getSelectedFile());
            } else {
                JOptionPane.showMessageDialog(null, 
                    "No se seleccionó carpeta. Saliendo de la aplicación.");
                System.exit(0);
            }
        });
    }
}