import java.text.*;
import java.util.*;

public class Main {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormatSymbols copy = (DecimalFormatSymbols) symbols.clone();
        check(copy.getDecimalSeparator() == '.' && copy.getGroupingSeparator() == ',', "symbols");
        DecimalFormat format = new DecimalFormat("#,##0.00", symbols);
        check(format.format(1234.5).equals("1,234.50"), "format");
        check(format.parse("1,234.50").doubleValue() == 1234.5, "parse");
        check(format.getNegativeSuffix().equals(format.getPositiveSuffix()), "inherited negative suffix");
        try (Scanner scanner = new Scanner("1,234 -2,345 1,234.50 -0.25 NaN Infinity").useLocale(Locale.US)) {
            check(scanner.nextInt() == 1234, "grouped int");
            check(scanner.nextInt() == -2345, "negative int");
            check(scanner.nextDouble() == 1234.5, "grouped double");
            check(scanner.nextDouble() == -0.25, "negative double");
            check(Double.isNaN(scanner.nextDouble()), "NaN");
            check(scanner.nextDouble() == Double.POSITIVE_INFINITY, "Infinity");
        }
        ThreadLocal<String> local = ThreadLocal.withInitial(() -> "value");
        check(local.get().equals("value"), "ThreadLocal initial value");
        local.set("changed");
        check(local.get().equals("changed"), "ThreadLocal set");
        local.remove();
        check(local.get().equals("value"), "ThreadLocal remove");
        boolean[] isolated = {false};
        local.set("main");
        Thread child = new Thread(() -> {
            boolean initial = local.get().equals("value");
            local.set("child");
            isolated[0] = initial && local.get().equals("child");
            local.remove();
        });
        child.start();
        child.join();
        check(isolated[0] && local.get().equals("main"), "ThreadLocal isolation");
        System.out.println("locale and ThreadLocal OK");
    }
}
