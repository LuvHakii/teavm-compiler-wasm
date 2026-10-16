import java.math.BigDecimal;
import java.util.Locale;

public class Main {
    static void t(String fmt, Object... args) {
        String r;
        try {
            r = "[" + String.format(fmt, args) + "]";
        } catch (Throwable e) {
            r = e.getClass().getSimpleName();
        }
        System.out.println(fmt + " => " + r);
    }

    public static void main(String[] a) {
        t("%d%% %s", 5, "x");
        t("%n");
        t("a%nb");
        t("%d%n", 7);
        t("%5d|%-5d|%05d|%+d|% d|%,d|%(d", 42, 42, 42, 42, 42, 1234567, -5);
        t("%x %X %o %#x %08X", 255, 255, 8, 255, 255);
        t("%c %C %b %B %s %S %10s|%-10s|%.3s", 'a', 'b', true, false, "str", "up", "r", "l", "truncate");
        t("%h", "hi");
        t("%f", 3.14159);
        t("%.2f", 3.14159);
        t("%.0f", 2.5);
        t("%.0f", 0.5);
        t("%.0f", 1.5);
        t("%.1f", 0.25);
        t("%.2f", 0.125);
        t("%.2f", 1.005);
        t("%.2f", 2.675);
        t("%.1f", 0.05);
        t("%.3f", 2.0005);
        t("%10.2f|%-10.2f|%010.2f|%+.2f|% .2f|%(.2f", 3.14159, 3.14159, 3.14159, 3.14159, 3.14159, -3.14159);
        t("%,.2f", 1234567.891);
        t("%,.2f", -1234567.891);
        t("%.2f", -0.001);
        t("%.2f", 0.0);
        t("%f", 1e10);
        t("%.2f", 1e-10);
        t("%.2f", 123456789012345.678);
        t("%f", Double.NaN);
        t("%f", Double.POSITIVE_INFINITY);
        t("%f", Double.NEGATIVE_INFINITY);
        t("%.2f", 3.14159f);
        t("%.3f", new BigDecimal("2.71828"));
        t("%f", 5);
        t("%e", 12345.6789);
        t("%.2e", 12345.6789);
        t("%E", 0.000123);
        t("%.0e", 5.5);
        t("%e", 0.0);
        t("%e", -1.5e100);
        t("%12.3e|%-12.3e|%012.3e", 12345.6789, 12345.6789, 12345.6789);
        t("%g", 12345.6789);
        t("%g", 0.0001234);
        t("%g", 0.00001234);
        t("%.3g", 1234567.0);
        t("%G", 1e-10);
        t("%s %s", 1, 2.5);
        t("%s", (Object) null);
        t("%2$s %1$s", "a", "b");
        t("%s %<s", "r");
        t("%d", "notint");
        t("%q", 1);
        t("%d");
        t("%10.4f|", Math.PI);
        t("%-8.1f|", 2.25);
        t("%5s|%-5s|", "abcdefg", "ab");
        t("%08.3f", -3.14159);
        t("%+08.3f", 3.14159);
        t("%.15f", 0.1);
        t("%.20f", 0.1);
        t("%.1f", 9.95);
        t("%.1f", 99.95);
        t("%.0f", 999.5);
        t("%.0f", 1e15);
        t("%s", 1.0f);
        t("%b", (Object) null);
        t("%c", 65);
        t("%x", -1);
        t("%x", 255L);
        t("%d", Long.MIN_VALUE);
    }
}
