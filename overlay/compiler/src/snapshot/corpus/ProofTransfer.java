import java.io.*;

public class Main {
    public static void main(String[] a) throws Exception {
        StringWriter sw = new StringWriter();
        long n = new StringReader("hello").transferTo(sw);
        System.out.println(n + " " + sw);
    }
}
