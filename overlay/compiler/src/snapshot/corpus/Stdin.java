public class Main {
    public static void main(String[] a) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = System.in.read()) != -1) {
            sb.append((char) c);
        }
        System.out.println("got " + sb.length() + " chars: " + sb.toString().trim());
        byte[] buf = new byte[8];
        System.out.println("after eof read=" + System.in.read(buf));
    }
}
