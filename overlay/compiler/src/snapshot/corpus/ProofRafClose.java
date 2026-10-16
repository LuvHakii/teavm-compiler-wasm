import java.io.*;

public class Main {
    public static void main(String[] a) throws Exception {
        for (int i = 0; i < 5; i++) {
            try (RandomAccessFile r = new RandomAccessFile("in.txt", "r")) {
                r.read();
            }
        }
        System.out.println("done");
    }
}
