import javax.swing.*;
import java.io.File;

public class FTPTest {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFileChooser jf = new JFileChooser();
                int r = jf.showOpenDialog(null);
                if (r == JFileChooser.APPROVE_OPTION) {
                    File f = jf.getSelectedFile();
                    System.out.println("Archivo seleccionado: " + f.getAbsolutePath());
                }
            }
        });
    }
}
