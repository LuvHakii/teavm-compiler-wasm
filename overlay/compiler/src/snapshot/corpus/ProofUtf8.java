import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CoderResult;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

public class Main {
    public static void main(String[] a) throws Exception {
        CharsetEncoder e = StandardCharsets.UTF_8.newEncoder();
        CharBuffer in = CharBuffer.wrap("\uD83D");
        ByteBuffer out = ByteBuffer.allocate(8);
        CoderResult r = e.encode(in, out, false);
        System.out.println(r + " " + in.position() + " " + out.position());
        in = CharBuffer.wrap("😀");
        out = ByteBuffer.allocate(8);
        r = e.encode(in, out, true);
        System.out.println(r + " " + in.position() + " " + out.position());
    }
}
