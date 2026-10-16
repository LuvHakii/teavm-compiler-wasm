import java.util.Map;

public class Main {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        check(args.length == 3, "argument count");
        check(args[0].equals("hello world") && args[1].equals("雪😀") && args[2].isEmpty(), "arguments");
        check("雪😀=value".equals(System.getenv("TEAVM_TEST")), "environment value");
        check("".equals(System.getenv("TEAVM_EMPTY")), "empty environment value");
        check(System.getenv("TEAVM_MISSING") == null, "missing environment value");
        Map<String, String> env = System.getenv();
        check(env.get("TEAVM_TEST").equals(System.getenv("TEAVM_TEST")), "environment map");
        try {
            env.put("TEAVM_TEST", "changed");
            throw new AssertionError("mutable environment");
        } catch (UnsupportedOperationException expected) {
        }
        try {
            System.getenv(null);
            throw new AssertionError("null environment name");
        } catch (NullPointerException expected) {
        }
        long wall = System.currentTimeMillis();
        long monotonic = System.nanoTime();
        Thread.sleep(25);
        check(wall > 1700000000000L, "epoch milliseconds");
        check(System.currentTimeMillis() >= wall, "wall clock progress");
        check(System.nanoTime() - monotonic >= 1000000L, "monotonic nanoseconds");
        System.out.println("runtime OK");
    }
}
