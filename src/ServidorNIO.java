import java.nio.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class ServidorNIO {
    private static final int PUERTO_CONTROL = 21;
    private static final int PUERTO_DATOS = 5000;
    private static final int BUFFER_SIZE = 3500;
    private static final String CARPETA_BASE = "drive";
    
    private Selector selector;
    private ServerSocketChannel serverChannel;
    private ServerSocketChannel dataServerChannel;
    private Path currentDir;
    private Map<SocketChannel, ClientSession> clientSessions;

    public ServidorNIO() throws IOException {
        this.currentDir = Paths.get(CARPETA_BASE).toAbsolutePath();
        this.clientSessions = new HashMap<>();
        initializeServer();
        initializeDataServer();
    }

    private void initializeServer() throws IOException {
        selector = Selector.open();
        serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.socket().bind(new InetSocketAddress(PUERTO_CONTROL));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        
        // Crear carpeta base si no existe
        if (!Files.exists(currentDir)) {
            Files.createDirectories(currentDir);
        }
        
        System.out.println("Servidor NIO iniciado en puerto " + PUERTO_CONTROL);
        System.out.println("Carpeta base: " + currentDir);
    }

    private void initializeDataServer() throws IOException {
        dataServerChannel = ServerSocketChannel.open();
        dataServerChannel.configureBlocking(false);
        dataServerChannel.socket().bind(new InetSocketAddress(PUERTO_DATOS));
        dataServerChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void start() throws IOException {
        while (true) {
            selector.select();
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();
                
                if (!key.isValid()) {
                    continue;
                }
                
                if (key.isAcceptable()) {
                    acceptConnection(key);
                } else if (key.isReadable()) {
                    readData(key);
                } else if (key.isWritable()) {
                    writeData(key);
                }
            }
        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        
        if (serverChannel == this.serverChannel) {
            // Conexión de control
            channel.register(selector, SelectionKey.OP_READ);
            ClientSession session = new ClientSession();
            clientSessions.put(channel, session);
            sendResponse(channel, "220 Bienvenido a EscomDrive NIO\r\n");
        } else if (serverChannel == this.dataServerChannel) {
            // Conexión de datos
            for (ClientSession session : clientSessions.values()) {
                if (session.dataChannel == null) {
                    session.dataChannel = channel;
                    channel.register(selector, SelectionKey.OP_READ);
                    break;
                }
            }
        }
    }

    private void readData(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSession session = clientSessions.get(channel);
        
        if (session == null) {
            // Es un canal de datos
            handleDataChannelRead(channel);
            return;
        }
        
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        int bytesRead = channel.read(buffer);
        
        if (bytesRead == -1) {
            disconnectClient(channel);
            return;
        }
        
        buffer.flip();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        session.input.append(new String(bytes, StandardCharsets.ISO_8859_1));
        
        if (session.input.toString().contains("\r\n")) {
            processCommand(channel, session);
        }
    }

    private void processCommand(SocketChannel channel, ClientSession session) throws IOException {
        String command = session.input.toString().trim();
        session.input.setLength(0);
        
        System.out.println("Comando recibido: " + command);
        
        if (command.toUpperCase().startsWith("PUT")) {
            handlePutCommand(channel, session, command);
        } else if (command.toUpperCase().startsWith("MPUT")) {
            handleMputCommand(channel, session, command);
        } else if (command.toUpperCase().startsWith("GET")) {
            handleGetCommand(channel, session, command);
        } else if (command.toUpperCase().startsWith("MGET")) {
            handleMgetCommand(channel, session, command);
        } else if (command.toUpperCase().startsWith("MKDIR")) {
            handleMkdirCommand(channel, command);
        } else if (command.toUpperCase().startsWith("DELETE")) {
            handleDeleteCommand(channel, command);
        } else if (command.toUpperCase().startsWith("PWD")) {
            sendResponse(channel, "257 \"" + currentDir.toString() + "\"\r\n");
        } else if (command.toUpperCase().startsWith("CWD")) {
            handleCwdCommand(channel, command);
        } else if (command.toUpperCase().startsWith("CD")) {
            handleCdCommand(channel, command);
        } else if (command.toUpperCase().startsWith("QUIT")) {
            sendResponse(channel, "221 Adios\r\n");
            disconnectClient(channel);
        } else if (command.toUpperCase().startsWith("LS")) {
            handleLsCommand(channel);
        } else {
            sendResponse(channel, "500 Comando no reconocido\r\n");
        }
    }

    private void handlePutCommand(SocketChannel channel, ClientSession session, String command) throws IOException {
        String filename = command.substring(4).trim();
        session.currentFile = currentDir.resolve(filename).toFile();
        session.fileOperation = FileOperation.UPLOAD;
        sendResponse(channel, "150 Esperando archivo " + filename + "\r\n");
    }

    private void handleMputCommand(SocketChannel channel, ClientSession session, String command) throws IOException {
        sendResponse(channel, "150 Esperando múltiples archivos\r\n");
        session.fileOperation = FileOperation.MULTI_UPLOAD;
    }

    private void handleGetCommand(SocketChannel channel, ClientSession session, String command) throws IOException {
        String path = command.substring(4).trim();
        File file = currentDir.resolve(path).toFile();
        
        if (!file.exists()) {
            sendResponse(channel, "550 Archivo no encontrado\r\n");
            return;
        }
        
        session.currentFile = file;
        session.fileOperation = FileOperation.DOWNLOAD;
        sendResponse(channel, "150 Preparando para enviar " + file.getName() + "\r\n");
    }

    private void handleMgetCommand(SocketChannel channel, ClientSession session, String command) throws IOException {
        String[] files = command.substring(5).trim().split("\\s+");
        session.filesToSend = new ArrayList<>();
        
        for (String file : files) {
            Path filePath = currentDir.resolve(file);
            if (Files.exists(filePath)) {
                session.filesToSend.add(filePath.toFile());
            }
        }
        
        session.fileOperation = FileOperation.MULTI_DOWNLOAD;
        sendResponse(channel, "150 Preparando para enviar " + session.filesToSend.size() + " archivos\r\n");
    }

    private void handleMkdirCommand(SocketChannel channel, String command) throws IOException {
        String dirName = command.substring(6).trim();
        Path newDir = currentDir.resolve(dirName);
        
        try {
            Files.createDirectories(newDir);
            sendResponse(channel, "257 Directorio creado correctamente\r\n");
        } catch (IOException e) {
            sendResponse(channel, "550 No se pudo crear el directorio\r\n");
        }
    }

    private void handleDeleteCommand(SocketChannel channel, String command) throws IOException {
        String fileName = command.substring(7).trim();
        Path filePath = currentDir.resolve(fileName);
        
        try {
            Files.delete(filePath);
            sendResponse(channel, "250 Archivo eliminado correctamente\r\n");
        } catch (IOException e) {
            sendResponse(channel, "550 No se pudo eliminar el archivo\r\n");
        }
    }

    private void handleCwdCommand(SocketChannel channel, String command) throws IOException {
        sendResponse(channel, "257 \"" + currentDir.toString() + "\"\r\n");
    }

    private void handleCdCommand(SocketChannel channel, String command) throws IOException {
        String newPath = command.substring(3).trim();
        
        if (newPath.equals("..")) {
            if (currentDir.getParent() != null && currentDir.getParent().endsWith(CARPETA_BASE)) {
                currentDir = currentDir.getParent();
                sendResponse(channel, "250 Directorio cambiado a " + currentDir + "\r\n");
            } else {
                sendResponse(channel, "550 No se puede salir de la carpeta base\r\n");
            }
        } else {
            Path newDir = currentDir.resolve(newPath).normalize();
            
            if (Files.exists(newDir) && Files.isDirectory(newDir)) {
                currentDir = newDir;
                sendResponse(channel, "250 Directorio cambiado a " + currentDir + "\r\n");
            } else {
                sendResponse(channel, "550 No existe el directorio\r\n");
            }
        }
    }

    private void handleLsCommand(SocketChannel channel) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("200 Listado de directorio\r\n");
        
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(currentDir)) {
            for (Path file : stream) {
                if (Files.isDirectory(file)) {
                    response.append(file.getFileName()).append("/\r\n");
                } else {
                    response.append(file.getFileName()).append("\r\n");
                }
            }
        } catch (IOException e) {
            response.append("550 Error al listar directorio\r\n");
        }
        
        sendResponse(channel, response.toString());
    }

    private void handleDataChannelRead(SocketChannel channel) throws IOException {
        // Buscar la sesión que tiene este canal de datos
        ClientSession session = null;
        for (ClientSession s : clientSessions.values()) {
            if (s.dataChannel == channel) {
                session = s;
                break;
            }
        }
        
        if (session == null) {
            channel.close();
            return;
        }
        
        switch (session.fileOperation) {
            case UPLOAD:
                receiveFile(channel, session);
                break;
            case MULTI_UPLOAD:
                receiveMultipleFiles(channel, session);
                break;
            case DOWNLOAD:
                sendFile(channel, session);
                break;
            case MULTI_DOWNLOAD:
                sendMultipleFiles(channel, session);
                break;
        }
    }

    private void receiveFile(SocketChannel channel, ClientSession session) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(session.currentFile);
             FileChannel fileChannel = fos.getChannel()) {
            
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            while (channel.read(buffer) > 0) {
                buffer.flip();
                fileChannel.write(buffer);
                buffer.clear();
            }
            
            sendResponse(session.controlChannel, "226 Archivo recibido correctamente\r\n");
        } finally {
            cleanupDataChannel(channel, session);
        }
    }

    private void receiveMultipleFiles(SocketChannel channel, ClientSession session) throws IOException {
        // Implementación similar a receiveFile pero para múltiples archivos
        // (se necesitaría un protocolo adicional para manejar múltiples archivos)
        cleanupDataChannel(channel, session);
    }

    private void sendFile(SocketChannel channel, ClientSession session) throws IOException {
        try (FileInputStream fis = new FileInputStream(session.currentFile);
             FileChannel fileChannel = fis.getChannel()) {
            
            ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
            while (fileChannel.read(buffer) > 0) {
                buffer.flip();
                channel.write(buffer);
                buffer.clear();
            }
            
            sendResponse(session.controlChannel, "226 Archivo enviado correctamente\r\n");
        } finally {
            cleanupDataChannel(channel, session);
        }
    }

    private void sendMultipleFiles(SocketChannel channel, ClientSession session) throws IOException {
        // Implementación para enviar múltiples archivos
        // (se necesitaría comprimir en un ZIP temporal como en tu versión original)
        cleanupDataChannel(channel, session);
    }

    private void cleanupDataChannel(SocketChannel channel, ClientSession session) throws IOException {
        if (channel != null) {
            channel.close();
        }
        session.dataChannel = null;
        session.fileOperation = FileOperation.NONE;
    }

    private void writeData(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSession session = clientSessions.get(channel);
        
        if (session != null && !session.outputBuffer.isEmpty()) {
            ByteBuffer buffer = session.outputBuffer.remove(0);
            channel.write(buffer);
            
            if (!buffer.hasRemaining()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void sendResponse(SocketChannel channel, String response) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(response.getBytes(StandardCharsets.ISO_8859_1));
        
        ClientSession session = clientSessions.get(channel);
        if (session != null) {
            session.outputBuffer.add(buffer);
            channel.register(selector, SelectionKey.OP_WRITE);
        } else {
            channel.write(buffer);
        }
    }

    private void disconnectClient(SocketChannel channel) throws IOException {
        ClientSession session = clientSessions.remove(channel);
        if (session != null && session.dataChannel != null) {
            session.dataChannel.close();
        }
        channel.close();
    }

    private static class ClientSession {
        SocketChannel controlChannel;
        SocketChannel dataChannel;
        StringBuilder input = new StringBuilder();
        List<ByteBuffer> outputBuffer = new ArrayList<>();
        FileOperation fileOperation = FileOperation.NONE;
        File currentFile;
        List<File> filesToSend;
    }

    private enum FileOperation {
        NONE, UPLOAD, MULTI_UPLOAD, DOWNLOAD, MULTI_DOWNLOAD
    }

    public static void main(String[] args) {
        try {
            ServidorNIO servidor = new ServidorNIO();
            servidor.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}