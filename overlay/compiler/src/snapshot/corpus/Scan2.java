import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Main {
    static void t(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            System.out.println(e.getClass().getSimpleName());
        }
    }

    public static void main(String[] a) {
        Scanner s = new Scanner("99999999999 ff -12 +7 1e3 .5 NaN abc 123456789012345678901234567890 0.10 x");
        t(() -> s.nextInt());
        System.out.println(s.nextLong());
        System.out.println(s.nextInt(16));
        System.out.println(s.nextInt());
        System.out.println(s.nextInt());
        System.out.println(s.nextDouble());
        System.out.println(s.nextDouble());
        System.out.println(s.nextDouble());
        t(() -> s.nextDouble());
        System.out.println(s.next());
        System.out.println(s.nextBigInteger());
        System.out.println(s.nextBigDecimal());
        System.out.println(s.hasNextInt() + " " + s.next() + " " + s.hasNext());
        t(() -> s.next());
        t(() -> s.nextLine());
        Scanner c = new Scanner("a b");
        c.close();
        t(() -> c.next());
        Scanner e = new Scanner("   \n  ");
        System.out.println(e.hasNext() + " " + e.hasNextLine());
        System.out.println("[" + e.nextLine() + "]");
        Scanner f = new Scanner("one two\r\nthree");
        System.out.println(f.findInLine("t(w)o") + "|" + f.nextLine() + "|" + f.nextLine() + "|" + f.hasNextLine());
    }
}
