import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Servidor {
    public static void main(String[] args) {
        try {
            // Creamos el Socket de control
            ServerSocket servidor = new ServerSocket(20); // Asignamos el puerto 21, que es el de control para FTP
            servidor.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            System.out.println("Servidor iniciado en el puerto " + servidor.getLocalPort());
            // Creacion de la carpeta Drive
            File f = new File("");
            String ruta = f.getAbsolutePath();
            System.out.println("La ruta es " + ruta);
            String rutaLocal = "drive" + "\\";
            String ruta_archivos = ruta + "\\" + rutaLocal;
            File f2 = new File(ruta_archivos);
            f2.mkdirs();
            f2.setWritable(true);
            File x2 = f2; // Inciamos por defecto con la ruta drive

            for (;;) {
                Socket c1 = servidor.accept();
                System.out.println("Cliente conectado desde " + c1.getInetAddress() + ":" + c1.getPort());
                BufferedReader in = new BufferedReader(new InputStreamReader(c1.getInputStream(), "ISO-8859-1"));
                PrintWriter out = new PrintWriter(new OutputStreamWriter(c1.getOutputStream(), "ISO-8859-1"));
                out.println("Bienvenido a EscromDrive");
                out.flush();
                ServerSocket s2 = new ServerSocket(21);
                s2.setReuseAddress(true);
                while (true) {

                    String comando = in.readLine();

                    if (comando.compareToIgnoreCase("QUIT") == 0) {
                        System.out.println("Cliente cierra la conexion");
                        out.println("226: Cerrando la conexión de datos.");
                        out.flush();
                        in.close();
                        out.close();
                        c1.close();

                        System.exit(0);
                    } else {
                        System.out.println("Comando recibido: ");
                        System.out.println(comando);
                        // Responder a los comandos del cliente
                        if (comando.toUpperCase().startsWith("LS")) {
                            out.println("---------- Listando archivos y carpetas (SERVIDOR) ----------");
                            out.flush(); // Asegura que el mensaje se envíe antes de los archivos
                            System.out.println("La cosa x2 es " + x2);
                            List<String> resultados = listFiles(x2, "");
                            if (resultados != null && !resultados.isEmpty()) {
                                // System.out.println("Entro");
                                for (String linea : resultados) {
                                    // System.out.println(linea);
                                    out.println(linea);
                                    out.flush();
                                }
                            } else {
                                out.println("550: Directorio Vacio"); // Indicador de directorio vacío
                                out.flush();
                            }

                            out.println("END_LIST"); // Indicador de fin de lista
                            out.flush();
                        } else if (comando.toUpperCase().startsWith("CWD")) {
                            out.println("Se cambio el modo correctamente"); // Indicador de fin de lista
                            out.flush();
                        } else if (comando.toUpperCase().startsWith("PWD")) {
                            String texto = x2.getAbsolutePath(); // Obtenemos la ruta general
                            String dirAbs = texto.replace(f.getAbsolutePath(), ""); // Conservamos unicamente la
                            // direccion de la carpeta Drive
                            out.println(dirAbs);
                            out.flush();
                        } else if (comando.toUpperCase().startsWith("MKDIR")) {
                            String texto = comando;
                            String directorio = texto.replaceAll("(?i)MKDIR ", "");
                            // Creación del directorio
                            String ruta_nueva = x2.getAbsolutePath() + "\\" + directorio;
                            File nuevoDirectorio = new File(ruta_nueva);
                            if (nuevoDirectorio.mkdirs()) { // Creamos el directorio
                                out.println("Se creó el directorio correctamente");
                            } else {
                                out.println("No se pudo crear el directorio");
                            }
                            out.flush();
                        } else if (comando.toUpperCase().startsWith("DELETE")) {
                            String texto = comando;
                            String directorio = texto.replaceAll("(?i)DELETE ", "");// unicamente el nombre
                            // Instanciamos la clase file con la ruta del fichero
                            System.out.println(directorio);
                            File miFichero = new File(x2.getAbsolutePath() + "\\" + directorio);
                            System.out.println(miFichero.getAbsolutePath());
                            // Comprobamos si existe el fichero
                            if (miFichero.exists()) {
                                // Borramos el fichero
                                boolean eliminado = miFichero.delete();
                                if (eliminado) {
                                    out.println("200: " + miFichero.getName() + " se elimino correctamente");
                                    out.flush();
                                } else {
                                    out.println("502: El directorio no esta vacio y no se puede eliminar");
                                    out.flush();
                                }
                                out.flush();
                            } else {
                                out.println(miFichero.getName() + " NO existe");
                                out.flush();
                            }
                        } else if (comando.toUpperCase().startsWith("CD")) {
                            String direccion_actual = x2.getAbsolutePath();
                            String texto = comando.replaceAll("(?i)CD ", "").trim(); // Elimina "CD" y espacios extra
                            if (texto.equals("..")) {
                                // Evitar salir del directorio "drive"
                                if (x2.getName().equalsIgnoreCase("drive") || x2.getName().equalsIgnoreCase("local")) {
                                    out.println("No se puede retroceder más, ya estamos en el directorio raíz.");
                                    out.flush();
                                } else {
                                    // Ir al directorio padre
                                    File directorioPadre = x2.getParentFile();
                                    if (directorioPadre != null) {
                                        x2 = directorioPadre;
                                        System.out.println(ruta_archivos);
                                        System.out.println(rutaLocal);
                                        out.println("Se cambió correctamente al directorio: "
                                                + x2.getAbsolutePath().replace(f.getAbsolutePath(), ""));
                                        out.flush();
                                    } else {
                                        out.println("No se puede retroceder, no existe directorio padre.");
                                        out.flush();
                                    }
                                }
                            } else if (texto.startsWith("\\")) {
                                // Ruta absoluta
                                String nuevaRuta = f.getAbsolutePath() + texto;
                                x2 = new File(nuevaRuta);
                                if (!x2.isDirectory()) {
                                    System.out.println("550 No se encontró la dirección");
                                    out.println("550 No se encontró la dirección");
                                    out.flush();
                                    x2 = new File(direccion_actual); // Volver al directorio anterior
                                } else {
                                    out.println("Se cambió correctamente al directorio: "
                                            + x2.getAbsolutePath().replace(f.getAbsolutePath(), ""));
                                    out.flush();
                                }
                            } else if (texto.equals("")) {
                                out.println("553 Acción no realizada. Nombre de fichero no permitido.");
                                out.flush();
                            } else {
                                // Ruta relativa
                                String nuevaRuta = x2.getAbsolutePath() + "\\" + texto;
                                x2 = new File(nuevaRuta);
                                if (!x2.isDirectory()) {
                                    System.out.println("550 No se encontró la dirección");
                                    out.println("550 No se encontró la dirección");
                                    out.flush();
                                    x2 = new File(direccion_actual); // Volver al directorio anterior
                                } else {
                                    out.println("Se cambió correctamente al directorio: "
                                            + x2.getAbsolutePath().replace(f.getAbsolutePath(), ""));
                                    out.flush();
                                }
                            }
                        } else if (comando.toUpperCase().startsWith("PUT")) {
                            out.println("200: Cargando...");
                            out.flush();
                            try {
                                System.out.println("Servidor iniciado esperando archivos");
                                // Espera la conexión para la transferencia de datos
                                Socket c2 = s2.accept();
                                System.out.println("Cliente conectado al socket de datos desde " 
                                                   + c2.getInetAddress() + ": " + c2.getPort());
                                DataInputStream dis = new DataInputStream(c2.getInputStream());
                                String nombre = dis.readUTF();
                                long tam = dis.readLong();
                                System.out.println("La super ruta local es " + rutaLocal);
                                File archivoDestino = new File(rutaLocal + File.separator + nombre);
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
                                    System.out.print("\rRecibido el " + porcentaje + "% del archivo");
                                }
                                dos.close();
                                dis.close();
                                c2.close();
                                System.out.println("\nArchivo recibido...");
        
                                // Si es ZIP, descomprimirlo
                                if (nombre.endsWith(".zip")) {
                                    File carpetaZipDestino = new File(rutaLocal + File.separator + nombre.replace(".zip", ""));
                                    carpetaZipDestino.mkdirs();
                                    descomprimirZip(archivoDestino, carpetaZipDestino);
                                    archivoDestino.delete(); // Opcional: borra el ZIP después de descomprimir
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }else if (comando.toUpperCase().startsWith("MPUT")) {
                            String texto = comando.replaceAll("(?i)MPUT ", "").trim(); // Elimina "MPUT" y espacios extra
                            String[] archivos = texto.split(","); // Divide los nombres de archivos por comas
                            for (String archivo : archivos) {
                                File fileAux = new File(archivo.trim());
                                if (!fileAux.isFile()) {
                                    System.out.println("550 No se encontró el archivo: " + archivo);
                                } else {
                                    out.println("PUT " + archivo.trim()); // Enviar comando al servidor
                                    out.flush();
                                    System.out.println(in.readLine()); // Obtenemos la respuesta del comando
                                    try {
                                        int pto = 21;
                                        Socket c2 = new Socket("127.0.0.1",pto);
                                        System.out.println("Conexión con el socket de archivos establecida");
        
                                        String nombre = fileAux.getName();
                                        String path = fileAux.getAbsolutePath();
                                        long tam = fileAux.length();
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
                                        System.out.println("Archivo " + nombre + " enviado con éxito\n");
                                        dis.close();
                                        dos.close();
                                        c2.close();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        } else if (comando.toUpperCase().startsWith("GET")) {
                            String nombreArchivo = comando.replaceAll("(?i)GET ", "").trim();
                            File rutaArchivo = new File(ruta_archivos + "\\" + nombreArchivo);
                        
                            // Verificar existencia del recurso (archivo o carpeta)
                            if (!rutaArchivo.exists()) {
                                out.println("550 Archivo o carpeta no encontrado");
                                out.flush();
                                return;
                            }
                        
                            File archivoAEnviar;
                            boolean esCarpeta = false;
                            if (rutaArchivo.isDirectory()) {
                                esCarpeta = true;
                                // Comprimir carpeta en un ZIP temporal (se nombra con el nombre de la carpeta + ".zip")
                                archivoAEnviar = new File(rutaArchivo.getParent(), ".zip");
                                System.out.println("hola soy el server, el archivo a enviar es " + archivoAEnviar);
                                comprimirCarpeta(rutaArchivo, archivoAEnviar);
                            } else {
                                archivoAEnviar = rutaArchivo;
                            }
                        
                            // Preparar transferencia
                            out.println("150 Preparando para enviar " + nombreArchivo);
                            out.flush();
                        
                            try {
                                // Esperar conexión de datos del cliente (similar a PUT)
                                System.out.println("Esperando conexión de datos en puerto 21...");
                                try (Socket c2 = s2.accept();
                                     DataOutputStream dos = new DataOutputStream(c2.getOutputStream());
                                     DataInputStream dis = new DataInputStream(new FileInputStream(archivoAEnviar))) {
                        
                                    System.out.println("Conexión de datos establecida desde " + c2.getInetAddress());
                        
                                    // Enviar metadatos (nombre y tamaño)
                                    dos.writeUTF(archivoAEnviar.getName());
                                    dos.writeLong(archivoAEnviar.length());
                                    dos.flush();
                        
                                    // Transferir archivo
                                    byte[] buffer = new byte[4096];
                                    long enviados = 0;
                                    int bytesLeidos;
                                    while ((bytesLeidos = dis.read(buffer)) != -1) {
                                        dos.write(buffer, 0, bytesLeidos);
                                        enviados += bytesLeidos;
                                        int porcentaje = (int) ((enviados * 100) / archivoAEnviar.length());
                                        System.out.printf("\rEnviado %d%%", porcentaje);
                                    }
                        
                                    System.out.println("\nTransferencia completada");
                                    out.println("226 Archivo enviado con éxito");
                                    out.flush();
                                }
                            } catch (Exception e) {
                                out.println("425 Error en transferencia: " + e.getMessage());
                                out.flush();
                                e.printStackTrace();
                            }
                            
                            // Si era una carpeta comprimida, eliminar el ZIP temporal
                            if (esCarpeta) {
                                archivoAEnviar.delete();
                            }
                        }

                        else if (comando.toUpperCase().startsWith("MGET")) {
                            // Extraer la lista de archivos; se asume que están separados por espacios
                            String archivosStr = comando.replaceAll("(?i)MGET ", "").trim();
                            String[] listaArchivos = archivosStr.split("\\s+");

                            // Procesar cada archivo en secuencia
                            for (String nombreArchivo : listaArchivos) {
                                File rutaArchivo = new File(ruta_archivos + "\\" + nombreArchivo);

                                // Verificar existencia del archivo
                                if (!rutaArchivo.exists() || !rutaArchivo.isFile()) {
                                    out.println("550 Archivo " + nombreArchivo + " no encontrado o no es válido");
                                    out.flush();
                                    continue; // Saltar al siguiente archivo
                                }

                                // Enviar respuesta inicial para el archivo actual
                                out.println("150 Preparando para enviar " + nombreArchivo);
                                out.flush();

                                try {
                                    // Esperar conexión de datos para el archivo actual (similar a GET)
                                    System.out.println(
                                            "Esperando conexión de datos en puerto 21 para " + nombreArchivo + "...");
                                    try (Socket c2 = s2.accept();
                                            DataOutputStream dos = new DataOutputStream(c2.getOutputStream());
                                            DataInputStream dis = new DataInputStream(
                                                    new FileInputStream(rutaArchivo))) {

                                        System.out.println("Conexión de datos establecida desde " + c2.getInetAddress()
                                                + " para " + nombreArchivo);

                                        // Enviar metadatos (nombre y tamaño)
                                        dos.writeUTF(rutaArchivo.getName());
                                        dos.writeLong(rutaArchivo.length());
                                        dos.flush();

                                        // Transferir el archivo
                                        byte[] buffer = new byte[4096];
                                        long enviados = 0;
                                        int bytesLeidos;
                                        while ((bytesLeidos = dis.read(buffer)) != -1) {
                                            dos.write(buffer, 0, bytesLeidos);
                                            enviados += bytesLeidos;
                                            int porcentaje = (int) ((enviados * 100) / rutaArchivo.length());
                                            System.out.printf("\rEnviado %d%% para %s", porcentaje, nombreArchivo);
                                        }

                                        System.out.println("\nTransferencia completada para " + nombreArchivo);
                                        // Enviar confirmación final para el archivo actual
                                        out.println("226 Archivo " + nombreArchivo + " enviado con éxito");
                                        out.flush();
                                    }
                                } catch (Exception e) {
                                    out.println(
                                            "425 Error en transferencia de " + nombreArchivo + ": " + e.getMessage());
                                    out.flush();
                                    e.printStackTrace();
                                }
                            }
                        } else {
                            System.out.println("502 Orden no implementada.");
                            out.println("502 Orden no implementada.");
                            out.flush();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            zos.closeEntry();
        }
    }
}
}