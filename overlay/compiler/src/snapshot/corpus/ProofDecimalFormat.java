import java.text.DecimalFormat;

public class Main {
    public static void main(String[] a) {
        System.out.println(new DecimalFormat("0.00").format(1.5));
        System.out.println(new DecimalFormat("#,##0.###").format(1234567.891));
        System.out.println(new DecimalFormat("$#.00").format(3.14159));
    }
}
