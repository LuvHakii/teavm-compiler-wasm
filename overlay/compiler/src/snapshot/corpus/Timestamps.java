import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        File file = new File("time.txt");
        byte[] bytes = "unchanged".getBytes(StandardCharsets.UTF_8);
        Files.write(Path.of(file.getPath()), bytes);
        if (!file.setLastModified(1234567890000L) || file.lastModified() != 1234567890000L) {
            throw new AssertionError("modification time");
        }
        if (!java.util.Arrays.equals(Files.readAllBytes(Path.of(file.getPath())), bytes)) {
            throw new AssertionError("content changed");
        }
        if (new File("missing-time.txt").setLastModified(1234567890000L)) {
            throw new AssertionError("missing file created");
        }
        try {
            file.setLastModified(-1);
            throw new AssertionError("negative time accepted");
        } catch (IllegalArgumentException expected) {
        }
        file.delete();
        System.out.println("timestamps OK");
    }
}
