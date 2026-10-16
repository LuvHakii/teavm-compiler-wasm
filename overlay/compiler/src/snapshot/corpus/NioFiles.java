import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class Main {
    static void p(String label, Object value) {
        System.out.println(label + " = " + value);
    }

    public static void main(String[] args) throws Exception {
        Path seed = Paths.get("in.txt");
        p("exists", Files.exists(seed));
        p("isRegularFile", Files.isRegularFile(seed));
        p("size", Files.size(seed));
        List<String> lines = Files.readAllLines(seed);
        p("readAllLines", lines);
        p("readString", new String(Files.readAllBytes(seed), StandardCharsets.UTF_8).replace("\n", "|"));
        p("readAllBytes", Files.readAllBytes(seed).length);

        Path dir = Paths.get("tmpdir");
        Files.createDirectories(dir.resolve("x/y"));
        p("isDirectory", Files.isDirectory(dir.resolve("x/y")));
        Path f = dir.resolve("note.txt");
        Files.write(f, Arrays.asList("one", "two"), StandardCharsets.UTF_8);
        p("written", Files.readAllLines(f));
        Files.write(f, "replaced\n".getBytes(StandardCharsets.UTF_8));
        p("writeString", new String(Files.readAllBytes(f), StandardCharsets.UTF_8).replace("\n", "|"));
        Files.write(f, "more\n".getBytes(StandardCharsets.UTF_8), java.nio.file.StandardOpenOption.APPEND);
        p("append", new String(Files.readAllBytes(f), StandardCharsets.UTF_8).replace("\n", "|"));
        Path moved = dir.resolve("moved.txt");
        Files.move(f, moved);
        p("moved", Files.exists(f) + "/" + Files.exists(moved));
        Path copy = dir.resolve("copy.txt");
        Files.copy(moved, copy);
        p("copy", new String(Files.readAllBytes(copy), StandardCharsets.UTF_8).replace("\n", "|"));
        try (var stream = Files.list(dir)) {
            p("list", stream.map(x -> x.getFileName().toString()).sorted().toList());
        }
        p("deleteIfExists", Files.deleteIfExists(copy) + "/" + Files.deleteIfExists(copy));
        Files.delete(moved);
        Files.delete(dir.resolve("x/y"));
        Files.delete(dir.resolve("x"));
        Files.delete(dir);
        p("final", Files.exists(dir));
        p("name", Paths.get("a/b/c.txt").getFileName());
        p("parent", Paths.get("a/b/c.txt").getParent());
    }
}
