public class Main {
    public static void main(String[] args) throws Exception {
        long t0 = System.currentTimeMillis();
        System.out.println("before");
        Thread.sleep(300);
        long dt = System.currentTimeMillis() - t0;
        System.out.println("after sleep, elapsed>=300: " + (dt >= 300));
        Thread t = new Thread(() -> System.out.println("in thread"));
        t.start();
        t.join();
        System.out.println("done");
    }
}
