public class Main {
    static class P implements Cloneable {
        int x = 7;
        int get() { return x; }
        final int getFinal() { return x; }
        static int st(P p) { return p.x; }
        public Object clone() {
            try { return super.clone(); } catch (CloneNotSupportedException e) { throw new AssertionError(e); }
        }
        public String toString() { return "P" + x; }
    }
    static void step(String name, java.util.function.IntSupplier s) {
        try {
            System.out.println(name + " " + s.getAsInt());
        } catch (Throwable t) {
            System.out.println(name + " threw " + t);
        }
    }
    public static void main(String[] a) throws Exception {
        P p = new P();
        step("orig virtual", p::get);
        P c = (P) p.clone();
        step("clone field", () -> c.x);
        step("clone static", () -> P.st(c));
        step("clone final", c::getFinal);
        step("clone virtual", c::get);
        step("clone hash", () -> c.hashCode() == c.hashCode() ? 1 : 0);
        step("clone toString", () -> c.toString().length());
        step("clone getClass", () -> c.getClass() == P.class ? 1 : 0);
    }
}
