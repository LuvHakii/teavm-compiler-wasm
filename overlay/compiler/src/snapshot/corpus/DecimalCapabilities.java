public class Main {
    private static void render(long bits) {
        double value = Double.longBitsToDouble(bits);
        String text = Double.toString(value);
        if (!new StringBuilder().append(value).toString().equals(text) || !("" + value).equals(text)) {
            throw new AssertionError("inconsistent decimal formatting");
        }
        System.out.println(text);
    }

    public static void main(String[] args) {
        if (Double.doubleToRawLongBits(Double.MIN_NORMAL) != 0x0010000000000000L) {
            throw new AssertionError("MIN_NORMAL");
        }
        long[] boundaries = {0L, 0x8000000000000000L, 1L, 2L, 20L, 0x0010000000000000L,
                0x7fefffffffffffffL, 0x7ff0000000000000L, 0xfff0000000000000L, 0x7ff8000000000000L,
                0x44b52d02c7e14af6L, 0x416312d000000000L};
        for (long bits : boundaries) render(bits);
        long state = 0x123456789abcdefL;
        for (int i = 0; i < 1000; ++i) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            render(state);
        }
    }
}
