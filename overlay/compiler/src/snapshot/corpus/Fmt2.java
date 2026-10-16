public class Main {
    static long seed = 12345;

    static long next() {
        seed = seed * 6364136223846793005L + 1442695040888963407L;
        return seed >>> 17;
    }

    static double value() {
        switch ((int) (next() % 7)) {
            case 0: return (next() % 2000000) / 1000.0 - 1000;
            case 1: return (next() % 1000) / 8.0;
            case 2: return Double.longBitsToDouble(next() | 0x3000000000000000L) % 1e6;
            case 3: return (next() % 100000) / 100000.0;
            case 4: return Math.pow(10, (int) (next() % 40) - 20) * ((next() % 900) + 100) / 100.0;
            case 5: return (next() % 20000) / 2.0 + 0.005;
            default: return (next() % 1000000) / 3.0;
        }
    }

    public static void main(String[] a) {
        String[] flags = {"", "-", "+", " ", "0", ",", "(", "+,", "0+"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            double v = value();
            if (next() % 5 == 0) {
                v = -v;
            }
            int prec = (int) (next() % 9);
            int width = next() % 3 == 0 ? (int) (next() % 20) : 0;
            String flag = flags[(int) (next() % flags.length)];
            String conv = new String[] {"f", "f", "e", "E", "g", "G"}[(int) (next() % 6)];
            if (flag.contains(",") && !conv.equals("f")) {
                flag = "";
            }
            if (flag.contains("-") && width == 0) {
                width = 12;
            }
            String fmt = "%" + flag + (width > 0 ? String.valueOf(width) : "") + "." + prec + conv;
            String r;
            try {
                r = String.format(fmt, v);
            } catch (Throwable e) {
                r = e.getClass().getSimpleName();
            }
            sb.append(fmt).append(' ').append(v).append(" => [").append(r).append("]\n");
        }
        System.out.print(sb);
    }
}
