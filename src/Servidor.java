import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Servidor {
    public static void main(String[] args) {
        try {
            // Creamos el Socket de control
            ServerSocket s1 = new ServerSocket(21); // Asignamos el puerto 21, que es el de control para FTP
            s1.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            System.out.println("Servidor iniciado en el puerto " + s1.getLocalPort());
            // Creacion de la carpeta Drive
            File f = new File("");
            String ruta = f.getAbsolutePath();
            String carpeta = "drive";
            String rutaLocal = carpeta + "\\";
            String ruta_archivos = ruta + "\\" + rutaLocal;
            File f2 = new File(ruta_archivos);
            f2.mkdirs();
            f2.setWritable(true);
            //Creacion de la carpeta local
            File t=new File("");
            String rutaT="C:";
            String carpetaT="local";
            String rutaLocalT=carpetaT+"\\";
            String ruta_archivosT=rutaT+"\\"+rutaLocalT;
            File t2=new File(ruta_archivosT);
            t2.mkdirs();
            t2.setWritable(true);
            boolean estado=false; //false=drive, true=local
            File x2=f2; //Inciamos por defecto con la ruta drive

            for (;;) {
                Socket c1 = s1.accept();
                System.out.println("Cliente conectado desde " + c1.getInetAddress() + ":" + c1.getPort());
                BufferedReader reader = new BufferedReader(new InputStreamReader(c1.getInputStream(), "ISO-8859-1"));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(c1.getOutputStream(), "ISO-8859-1"));
                writer.println("Bienvenido a EscromDrive");
                writer.flush();
                // Creamos el Socket de datos
                ServerSocket s2 = new ServerSocket(20);
                s2.setReuseAddress(true);

                // Ciclo para recibir comandos
                while (true) {

                    String comando = reader.readLine();

                    if (comando.compareToIgnoreCase("QUIT") == 0) {
                        System.out.println("Cliente cierra la conexion");
                        writer.println("226: Cerrando la conexión de datos.");
                        writer.flush();
                        reader.close();
                        writer.close();
                        c1.close();

                        System.exit(0);
                    }
                    else {
                        System.out.println("Comando recibido: ");
                        System.out.println(comando);
                        // Responder a los comandos del cliente
                        if (comando.toUpperCase().startsWith("LS")) {

                            List<String> resultados = listFiles(x2, "");
                            if(resultados != null && !resultados.isEmpty()){
                                System.out.println("Entro");
                                for (String linea : resultados) {
                                    System.out.println(linea);
                                    writer.println(linea);
                                    writer.flush();
                                }
                            }else{
                                writer.println("550: Directorio Vacio"); // Indicador de fin de lista
                                writer.flush();
                            }

                            writer.println("END_LIST"); // Indicador de fin de lista
                            writer.flush();

                        }else if(comando.toUpperCase().startsWith("CWD")){
                            estado=!estado;
                            if(estado){
                                x2=t2;
                            }else{
                                x2=f2;
                            }

                            writer.println("Se cambio el modo correctamente"); // Indicador de fin de lista
                            writer.flush();
                        }
                        else if (comando.toUpperCase().startsWith("PWD")) {
                            String texto = x2.getAbsolutePath(); // Obtenemos la ruta general
                            String dirAbs = texto.replace(f.getAbsolutePath(), ""); // Conservamos unicamente la
                            // direccion de la carpeta Drive
                            writer.println(dirAbs);
                            writer.flush();
                        } else if (comando.toUpperCase().startsWith("MKDIR")) {
                            String texto = comando;
                            String directorio = texto.replaceAll("(?i)MKDIR ", "");
                            // Creación del directorio
                            String ruta_nueva = x2.getAbsolutePath() + "\\" + directorio;
                            File nuevoDirectorio = new File(ruta_nueva);
                            if (nuevoDirectorio.mkdirs()) { // Creamos el directorio
                                writer.println("Se creó el directorio correctamente");
                            } else {
                                writer.println("No se pudo crear el directorio");
                            }
                            writer.flush();
                        }else if (comando.toUpperCase().startsWith("DELETE")) {
                            String texto = comando;
                            String directorio = texto.replaceAll("(?i)DELETE ", "");// unicamente el nombre
                            // Instanciamos la clase file con la ruta del fichero
                            System.out.println(directorio);
                            File miFichero = new File(x2.getAbsolutePath() + "\\" + directorio);
                            System.out.println(miFichero.getAbsolutePath());
                            // Comprobamos si existe el fichero
                            if (miFichero.exists()) {
                                // Borramos el fichero
                                boolean eliminado= miFichero.delete();
                                if (eliminado) {
                                    writer.println("200: " + miFichero.getName() + " se elimino correctamente");
                                    writer.flush();
                                } else {
                                    writer.println("502: El directorio no esta vacio y no se puede eliminar");
                                    writer.flush();
                                }
                                writer.flush();
                            } else {
                                writer.println(miFichero.getName() + " NO existe");
                                writer.flush();
                            }
                        } else if (comando.toUpperCase().startsWith("CD")) {
                            String direccion_actual = x2.getAbsolutePath();
                            String texto = comando.replaceAll("(?i)CD ", "").trim(); // Elimina "CD" y espacios extra
                            if (texto.equals("..")) {
                                // Evitar salir del directorio "drive"
                                if (x2.getName().equalsIgnoreCase("drive")||x2.getName().equalsIgnoreCase("local")) {
                                    writer.println("No se puede retroceder más, ya estamos en el directorio raíz.");
                                    writer.flush();
                                } else {
                                    // Ir al directorio padre
                                    File directorioPadre = x2.getParentFile();
                                    if (directorioPadre != null) {
                                        x2 = directorioPadre;
                                        System.out.println(ruta_archivos);
                                        System.out.println(rutaLocal);
                                        writer.println("Se cambió correctamente al directorio: " + x2.getAbsolutePath().replace(f.getAbsolutePath(),""));
                                        writer.flush();
                                    } else {
                                        writer.println("No se puede retroceder, no existe directorio padre.");
                                        writer.flush();
                                    }
                                }
                            } else if (texto.startsWith("\\")) {
                                // Ruta absoluta
                                String nuevaRuta = f.getAbsolutePath() + texto;
                                x2 = new File(nuevaRuta);
                                if (!x2.isDirectory()) {
                                    System.out.println("550 No se encontró la dirección");
                                    writer.println("550 No se encontró la dirección");
                                    writer.flush();
                                    x2 = new File(direccion_actual); // Volver al directorio anterior
                                } else {
                                    writer.println("Se cambió correctamente al directorio: " + x2.getAbsolutePath().replace(f.getAbsolutePath(), ""));
                                    writer.flush();
                                }
                            }else if(texto.equals("")){
                                writer.println("553 Acción no realizada. Nombre de fichero no permitido.");
                                writer.flush();
                            } else {
                                // Ruta relativa
                                String nuevaRuta = x2.getAbsolutePath() + "\\" + texto;
                                x2 = new File(nuevaRuta);
                                if (!x2.isDirectory()) {
                                    System.out.println("550 No se encontró la dirección");
                                    writer.println("550 No se encontró la dirección");
                                    writer.flush();
                                    x2 = new File(direccion_actual); // Volver al directorio anterior
                                } else {
                                    writer.println("Se cambió correctamente al directorio: " + x2.getAbsolutePath().replace(f.getAbsolutePath(),""));
                                    writer.flush();
                                }
                            }
                        } else if (comando.toUpperCase().startsWith("PUT")) {
                            writer.println("200: Cargando...");
                            writer.flush();
                            try{

                                System.out.println("Servidor iniciado esperando archivos");
                                for(;;) {
                                    Socket c2 = s2.accept();
                                    System.out.println("Cliente conectado al socket de datos desde " + c2.getInetAddress() + ": " + c2.getPort());
                                    DataInputStream dis = new DataInputStream(c2.getInputStream());
                                    String nombre = dis.readUTF();
                                    long tam = dis.readLong();
                                    System.out.println("Comienza la descarga del archivo " + nombre + " de: " + tam + " bytes\n\n");
                                    DataOutputStream dos = new DataOutputStream(new FileOutputStream(f2.getAbsolutePath()+"\\"+nombre));
                                    long recibidos = 0;
                                    int l = 0, porcentaje = 0;
                                    while (recibidos < tam) {
                                        System.out.println("entro");
                                        byte[] b = new byte[3500];
                                        l = dis.read(b);
                                        System.out.println("Leidos " + l);
                                        dos.write(b, 0, l);
                                        dos.flush();
                                        recibidos += l;
                                        porcentaje = (int) ((recibidos * 100) / tam);
                                        System.out.println("\rRecibido el " + porcentaje + "% del archivo");
                                    }
                                    System.out.println("Archivo recibido...");
                                    dos.close();
                                    dis.close();
                                    c2.close();
                                    break;
                                }
                            }catch (Exception e){
                                e.printStackTrace();
                            }
                        } else if (comando.toUpperCase().startsWith("GET")) {
                            String nombreArchivo = comando.replace("GET ", "").trim();
                            File archivo = new File(x2.getAbsolutePath() + "\\" + nombreArchivo);
                            if (archivo.exists() && archivo.isFile()) {
                                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(archivo))) {
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    OutputStream outputStream = c1.getOutputStream();
                                    while ((bytesRead = bis.read(buffer)) != -1) {
                                        outputStream.write(buffer, 0, bytesRead);
                                    }
                                    writer.println("Archivo " + nombreArchivo + " descargado correctamente.");
                                } catch (IOException e) {
                                    writer.println("Error al descargar el archivo " + nombreArchivo);
                                }
                            } else {
                                writer.println("El archivo " + nombreArchivo + " no existe.");
                            }
                            writer.flush();
                        } else if (comando.toUpperCase().startsWith("MGET")) {
                            String[] archivos = comando.replace("MGET ", "").trim().split(" ");
                            for (String nombreArchivo : archivos) {
                                File archivo = new File(x2.getAbsolutePath() + "\\" + nombreArchivo);
                                if (archivo.exists() && archivo.isFile()) {
                                    try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(archivo))) {
                                        byte[] buffer = new byte[4096];
                                        int bytesRead;
                                        OutputStream outputStream = c1.getOutputStream();
                                        while ((bytesRead = bis.read(buffer)) != -1) {
                                            outputStream.write(buffer, 0, bytesRead);
                                        }
                                        writer.println("Archivo " + nombreArchivo + " descargado correctamente.");
                                    } catch (IOException e) {
                                        writer.println("Error al descargar el archivo " + nombreArchivo);
                                    }
                                } else {
                                    writer.println("El archivo " + nombreArchivo + " no existe.");
                                }
                            }
                            writer.flush();
                        } else {
                            System.out.println("502 Orden no implementada.");
                            writer.println("502 Orden no implementada.");
                            writer.flush();
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
}