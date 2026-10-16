import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.*;

public class Main {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path path = Path.of("scanner.txt");
        Files.write(path, "雪😀 42\nnext".getBytes(StandardCharsets.UTF_8));
        try (Scanner s = new Scanner(new File("scanner.txt"), StandardCharsets.UTF_8)) {
            check(s.next().equals("雪😀") && s.nextInt() == 42 && s.nextLine().equals("") && s.nextLine().equals("next"), "file constructor");
        }
        try (Scanner s = new Scanner(path, "UTF-8")) {
            check(s.next().equals("雪😀"), "path constructor");
        }
        Files.delete(path);
        try (Scanner s = new Scanner("one two\r\nthree")) {
            check(s.findInLine("t(w)o").equals("two"), "line search");
            check(s.nextLine().equals("") && s.nextLine().equals("three"), "search must leave newline");
        }
        try (Scanner s = new Scanner("a 42 43")) {
            check(s.next().equals("a"), "first token");
            check(s.hasNextInt() && s.hasNextInt() && s.nextInt() == 42 && s.nextInt() == 43, "peek cache");
        }
        try (Scanner s = new Scanner("1 2 3")) {
            check(s.hasNextInt(), "cached first");
            check(s.next().equals("1") && s.nextInt() == 2, "string next clears numeric cache");
        }
        try (Scanner s = new Scanner("10")) {
            check(s.hasNextInt(), "decimal peek");
            check(s.useRadix(16).nextInt() == 16, "radix clears peek");
        }
        try (Scanner s = new Scanner("10")) {
            check(s.hasNextInt(10) && s.nextInt(16) == 16, "explicit radix changes peek");
        }
        try (Scanner s = new Scanner("1,234 12")) {
            check(s.useLocale(Locale.US).nextInt() == 1234, "locale grouping");
            s.useRadix(16).useDelimiter(",").reset();
            check(s.radix() == 10 && s.nextInt() == 12, "reset");
        }
        try (Scanner s = new Scanner("x1 y22 z333")) {
            check(s.findAll("\\d+").map(MatchResult::group).collect(Collectors.joining(",")).equals("1,22,333"), "findAll");
        }
        try (Scanner s = new Scanner("ab")) {
            check(s.findAll("").count() == 3, "zero-length findAll");
        }
        try (Scanner s = new Scanner("a b c")) {
            try (Stream<String> tokens = s.tokens()) {
                check(tokens.collect(Collectors.joining("|")).equals("a|b|c"), "tokens");
            }
            try { s.next(); throw new AssertionError("stream close"); } catch (IllegalStateException expected) { }
        }
        try (Scanner s = new Scanner("word 4")) {
            try { s.nextInt(); throw new AssertionError("mismatch"); } catch (InputMismatchException expected) { }
            check(s.next().equals("word") && s.nextInt() == 4, "mismatch preserves token");
        }
        String longToken = "雪😀".repeat(1500);
        try (Scanner s = new Scanner(longToken + " 7")) {
            check(s.next().equals(longToken) && s.nextInt() == 7, "buffer growth");
        }
        System.out.println("Scanner capabilities OK");
    }
}
