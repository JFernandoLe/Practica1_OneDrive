import java.io.*;
import java.net.*;
import java.sql.SQLOutput;

public class Servidor {
    public static void main(String[] args){
        try{
            //Creamos el Socket de control
            ServerSocket s1=new ServerSocket(21); //Asignamos el puerto 21, que es el de control para FTP
            s1.setOption(StandardSocketOptions.SO_REUSEADDR,true);
            System.out.println("Servidor iniciado en el puerto "+s1.getLocalPort());
            //Creacion de la carpeta del usuario
            File f=new File("");
            String ruta=f.getAbsolutePath();
            String carpeta="drive";
            String rutaLocal=carpeta+"\\";
            String ruta_archivos=ruta+"\\"+rutaLocal;
            File f2=new File(ruta_archivos);
            f2.mkdirs();
            f2.setWritable(true);
            for(;;){
                Socket c1=s1.accept();
                System.out.println("Cliente conectado desde "+c1.getInetAddress()+":"+c1.getPort());
                BufferedReader reader=new BufferedReader(new InputStreamReader(c1.getInputStream(),"ISO-8859-1"));
                PrintWriter writer=new PrintWriter(new OutputStreamWriter(c1.getOutputStream(),"ISO-8859-1"));
                writer.println("Bienvenido a EscromDrive");
                writer.flush();
                //Creamos el Socket de datos
                ServerSocket s2=new ServerSocket(20);
                //Ciclo para recibir comandos
                while(true){
                    String comando=reader.readLine();
                    if(comando.compareToIgnoreCase("QUIT")==0){
                        System.out.println("Cliente cierra la conexion");
                        reader.close();
                        writer.close();
                        c1.close();
                        break;
                    }else{
                        System.out.println("Comando recibido: ");
                        System.out.println(comando);
                        // Responder a los comandos del cliente
                        if (comando.startsWith("LS")) {
                            //Obtenemos el Array de files
                            File [] ficheros = f2.listFiles();
                            //Iteramos sobre el Array
                            for (File fs: ficheros) {
                                if (fs.isFile()) {
                                    System.out.println(fs.getName() + " es un fichero");
                                }
                                if (fs.isDirectory()) {
                                    System.out.println(fs.getName() + " es un directorio");
                                }
                            }
                        } else if (comando.startsWith("PWD")) {
                            String texto=ruta_archivos; //Obtenemos la ruta general
                            String dirAbs=texto.replace(f.getAbsolutePath(),""); //Conservamos unicamente la direccion de la carpeta Drive
                            System.out.println(dirAbs);
                        } else if (comando.startsWith("MKDIR")) {
                            String texto = comando;
                            String directorio = texto.replace("MKDIR ", ""); // Elimina el comando para tener unicamente el nombre
                            //Creacion del directorio
                            rutaLocal=directorio+"\\";
                            ruta_archivos=ruta_archivos+rutaLocal;
                            System.out.println(ruta_archivos);
                            f2=new File(ruta_archivos);
                            f2.mkdirs();
                            f2.setWritable(true);
                        } else if (comando.startsWith("DELETE")) {
                            String texto = comando;
                            String directorio = texto.replace("DELETE ", ""); // Elimina el comando para tener unicamente el nombre
                            //Instanciamos la clase file con la ruta del fichero
                            File miFichero = new File(ruta_archivos + directorio);
                            //Comprobamos si existe el fichero
                            if (miFichero.exists()) {
                                //Borramos el fichero
                                miFichero.delete();
                            } else {
                                System.out.println(miFichero.getName() + " NO existe");
                            }
                        } else if(comando.startsWith("CD")) {
                            String texto=comando;
                            String nuevaRuta=texto.replace("CD ",""); // Elimina el comando para tener unicamente la direccion
                            ruta_archivos=f.getAbsolutePath()+nuevaRuta+"\\";
                        } else if (comando.startsWith("QUIT")) {

                            break;
                        } else {
                            System.out.println("502 Comando no implementado.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
