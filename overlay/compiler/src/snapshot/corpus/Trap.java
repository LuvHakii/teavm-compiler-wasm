public class Main {
    static class P implements Cloneable {
        int x = 7;
        int get() { return x; }
        public Object clone() {
            try { return super.clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
        }
    }
    public static void main(String[] a) {
        P c = (P) new P().clone();
        System.out.println(c.get());
    }
}
