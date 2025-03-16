import org.apache.commons.net.ftp.FTPClient;
import java.io.IOException;

public class FTPTest {
    public static void main(String[] args) {
        FTPClient ftp = new FTPClient();

        try {
            // Intenta conectar con un servidor FTP público para pruebas
            ftp.connect("ftp.dlptest.com"); // Usa un servidor FTP público de prueba
            if (ftp.isConnected()) {
                System.out.println("✅ Conexión exitosa al servidor FTP.");
            } else {
                System.out.println("❌ No se pudo conectar.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                ftp.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
