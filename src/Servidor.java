import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

public class Servidor {
    public static void main(String[] args) {
        try {
            ServerSocket s1 = new ServerSocket(21);
            s1.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            System.out.println("Servidor iniciado en el puerto " + s1.getLocalPort());

            File f = new File("");
            String ruta = f.getAbsolutePath();
            String carpeta = "drive";
            String rutaLocal = carpeta + "\\";
            String ruta_archivos = ruta + "\\" + rutaLocal;
            File f2 = new File(ruta_archivos);
            f2.mkdirs();
            f2.setWritable(true);

            for (;;) {
                Socket c1 = s1.accept();
                System.out.println("Cliente conectado desde " + c1.getInetAddress() + ":" + c1.getPort());
                BufferedReader reader = new BufferedReader(new InputStreamReader(c1.getInputStream(), "ISO-8859-1"));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(c1.getOutputStream(), "ISO-8859-1"));
                writer.println("Bienvenido a EscromDrive");
                writer.flush();

                while (true) {
                    String comando = reader.readLine();
                    comando = comando.toUpperCase();
                    if (comando.compareToIgnoreCase("QUIT") == 0) {
                        System.out.println("Cliente cierra la conexion");
                        reader.close();
                        writer.close();
                        c1.close();
                        System.exit(0);
                    } else {
                        System.out.println("Comando recibido: " + comando);

                        if (comando.startsWith("LS")) {
                            List<String> resultados = listFiles(f2, "");
                            for (String linea : resultados) {
                                System.out.println(linea);
                                writer.println(linea);
                                writer.flush();
                            }
                            writer.println("END_LIST");
                            writer.flush();
                        } else if (comando.startsWith("PWD")) {
                            String texto = f2.getAbsolutePath();
                            String dirAbs = texto.replace(f.getAbsolutePath(), "");
                            writer.println(dirAbs);
                            writer.flush();
                        } else if (comando.startsWith("MKDIR")) {
                            String directorio = comando.replace("MKDIR ", "");
                            String ruta_nueva = f2.getAbsolutePath() + "\\" + directorio;
                            File nuevoDirectorio = new File(ruta_nueva);
                            if (nuevoDirectorio.mkdirs()) {
                                writer.println("Se creó el directorio correctamente");
                            } else {
                                writer.println("No se pudo crear el directorio");
                            }
                            writer.flush();
                        } else if (comando.startsWith("DELETE")) {
                            String directorio = comando.replace("DELETE ", "");
                            File miFichero = new File(f2.getAbsolutePath() + "\\" + directorio);
                            if (miFichero.exists()) {
                                miFichero.delete();
                                writer.println("Se elimino correctamente");
                            } else {
                                writer.println(miFichero.getName() + " NO existe");
                            }
                            writer.flush();
                        } else if (comando.startsWith("CD")) {
                            String direccion_actual = f2.getAbsolutePath();
                            String texto = comando.replace("CD", "").trim();

                            if (texto.equals("..")) {
                                if (f2.getName().equalsIgnoreCase("drive")) {
                                    writer.println("No se puede retroceder más, ya estamos en el directorio raíz.");
                                } else {
                                    File directorioPadre = f2.getParentFile();
                                    if (directorioPadre != null) {
                                        f2 = directorioPadre;
                                        writer.println("Se cambió correctamente al directorio: " + f2.getAbsolutePath());
                                    } else {
                                        writer.println("No se puede retroceder, no existe directorio padre.");
                                    }
                                }
                            } else if (texto.startsWith("\\")) {
                                String nuevaRuta = f.getAbsolutePath() + texto;
                                f2 = new File(nuevaRuta);
                                if (!f2.isDirectory()) {
                                    writer.println("550 No se encontró la dirección");
                                    f2 = new File(direccion_actual);
                                } else {
                                    writer.println("Se cambió correctamente al directorio: " + f2.getAbsolutePath());
                                }
                            } else {
                                String nuevaRuta = f2.getAbsolutePath() + "\\" + texto;
                                f2 = new File(nuevaRuta);
                                if (!f2.isDirectory()) {
                                    writer.println("550 No se encontró la dirección");
                                    f2 = new File(direccion_actual);
                                } else {
                                    writer.println("Se cambió correctamente al directorio: " + f2.getAbsolutePath());
                                }
                            }
                            writer.flush();
                        } else if (comando.startsWith("PUT")) {
                            String nombreArchivo = comando.replace("PUT ", "").trim();
                            File archivo = new File(f2.getAbsolutePath() + "\\" + nombreArchivo);
                            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(archivo))) {
                                byte[] buffer = new byte[4096];
                                int bytesRead;
                                InputStream inputStream = c1.getInputStream();
                                while ((bytesRead = inputStream.read(buffer)) != -1) {
                                    bos.write(buffer, 0, bytesRead);
                                }
                                writer.println("Archivo " + nombreArchivo + " subido correctamente.");
                            } catch (IOException e) {
                                writer.println("Error al subir el archivo " + nombreArchivo);
                            }
                            writer.flush();
                        } else if (comando.startsWith("MPUT")) {
                            String[] archivos = comando.replace("MPUT ", "").trim().split(" ");
                            for (String nombreArchivo : archivos) {
                                File archivo = new File(f2.getAbsolutePath() + "\\" + nombreArchivo);
                                try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(archivo))) {
                                    byte[] buffer = new byte[4096];
                                    int bytesRead;
                                    InputStream inputStream = c1.getInputStream();
                                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                                        bos.write(buffer, 0, bytesRead);
                                    }
                                    writer.println("Archivo " + nombreArchivo + " subido correctamente.");
                                } catch (IOException e) {
                                    writer.println("Error al subir el archivo " + nombreArchivo);
                                }
                            }
                            writer.flush();
                        } else if (comando.startsWith("GET")) {
                            String nombreArchivo = comando.replace("GET ", "").trim();
                            File archivo = new File(f2.getAbsolutePath() + "\\" + nombreArchivo);
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
                        } else if (comando.startsWith("MGET")) {
                            String[] archivos = comando.replace("MGET ", "").trim().split(" ");
                            for (String nombreArchivo : archivos) {
                                File archivo = new File(f2.getAbsolutePath() + "\\" + nombreArchivo);
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
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String resultado = tabulacion + file.getName();
                if (file.isDirectory()) {
                    resultado = resultado + "\\";
                }
                resultados.add(resultado);
                if (file.isDirectory()) {
                    resultados.addAll(listFiles(file, tabulacion + "\t"));
                }
            }
        }
        return resultados;
    }
}