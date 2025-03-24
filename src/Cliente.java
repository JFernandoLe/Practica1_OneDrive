import javax.swing.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Cliente {
    public static void main(String[] args) {
        // Secuencias para cambiar colores
        String rojo = "\u001B[31m";
        String verde = "\u001B[32m";
        String azul = "\u001B[34m";
        String reset = "\u001B[0m";
        // Scanner de Java
        Scanner scanner = new Scanner(System.in);
        try {
            String dir = "127.0.0.1"; // Direccion del servidor
            int puerto = 21;// Puerto de control para FTP
            Socket c1 = new Socket(dir, puerto); // Hacemos la conexion al Socket
            BufferedReader reader = new BufferedReader(new InputStreamReader(c1.getInputStream(), "ISO-8859-1"));
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(c1.getOutputStream(), "ISO-8859-1"));
            // Leemos la bienvenida del servidor

            System.out.println(azul + reader.readLine() + reset);
            System.out.println(azul + "Escribe un comando: " + reset);

            // Control para Ingresar y enviar comandos
            while (true) {
                // Leemos la entrada del comando
                String comando = scanner.nextLine();
                if (comando.compareToIgnoreCase("HELP") == 0) {
                    // Comandos
                    System.out.println(azul + "PWD" + reset + " - Mostrar directorio actual");//
                    System.out.println(azul + "LS" + reset + " - Listar archivos y carpetas");//
                    System.out.println(azul + "MKDIR" + reset + " - Crea el directorio indicado de forma remota");//
                    System.out.println(azul + "CD <directorio>" + reset + " - Cambiar de directorio"); //
                    System.out.println(azul + "GET <archivo>" + reset + " - Descargar un archivo");
                    System.out.println(azul + "MGET <archivos>" + reset + " - Descargar multiples archivos");
                    System.out.println(azul + "PUT <archivo>" + reset + " - Subir un archivo");
                    System.out.println(azul + "MPUT <archivos>" + reset + " - Subir multiples archivos");
                    System.out.println(azul + "DELETE <archivo>" + reset + " - Eliminar un archivo");//
                    System.out.println(azul + "RENAME <archivo>" + reset + " - Cambia el nombre a un archivo");
                    System.out.println(azul + "QUIT" + reset + " - Cerrar sesión");//
                }
                writer.println(comando); // Enviar comando al servidor
                writer.flush(); // Se envia de inmediato
                if (comando.compareToIgnoreCase("LS") == 0) {
                    String linea;
                    while (!(linea = reader.readLine()).equals("END_LIST")) { // Espera hasta recibir "END_LIST"
                        System.out.println(azul + linea + reset);
                    }
                } else {
                    System.out.println(azul + reader.readLine() + reset); // Obtenemos la respuesta del comando
                }

                if (comando.compareToIgnoreCase("QUIT") == 0) {
                    // Cerramos la conexion
                    System.out.println("Cerrando sesion...");
                    writer.close();
                    reader.close();
                    c1.close();
                    System.exit(0);
                }

                if(comando.toUpperCase().startsWith("PUT")){
                    String texto = comando.replaceAll("(?i)PUT ", "").trim(); // Elimina "PUT" y espacios extra
                    File f = new File(texto);
                    System.out.println(f.getAbsolutePath());
                    if (!f.isFile()) {
                        System.out.println("550 No se encontró el directorio");

                    } else{
                        System.out.println("si se ecn");
                        try{
                            int pto=20;
                            Socket c2=new Socket(dir,pto);
                            System.out.println("Conexion con el socket de archivos");
                            //JFileChooser jf=new JFileChooser();
                            //int r=jf.showOpenDialog(null);
                            //jf.setRequestFocusEnabled(true);
                            String nombre=f.getName();
                            String path=f.getAbsolutePath();
                            long tam=f.length();
                            System.out.println("Preparandose para enviar archivo"+path+" de "+tam+" bytes\n\n");
                            DataOutputStream dos=new DataOutputStream(c2.getOutputStream());
                            DataInputStream dis=new DataInputStream(new FileInputStream(path));
                            dos.writeUTF(nombre);
                            dos.flush();
                            dos.writeLong(tam);
                            dos.flush();
                            long enviados=0;
                            int l=0,porcentaje=0;
                            while(enviados<tam){
                                byte[] b=new byte[3500];
                                l=dis.read(b);
                                System.out.println("enviados: "+l);
                                dos.write(b,0,l);
                                dos.flush();
                                enviados=enviados+l;
                                porcentaje=(int)((enviados*100)/tam);
                                System.out.println("\rEnviado el "+porcentaje+"% del archivo");
                            }
                            System.out.println("\nArchivo enviado");
                            dis.close();
                            dos.close();
                            c2.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        System.out.println("terminamos");
                    }

                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
