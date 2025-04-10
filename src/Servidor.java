
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

public class Servidor {
    boolean servidorActivo = true;

    public static void main(String[] args) {
        try {
            // Iniciar servidor
            int puertoFTP = 21;
            ServerSocket servidor = new ServerSocket(puertoFTP);
            servidor.setReuseAddress(true); // la forma correcta para ServerSocket
            System.out.println("Servidor iniciado en el puerto " + servidor.getLocalPort());

            // Verificar y crear carpeta del servidor
            File carpetaServidor = new File("drive");
            File carpetaServidorRutaAbsoluta = carpetaServidor.getAbsoluteFile();

            if (carpetaServidor.exists() && carpetaServidor.isDirectory()) {
                System.out.println("La carpeta ya existe: ");
            } else {
                System.out.println("La carpeta NO existe, creando automáticamente.");
                // Crear la carpeta y subcarpetas si no existen
                if (carpetaServidor.mkdirs()) {
                    System.out.println("Carpeta creada correctamente.");
                } else {
                    System.out.println("No se pudo crear la carpeta.");
                }
            }

            // Información extra
            System.out.println("Ruta relativa: " + carpetaServidor);
            System.out.println("Ruta absoluta: " + carpetaServidorRutaAbsoluta);
            while (true) {
                Socket socketCliente = servidor.accept();
                System.out.println("Cliente conectado desde " + socketCliente.getInetAddress() + " desde el puerto: "
                        + socketCliente.getPort());
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socketCliente.getInputStream(), "ISO-8859-1"));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socketCliente.getOutputStream(), "ISO-8859-1"));
                writer.println("Bienvenido a EscomDrive");
                writer.flush();
                // Recibimos los comandos
                while (true) {
                    String comando = reader.readLine();
                    System.out.println("Comando recibido: " + comando);
                    // Si se recibe un PUT
                    if (comando.toUpperCase().startsWith("PUT")) {
                        ServerSocket serverSocketRecibirDatos = new ServerSocket(5000); // Modo pasivo, abrimos un
                                                                                        // ServerSocket
                        // para recibir datos en el puerto 5000
                        System.out.println("Servidor iniciado esperando archivos");
                        writer.println("Listo");
                        writer.flush();
                        Socket socketDatos = serverSocketRecibirDatos.accept();
                        System.out.println("Cliente conectado al socket de datos desde "
                                + socketDatos.getInetAddress() + ": " + socketDatos.getPort());
                        DataInputStream dis = new DataInputStream(socketDatos.getInputStream());
                        String nombreArchivo = dis.readUTF();
                        long tam = dis.readLong();
                        File archivoDestino = new File(carpetaServidor + "/" + nombreArchivo);
                        DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoDestino));
                        long recibidos = 0;
                        int l = 0, porcentaje = 0;
                        while (recibidos < tam) {
                            byte[] b = new byte[3500];
                            l = dis.read(b);
                            // Escribe 'l' bytes desde el arreglo 'b' en el archivo de destino, comenzando
                            // desde el índice 0.
                            dos.write(b, 0, l);
                            dos.flush();
                            recibidos += l;
                            porcentaje = (int) ((recibidos * 100) / tam);
                            System.out.print("\rRecibido el " + porcentaje + "% del archivo");
                        }
                        dos.close();
                        dis.close();
                        serverSocketRecibirDatos.close();
                        System.out.println("\nArchivo recibido...");
                        // Descomprimir el zip
                        if (nombreArchivo.endsWith(".zip")) {
                            File carpetaZipDestino = new File(
                                    carpetaServidor + "/" + nombreArchivo.replace(".zip", ""));
                            carpetaZipDestino.mkdirs();
                            descomprimirZip(archivoDestino, carpetaZipDestino);
                            archivoDestino.delete(); // Opcional: borra el ZIP después de descomprimir
                        }
                    } else if (comando.toUpperCase().startsWith("MPUT")) {
                        System.out.println("Recibiendo múltiples archivos...");
                        writer.println("Listo");
                        writer.flush();

                        // Leer el número de archivos que el cliente enviará
                        int totalArchivos = Integer.parseInt(reader.readLine());

                        for (int i = 0; i < totalArchivos; i++) {
                            ServerSocket serverSocketRecibirDatos = new ServerSocket(5000);
                            System.out.println("Esperando conexión para archivo " + (i + 1) + " de " + totalArchivos);
                            Socket socketDatos = serverSocketRecibirDatos.accept();
                            System.out.println("Cliente conectado desde " + socketDatos.getInetAddress() + ":"
                                    + socketDatos.getPort());

                            DataInputStream dis = new DataInputStream(socketDatos.getInputStream());
                            String nombreArchivo = dis.readUTF();
                            long tam = dis.readLong();

                            File archivoDestino = new File(carpetaServidor + "/" + nombreArchivo);
                            DataOutputStream dos = new DataOutputStream(new FileOutputStream(archivoDestino));

                            long recibidos = 0;
                            int l = 0, porcentaje = 0;
                            while (recibidos < tam) {
                                byte[] b = new byte[3500];
                                l = dis.read(b);
                                dos.write(b, 0, l);
                                dos.flush();
                                recibidos += l;
                                porcentaje = (int) ((recibidos * 100) / tam);
                                System.out.print("\rRecibido " + porcentaje + "% del archivo " + nombreArchivo);
                            }
                            System.out.println("\nArchivo recibido: " + nombreArchivo);

                            dos.close();
                            dis.close();
                            serverSocketRecibirDatos.close();

                            // Si es un ZIP se descomprime
                            if (nombreArchivo.endsWith(".zip")) {
                                File carpetaZipDestino = new File(
                                        carpetaServidor + "/" + nombreArchivo.replace(".zip", ""));
                                carpetaZipDestino.mkdirs();
                                descomprimirZip(archivoDestino, carpetaZipDestino);
                                archivoDestino.delete();
                            }
                        }
                    }

                    else if (comando.toUpperCase().startsWith("GET")) {
                        String rutaRelative = comando.substring(4).trim();
                        File rutaArchivoODirectorioAEnviar = new File(carpetaServidor, rutaRelative);

                        // Verifica si el archivo o directorio existe
                        if (!rutaArchivoODirectorioAEnviar.exists()) {
                            writer.println("550 Archivo no encontrado");
                            writer.flush();
                        } else {
                            // Abre el ServerSocket en el puerto 5000 para enviar el archivo
                            ServerSocket serverSocketEnviarDatos = new ServerSocket(5000);
                            System.out.println("Servidor esperando conexión de datos para GET en el puerto 5000...");
                            writer.println("Listo");
                            writer.flush();

                            // Espera la conexión del cliente en el socket de datos
                            Socket socketDatos = serverSocketEnviarDatos.accept();

                            File archivoAEnviar;
                            if (rutaArchivoODirectorioAEnviar.isDirectory()) {
                                // Comprime la carpeta para enviarla
                                archivoAEnviar = new File(".zip");
                                comprimirCarpeta(rutaArchivoODirectorioAEnviar, archivoAEnviar);
                            } else {
                                archivoAEnviar = rutaArchivoODirectorioAEnviar;
                            }
                            System.out.println("Cliente conectado al socket de datos (GET) desde " +
                                    socketDatos.getInetAddress() + ":" + socketDatos.getPort());

                            String nombre = archivoAEnviar.getName();
                            String path = archivoAEnviar.getAbsolutePath();
                            long tam = archivoAEnviar.length();

                            System.out.println("Enviando metadatos para el archivo: " + path + " de " + tam + " bytes");

                            // Crea un DataOutputStream para enviar datos por el socket
                            DataOutputStream dos = new DataOutputStream(socketDatos.getOutputStream());
                            // Envía el nombre y tamaño del archivo
                            dos.writeUTF(nombre);
                            dos.flush();
                            dos.writeLong(tam);
                            dos.flush();
                            String respuesta = reader.readLine();
                            if (respuesta.equals("ListoParaRecibir")) {
                                // Envía el contenido del archivo
                                DataInputStream dis = new DataInputStream(new FileInputStream(path));
                                long enviados = 0;
                                int l = 0, porcentaje = 0;
                                byte[] b;
                                while (enviados < tam) {
                                    b = new byte[3500];
                                    l = dis.read(b);
                                    dos.write(b, 0, l);
                                    dos.flush();
                                    enviados += l;
                                    porcentaje = (int) ((enviados * 100) / tam);
                                    System.out.print("\rEnviado el " + porcentaje + "% del archivo");
                                }
                                System.out.println("\nArchivo enviado");
                                dis.close();
                            }

                            dos.close();
                            socketDatos.close();

                            // Si se comprimió una carpeta, elimina el .zip temporal
                            if (rutaArchivoODirectorioAEnviar.isDirectory()) {
                                archivoAEnviar.delete();
                            }
                            serverSocketEnviarDatos.close();
                        }
                    } else if (comando.toUpperCase().startsWith("MGET")) {
                        // Separa las rutas enviadas después de "MGET"
                        String[] rutas = comando.substring(5).trim().split("\\s+");

                        // Notifica que se está listo para enviar archivos
                        writer.println("Listo");
                        writer.flush();

                        // Envía al cliente el número total de archivos/carpetas a transferir
                        writer.println(rutas.length);
                        writer.flush();

                        // Itera en cada ruta
                        for (String rutaRelative : rutas) {
                            File rutaArchivoODirectorioAEnviar = new File(carpetaServidor, rutaRelative);

                            // Verifica existencia; si no existe, se podría enviar un error
                            if (!rutaArchivoODirectorioAEnviar.exists()) {
                                writer.println("550");
                                writer.flush();
                                System.out.println("No se encontró " + rutaRelative);
                                continue; // Se salta este archivo y se continúa con el siguiente
                            } else {
                                // Notifica que se continuará con este archivo
                                writer.println("OK");
                                writer.flush();
                            }

                            // Abre el ServerSocket para la transferencia de este archivo
                            try {
                                ServerSocket serverSocketEnviarDatos = new ServerSocket(5000);
                                System.out.println("Servidor esperando conexión de datos para " + rutaRelative
                                        + " en el puerto 5000...");
                                // Notifica al cliente que ya se abrió el socket para este archivo
                                writer.println("Listo");
                                writer.flush();

                                // Espera la conexión del cliente en el socket
                                Socket socketDatos = serverSocketEnviarDatos.accept();

                                File archivoAEnviar;
                                if (rutaArchivoODirectorioAEnviar.isDirectory()) {
                                    // Comprime la carpeta para enviarla
                                    archivoAEnviar = new File(".zip"); // Se recomienda usar un nombre temporal único si
                                                                       // fuera necesario
                                    comprimirCarpeta(rutaArchivoODirectorioAEnviar, archivoAEnviar);
                                } else {
                                    archivoAEnviar = rutaArchivoODirectorioAEnviar;
                                }

                                System.out.println("Cliente conectado al socket de datos (MGET) desde " +
                                        socketDatos.getInetAddress() + ":" + socketDatos.getPort());

                                String nombre = archivoAEnviar.getName();
                                String path = archivoAEnviar.getAbsolutePath();
                                long tam = archivoAEnviar.length();

                                System.out.println(
                                        "Enviando metadatos para el archivo: " + path + " de " + tam + " bytes");

                                DataOutputStream dos = new DataOutputStream(socketDatos.getOutputStream());
                                // Envía metadatos: nombre y tamaño
                                dos.writeUTF(nombre);
                                dos.flush();
                                dos.writeLong(tam);
                                dos.flush();

                                // Espera la confirmación del cliente para iniciar la transferencia
                                String respuesta = reader.readLine();
                                if (respuesta.equals("ListoParaRecibir")) {
                                    DataInputStream dis = new DataInputStream(new FileInputStream(path));
                                    long enviados = 0;
                                    int l = 0, porcentaje = 0;
                                    byte[] b;
                                    while (enviados < tam) {
                                        b = new byte[3500];
                                        l = dis.read(b);
                                        dos.write(b, 0, l);
                                        dos.flush();
                                        enviados += l;
                                        porcentaje = (int) ((enviados * 100) / tam);
                                        System.out
                                                .print("\rEnviado el " + porcentaje + "% del archivo " + rutaRelative);
                                    }
                                    System.out.println("\nArchivo enviado: " + rutaRelative);
                                    dis.close();
                                }

                                dos.close();
                                socketDatos.close();
                                serverSocketEnviarDatos.close();

                                // Si se comprimió la carpeta, elimina el ZIP temporal
                                if (rutaArchivoODirectorioAEnviar.isDirectory()) {
                                    archivoAEnviar.delete();
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    } else if (comando.toUpperCase().startsWith("MKDIR")) {
                        // Obtiene la parte del comando que indica el nombre/ruta relativa del nuevo
                        // directorio
                        String dirName = comando.substring(6).trim();
                        // Crea el nuevo directorio bajo la carpeta del servidor
                        File nuevoDirectorio = new File(carpetaServidor, dirName);
                        if (nuevoDirectorio.mkdirs()) {
                            writer.println("Se creó el directorio correctamente");
                        } else {
                            writer.println("No se pudo crear el directorio");
                        }
                        writer.flush();
                    } else if (comando.toUpperCase().startsWith("DELETE")) {
                        String nombreArchivo = comando.substring(7).trim();
                        File miFichero = new File(carpetaServidor, nombreArchivo);
                        if (miFichero.exists()) {

                            if (miFichero.delete()) {
                                writer.println("200: " + miFichero.getName() + " se eliminó correctamente");
                            } else {
                                writer.println("502: El directorio no está vacío y no se puede eliminar");
                            }
                        } else {
                            writer.println(miFichero.getName() + " NO existe");
                        }
                        writer.flush();
                    } else if (comando.toUpperCase().startsWith("PWD")) {
                        writer.println("\nDirectorio actual: " + carpetaServidor + ">");
                        writer.flush();
                    } else if (comando.toUpperCase().startsWith("CWD")) {
                        writer.println(carpetaServidor);
                        writer.flush();
                    } else if (comando.toUpperCase().startsWith("CD")) {
                        String nuevaRutaRelativa = comando.substring(3).trim();
                        File nuevaRuta;

                        if (nuevaRutaRelativa.equals("..")) {
                            nuevaRuta = carpetaServidor.getParentFile();
                        } else {
                            nuevaRuta = new File(carpetaServidor, nuevaRutaRelativa).getAbsoluteFile();
                        }

                        if (nuevaRuta != null && nuevaRuta.exists() && nuevaRuta.isDirectory()) {
                            carpetaServidor = nuevaRuta;
                            writer.println(carpetaServidor + " >");

                            // Obtener la ruta relativa desde la carpeta base "drive"
                            String rutaCompleta = carpetaServidor.getAbsolutePath();
                            String rutaRelativa = rutaCompleta.substring(rutaCompleta.indexOf("drive"));

                            writer.println(rutaRelativa);
                        } else {
                            writer.println("No se pudo cambiar a la ruta");
                            String rutaCompleta = carpetaServidor.getAbsolutePath();
                            String rutaRelativa = rutaCompleta.substring(rutaCompleta.indexOf("drive"));
                            writer.println(rutaRelativa);
                        }
                        writer.flush();
                    }

                    else if (comando.toUpperCase().startsWith("QUIT")) {
                        System.out.println("Cliente cierra la conexión");
                        writer.println("226: Cerrando la conexión de datos.");
                        writer.flush();
                        writer.println("Listo");
                        writer.flush();
                        // Cierra streams y sockets del lado del servidor
                        reader.close();
                        writer.close();
                        socketCliente.close();
                        System.exit(0);
                    } else if (comando.toUpperCase().startsWith("LS")) {
                        writer.println("---------- Listando archivos y carpetas (SERVIDOR) ----------");
                        writer.flush(); // Asegura que el mensaje se envíe antes de los archivos
                        List<String> resultados = listFiles(carpetaServidor, "");
                        if (resultados != null && !resultados.isEmpty()) {
                            // System.out.println("Entro");
                            for (String linea : resultados) {
                                writer.println(linea);
                                writer.flush();
                            }
                        } else {
                            writer.println("550: Directorio Vacio"); // Indicador de directorio vacío
                            writer.flush();
                        }

                        writer.println("Finalizado"); // Indicador de fin de lista
                        writer.flush();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public static List<String> listFiles(File dir, String tabulacion) {
        List<String> resultados = new ArrayList<>();
        File[] files = dir.listFiles(); // Obtenemos archivos y directorios
        if (files != null) { // Verificamos que el directorio no esté vacío
            for (File file : files) {
                String resultado = tabulacion + file.getName(); // Solo mostramos el nombre del archivo o directorio
                if (file.isDirectory()) {
                    resultado = resultado + "\\"; // Si es un directorio, agregamos "\" al final para identificarlo
                }
                resultados.add(resultado); // Agregar a la lista
                if (file.isDirectory()) { // Si es un directorio, hacemos recursión
                    resultados.addAll(listFiles(file, tabulacion + "\t")); // Se agrega una tabulación extra
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
