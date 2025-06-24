import java.io.*;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.ArrayList;

public class Servidor {
    public static void main(String[] args) {
        try {
            int puertoFTP = 21;
            ServerSocketChannel servidor = ServerSocketChannel.open(); // Crear un canal de socket del servidor
            servidor.bind(new InetSocketAddress(puertoFTP)); // Vincular al puerto 21, bind especifica la dirección y el puerto
            servidor.configureBlocking(false);  // no bloqueante para aceptar clientes

            System.out.println("Servidor iniciado en el puerto " + puertoFTP);

            File carpetaServidor = new File("drive");
            if (!carpetaServidor.exists()) {
                carpetaServidor.mkdirs();
                System.out.println("Carpeta crea    da");
            }

            while (true) { // Bucle principal del servidor
                // Aceptar conexiones de clientes
                SocketChannel socketCliente = servidor.accept(); // Aceptar una conexión entrante
                if (socketCliente != null) { // Si hay un cliente conectado
                    // Configurar el canal del cliente
                    System.out.println("Cliente conectado: " + socketCliente.getRemoteAddress());
                    socketCliente.configureBlocking(true);  // ponemos el canal del cliente en bloqueante (simplifica)

                    // Streams para leer y escribir
                    InputStream in = socketCliente.socket().getInputStream(); // InputStream para leer datos del cliente
                    OutputStream out = socketCliente.socket().getOutputStream(); // OutputStream para enviar datos al cliente
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, "ISO-8859-1")); // BufferedReader para leer líneas de texto
                    PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, "ISO-8859-1"), true); // PrintWriter para escribir líneas de texto

                    // Mensaje bienvenida
                    writer.println("Bienvenido a EscomDrive");

                    while (true) { // Bucle para manejar comandos del cliente
                        // Leer comando del cliente
                        String comando = reader.readLine();
                        if (comando == null) break;  // Cliente se desconectó
                        System.out.println("Comando recibido: " + comando);

                        if (comando.toUpperCase().startsWith("PUT")) { // Comando PUT para subir archivos
                            ServerSocketChannel serverDatos = ServerSocketChannel.open(); // Crear un canal de socket del servidor para datos
                            serverDatos.bind(new InetSocketAddress(5000)); // Vincular al puerto 5000 para la transferencia de datos
                            serverDatos.configureBlocking(true); // Configurar el canal de datos en bloqueante

                            writer.println("Listo");

                            SocketChannel socketDatos = serverDatos.accept();
                            System.out.println("Recibiendo archivo desde: " + socketDatos.getRemoteAddress());

                            DataInputStream dis = new DataInputStream(socketDatos.socket().getInputStream());
                            String nombreArchivo = dis.readUTF();
                            long tam = dis.readLong();

                            File archivoDestino = new File(carpetaServidor, nombreArchivo);
                            DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoDestino));

                            byte[] b = new byte[4096];
                            long recibidos = 0;
                            int l;
                            while (recibidos < tam && (l = dis.read(b)) != -1) {
                                dos.write(b, 0, l);
                                recibidos += l;
                            }
                            dos.close();
                            dis.close();
                            socketDatos.close();
                            serverDatos.close();

                            System.out.println("Archivo recibido: " + nombreArchivo);

                            if (nombreArchivo.endsWith(".zip")) {
                                File carpetaZipDestino = new File(carpetaServidor, nombreArchivo.replace(".zip", ""));
                                carpetaZipDestino.mkdirs();
                                descomprimirZip(archivoDestino, carpetaZipDestino);
                                archivoDestino.delete();
                            }
                        }

                        else if (comando.toUpperCase().startsWith("GET")) {
                            String rutaRelative = comando.substring(4).trim();
                            File rutaArchivo = new File(carpetaServidor, rutaRelative);

                            if (!rutaArchivo.exists()) {
                                writer.println("550 Archivo no encontrado");
                            } else {
                                ServerSocketChannel serverDatos = ServerSocketChannel.open();
                                serverDatos.bind(new InetSocketAddress(5000));
                                serverDatos.configureBlocking(true);

                                writer.println("Listo");

                                SocketChannel socketDatos = serverDatos.accept();
                                System.out.println("Enviando archivo a: " + socketDatos.getRemoteAddress());

                                File archivoAEnviar;
                                if (rutaArchivo.isDirectory()) {
                                    archivoAEnviar = new File(".zip");
                                    comprimirCarpeta(rutaArchivo, archivoAEnviar);
                                } else {
                                    archivoAEnviar = rutaArchivo;
                                }

                                DataOutputStream dos = new DataOutputStream(socketDatos.socket().getOutputStream());
                                dos.writeUTF(archivoAEnviar.getName());
                                dos.writeLong(archivoAEnviar.length());
                                dos.flush();

                                String resp = reader.readLine();
                                if (resp.equals("ListoParaRecibir")) {
                                    DataInputStream dis = new DataInputStream(new FileInputStream(archivoAEnviar));
                                    byte[] b = new byte[4096];
                                    long enviados = 0;
                                    int l;
                                    while (enviados < archivoAEnviar.length() && (l = dis.read(b)) != -1) {
                                        dos.write(b, 0, l);
                                        enviados += l;
                                    }
                                    dis.close();
                                }

                                dos.close();
                                socketDatos.close();
                                serverDatos.close();

                                if (rutaArchivo.isDirectory()) {
                                    archivoAEnviar.delete();
                                }

                                System.out.println("Archivo enviado.");
                            }
                        }

                        else if (comando.toUpperCase().startsWith("MKDIR")) {
                            String dirName = comando.substring(6).trim();
                            File nuevoDir = new File(carpetaServidor, dirName);
                            if (nuevoDir.mkdirs()) {
                                writer.println("Se creó el directorio");
                            } else {
                                writer.println("No se pudo crear el directorio");
                            }
                        }

                        else if (comando.toUpperCase().startsWith("DELETE")) {
                            String nombreArchivo = comando.substring(7).trim();
                            File miFichero = new File(carpetaServidor, nombreArchivo);
                            if (miFichero.exists()) {
                                if (miFichero.delete()) {
                                    writer.println("200: Se eliminó correctamente");
                                } else {
                                    writer.println("502: No se pudo eliminar");
                                }
                            } else {
                                writer.println("No existe");
                            }
                        }

                        else if (comando.toUpperCase().startsWith("PWD")) {
                            writer.println("Directorio actual: " + carpetaServidor.getAbsolutePath());
                        }

                        else if (comando.toUpperCase().startsWith("LS")) {
                            writer.println("------ Listado de archivos ------");
                            List<String> resultados = listFiles(carpetaServidor, "");
                            if (resultados != null && !resultados.isEmpty()) {
                                for (String linea : resultados) {
                                    writer.println(linea);
                                }
                            } else {
                                writer.println("Directorio vacío");
                            }
                            writer.println("Finalizado");
                        }

                        else if (comando.toUpperCase().startsWith("CWD")) {
                            writer.println(carpetaServidor.getAbsolutePath());
                        }

                        else if (comando.toUpperCase().startsWith("QUIT")) {
                            writer.println("226: Cerrando conexión");
                            writer.println("Listo");
                            reader.close();
                            writer.close();
                            socketCliente.close();
                            System.out.println("Cliente desconectado.");
                            break;
                        }

                        // Puedes seguir añadiendo los comandos que tú tenías: MPUT, MGET, CD...
                    }

                }  // fin if socketCliente != null

                Thread.sleep(100);  // para no sobrecargar CPU
            }  // fin while(true)

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Métodos auxiliares para ZIP ---
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

    public static List<String> listFiles(File dir, String tabulacion) {
        List<String> resultados = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String resultado = tabulacion + file.getName();
                if (file.isDirectory()) {
                    resultado += "\\";
                }
                resultados.add(resultado);
                if (file.isDirectory()) {
                    resultados.addAll(listFiles(file, tabulacion + "\t"));
                }
            }
        }
        return resultados;
    }

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
}