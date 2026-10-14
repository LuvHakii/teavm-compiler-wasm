import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Set;
import static java.nio.file.StandardOpenOption.*;

public class Main {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    interface Action { void run() throws Exception; }
    static void fails(Class<?> kind, Action action) throws Exception {
        try { action.run(); } catch (Exception e) { if (kind.isInstance(e)) return; throw e; }
        throw new AssertionError("Expected " + kind.getName());
    }
    public static void main(String[] args) throws Exception {
        check(new File("").getAbsolutePath().equals("/root"));
        check(System.getProperty("user.dir").equals("/root"));
        check(!new File("data.bin").toPath().isAbsolute());
        Path p = Path.of("data.bin");
        Files.writeString(p, "abcd");
        try (var c = FileChannel.open(p, WRITE)) {
            check(c.position() == 0);
            c.write(ByteBuffer.wrap(new byte[]{90}));
            check(Files.readString(p).equals("Zbcd"));
            c.write(ByteBuffer.wrap(new byte[]{89}), 2);
            check(c.position() == 1);
            check(Files.readString(p).equals("ZbYd"));
            c.position(4).truncate(2);
            check(c.position() == 2);
            check(Files.readString(p).equals("Zb"));
            c.position(10);
            check(c.size() == 2);
            fails(NonReadableChannelException.class, () -> c.read(ByteBuffer.allocate(1)));
            fails(IOException.class, () -> c.position(1L + Integer.MAX_VALUE));
            fails(IllegalArgumentException.class, () -> c.truncate(-1));
            c.force(true);
        }
        fails(FileAlreadyExistsException.class, () -> FileChannel.open(p, WRITE, CREATE_NEW));
        check(Files.readString(p).equals("Zb"));
        fails(NoSuchFileException.class, () -> FileChannel.open(Path.of("absent/file"), WRITE, CREATE));
        try (var c = FileChannel.open(p, APPEND)) {
            check(c.position() == 2);
            c.position(0).write(ByteBuffer.wrap(new byte[]{33}));
            check(Files.readString(p).equals("Zb!"));
        }
        try (var c = FileChannel.open(p, READ, TRUNCATE_EXISTING)) {
            check(c.size() == 3);
            check(c.read(ByteBuffer.allocate(0)) == 0);
            fails(ReadOnlyBufferException.class, () -> c.read(ByteBuffer.allocate(1).asReadOnlyBuffer()));
            check(c.position() == 0);
            byte[] bytes = new byte[5];
            var slice = ByteBuffer.wrap(bytes, 1, 3).slice();
            check(c.read(slice) == 3);
            check(bytes[0] == 0 && bytes[1] == 90 && bytes[2] == 98 && bytes[3] == 33 && bytes[4] == 0);
            check(c.read(ByteBuffer.allocate(1)) == -1);
            fails(NonWritableChannelException.class, () -> c.write(ByteBuffer.allocate(1)));
            fails(IndexOutOfBoundsException.class, () -> c.read(new ByteBuffer[1], 1, 1));
            c.force(false);
        }
        fails(IllegalArgumentException.class, () -> FileChannel.open(p, READ, APPEND));
        fails(IllegalArgumentException.class, () -> FileChannel.open(p, APPEND, TRUNCATE_EXISTING));
        fails(UnsupportedOperationException.class, () -> FileChannel.open(p, SYNC));
        fails(NullPointerException.class, () -> FileChannel.open(p, (java.nio.file.OpenOption)null));
        try (var c = FileChannel.open(p, WRITE)) {
            var direct = ByteBuffer.allocateDirect(1);
            direct.put((byte)68).flip();
            c.write(direct);
            check(direct.position() == 1);
        }
        try (var c = FileChannel.open(p)) {
            var direct = ByteBuffer.allocateDirect(3);
            check(c.read(direct) == 3);
            check(direct.get(0) == 68);
        }
        var closed = FileChannel.open(p);
        closed.close();
        fails(ClosedChannelException.class, () -> closed.position());
        try (var in = Files.newInputStream(p, READ)) { check(in.read() == 68); }
        try (var out = Files.newOutputStream(p, WRITE)) { out.write(69); }
        check(Files.readString(Path.of("./sub/../data.bin")).equals("Eb!"));
        Files.writeString(Path.of("/tmp/tmp.txt"), "tmp");
        check(Files.readString(Path.of("/tmp/tmp.txt")).equals("tmp"));
        fails(IOException.class, () -> Files.readString(Path.of("/outside/data.bin")));
        System.out.println("ok");
    }
}
