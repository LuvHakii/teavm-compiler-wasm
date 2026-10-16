import java.io.*;

public class Main {
    public static void main(String[] a) throws Exception {
        File f = new File("proof-raf.bin");
        f.delete();
        try (RandomAccessFile r = new RandomAccessFile(f, "rw")) {
            r.writeInt(42);
            r.seek(0);
            System.out.println(r.readInt());
        }
        System.out.println(f.exists() + " " + f.length());
        f.delete();
    }
}
