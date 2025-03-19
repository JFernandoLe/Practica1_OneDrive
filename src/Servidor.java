import java.io.*;
import java.net.*;
import java.sql.SQLOutput;
import java.util.ArrayList;
import java.util.List;


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
                        System.exit(0);
                    }else{
                        System.out.println("Comando recibido: ");
                        System.out.println(comando);
                        // Responder a los comandos del cliente
                        if (comando.startsWith("LS")) {

                            List<String> resultados = listFiles(f2, "");
                            for (String linea : resultados) {
                                System.out.println(linea);
                                writer.println(linea);
                                writer.flush();
                            }
                            writer.println("END_LIST"); // Indicador de fin de lista
                            writer.flush();

                        } else if (comando.startsWith("PWD")) {
                            String texto= f2.getAbsolutePath(); //Obtenemos la ruta general
                            String dirAbs=texto.replace(f.getAbsolutePath(),""); //Conservamos unicamente la direccion de la carpeta Drive
                            writer.println(dirAbs);
                            writer.flush();
                        } else if (comando.startsWith("MKDIR")) {
                            String texto = comando;
                            String directorio = texto.replace("MKDIR ", ""); // Elimina el comando para tener unicamente el nombre
                            //Creacion del directorio
                            rutaLocal=directorio+"\\";
                            String ruta_nueva=f2.getAbsolutePath()+"\\"+rutaLocal;
                            f2=new File(ruta_nueva);
                            f2.mkdirs();    //Creamos el directorio
                            f2.setWritable(true);
                            writer.println("Se creo el directorio correctamente");
                            writer.flush();
                        } else if (comando.startsWith("DELETE")) {
                            String texto = comando;
                            String directorio = texto.replace("DELETE ", ""); // Elimina el comando para tener unicamente el nombre
                            //Instanciamos la clase file con la ruta del fichero
                            File miFichero = new File(f2.getAbsolutePath() + "\\"+ directorio);
                            //Comprobamos si existe el fichero
                            if (miFichero.exists()) {
                                //Borramos el fichero
                                miFichero.delete();
                                writer.println("Se elimino correctamente");
                                writer.flush();
                            } else {
                                writer.println(miFichero.getName() + " NO existe");
                                writer.flush();
                            }
                        } else if(comando.startsWith("CD")) {
                            String direccion_actual=f2.getAbsolutePath();
                            String texto=comando.replace("CD ",""); //Para obtener unicamente lo que escribio el usuario
                            String nuevaRuta;
                            if(texto.startsWith("\\")){
                                nuevaRuta=f.getAbsolutePath()+texto;
                                //Comprobamos si esa ruta existe
                                f2=new File(nuevaRuta);
                                if(!f2.isDirectory()){
                                    System.out.println("No se encontro la direccion");
                                    writer.println("No se encontro la direccion");
                                    writer.flush();
                                    f2=new File(direccion_actual+"\\");//En caso de no encontrar la ruta especificada, volvemos a apuntar a la actual
                                }else{
                                    writer.println("Se cambio correctamente");
                                    writer.flush();
                                }
                            }else {
                                nuevaRuta = f2.getAbsolutePath() + "\\" + texto;
                                //Comprobamos si esa ruta existe
                                f2=new File(nuevaRuta);
                                if(!f2.isDirectory()){
                                    System.out.println("No se encontro la direccion");
                                    writer.println("No se encontro la direccion");
                                    writer.flush();
                                    f2=new File(direccion_actual+"\\");//En caso de no encontrar la ruta especificada, volvemos a apuntar a la actual
                                }else{
                                    writer.println("Se cambio correctamente");
                                    writer.flush();
                                }

                            }

                        } else if (comando.startsWith("QUIT")) {
                            reader.close();
                            writer.close();
                            c1.close();
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
