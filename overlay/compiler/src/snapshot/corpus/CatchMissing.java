import java.util.IllegalFormatException;
import java.util.MissingFormatArgumentException;

public class Main {
    public static void main(String[] a) {
        try {
            String.format("%d %d", 1);
        } catch (MissingFormatArgumentException e) {
            System.out.println("missing " + e.getFormatSpecifier());
        }
        try {
            String.format("%d");
        } catch (IllegalFormatException e) {
            System.out.println("illegal " + e.getMessage());
        }
    }
}
