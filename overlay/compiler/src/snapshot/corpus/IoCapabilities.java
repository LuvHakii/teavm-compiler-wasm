import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class Main {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        File file = new File("random-new.bin");
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.writeInt(0x12345678);
            out.seek(8);
            out.writeByte(9);
        }
        RandomAccessFile read = new RandomAccessFile(file, "rw");
        check(read.length() == 9 && read.readInt() == 0x12345678, "existing file truncated");
        read.close();
        read.close();
        try {
            read.read();
            throw new AssertionError("closed file readable");
        } catch (IOException expected) {
        }
        try {
            new RandomAccessFile("missing.bin", "r");
            throw new AssertionError("read-only file created");
        } catch (FileNotFoundException expected) {
        }
        try {
            new RandomAccessFile("missing-parent/file.bin", "rw");
            throw new AssertionError("missing parent created");
        } catch (FileNotFoundException expected) {
        }
        for (int i = 0; i < 32; ++i) {
            try (RandomAccessFile opened = new RandomAccessFile(file, "rw")) {
                check(opened.readInt() == 0x12345678, "reopen");
            }
        }
        file.delete();
        String value = "雪😀".repeat(4000);
        StringWriter writer = new StringWriter();
        check(new StringReader(value).transferTo(writer) == value.length(), "character count");
        check(writer.toString().equals(value), "transfer content");
        check(new StringReader("").transferTo(new StringWriter()) == 0, "empty transfer");
        try {
            new StringReader("").transferTo(null);
            throw new AssertionError("null writer accepted");
        } catch (NullPointerException expected) {
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        check(bytes.length == 28000, "UTF-8 length: " + bytes.length);
        check(new String(bytes, StandardCharsets.UTF_8).equals(value), "UTF-8 roundtrip");
        Files.write(Path.of("text.txt"), bytes);
        check(java.util.Arrays.equals(Files.readAllBytes(Path.of("text.txt")), bytes), "file bytes");
        String actual = Files.readString(Path.of("text.txt"));
        check(actual.equals(value), "readString: expected " + value.length() + " chars, got " + actual.length());
        Files.delete(Path.of("text.txt"));
        System.out.println(new char[]{'雪', 'O', 'K'});
        System.out.println("I/O OK");
    }
}
