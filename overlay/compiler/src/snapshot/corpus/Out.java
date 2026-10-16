import java.io.PrintStream;

public class Main {
    public static void main(String[] a) throws Exception {
        System.out.println("héllo ✓ 😀");
        System.out.print("no newline|");
        System.out.print('c');
        System.out.print(42);
        System.out.print(1.5);
        System.out.print(true);
        System.out.println();
        System.out.printf("%d %s\n", 7, "x");
        System.err.println("to stderr é");
        System.out.write("raw bytes é\n".getBytes("UTF-8"));
        System.out.write(0xC3);
        System.out.write(0xA9);
        System.out.write(10);
        System.out.flush();
        PrintStream p = new PrintStream(System.out, true, "UTF-8");
        p.println("via wrapper ✓");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            sb.append((char) ('a' + i % 26));
        }
        System.out.println(sb.length() + " " + sb.substring(0, 10));
        System.out.println(sb);
        System.out.println((Object) null);
        System.out.println((String) null);
        System.out.println(1L << 40);
        System.out.println(-0.5f);
        System.out.println('x');
    }
}
