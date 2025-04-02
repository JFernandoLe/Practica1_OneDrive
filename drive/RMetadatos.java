import java.net.*;
import java.io.*;
import java.util.*;

public class RMetadatos {
    public static void main(String[] args){
        try{
            DatagramSocket s = new DatagramSocket(5555);
            System.out.println("Servidor esperando datagrama..");
            int contador = 0,contadorInterno=0; //Contabdor interno es para contabilizar los elementos dentro del mapa
            Map<Integer, byte[]> paquetesRecibidos = new TreeMap<>(); // Para almacenar los paquetes en desorden

            for(;;){
                DatagramPacket p = new DatagramPacket(new byte[65535],65535);    // Se crea un DatagramPacket con un buffer de 65535 bytes para almacenar el datagrama recibido
                s.receive(p);// Recibe un datagrama UDP y lo guarda en el buffer del DatagramPacket 'p'
                // Creamos un flujo de entrada de bytes con los datos del datagrama que recibimos.
                // El `p.getData()` devuelve el arreglo de bytes con los datos del datagrama.
                DataInputStream dis = new DataInputStream(new ByteArrayInputStream(p.getData()));
                // Leemos un entero (que está en los primeros 4 bytes del datagrama) usando el DataInputStream.
                // Este entero podría ser el número de paquete o algún otro valor relevante en el datagrama.
                int n = dis.readInt();  // Lee los primeros 4 bytes del datagrama, que corresponden al número de secuencia del paquete
            
                // Almacenamos el paquete si no llega en orden
                if (n == contador) {
                    System.out.println("Se recibió el paquete " + n + " en orden correcto");
                    int tam = dis.readInt();  // Lee los siguientes 4 bytes, que corresponden al tamaño del mensaje (cantidad de bytes del contenido)
                    byte[] b = new byte[tam];
                    dis.read(b);
                    String cadena = new String(b);
                    System.out.println("Paquete recibido con los datos: #paquete->" + n + " con " + tam + " bytes y el mensaje: " + cadena);
                    contador++;
                    // Vemos si el siguiente paquete está en el mapa
                    if(paquetesRecibidos.containsKey(contador)){
                    contadorInterno=contador; // Si está, lo procesamos
                      while (paquetesRecibidos.containsKey(contadorInterno)) { // Procesamos todos los paquetes que estén en el mapa
                        byte[] b1 = paquetesRecibidos.get(contadorInterno); // Obtenemos el paquete
                        String cadena1 = new String(b1); // Convertimos el paquete a cadena
                        int tam1 = b1.length; // Obtenemos el tamaño del paquete
                        System.out.println("Paquete recuperado con los datos: #paquete->" + contadorInterno + " con " + tam1 + " bytes y el mensaje: " + cadena1);
                        contadorInterno++;
                    }
                    contador=contadorInterno;
                    }
                } else {
                    System.out.println("Se recibió el paquete " + n + " fuera de orden");
                    // Almacenamos el paquete para procesarlo más tarde
                    int tam = dis.readInt();
                    byte[] b = new byte[tam];
                    dis.read(b);
                    paquetesRecibidos.put(n, b);
                }
                dis.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
