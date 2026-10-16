public class Main {
    public static void main(String[] args) {
        System.out.println("start");
        if (args.length == 0) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> System.out.println("hook")));
        }
        try {
            System.exit(3);
        } finally {
            System.out.println("finally must not run on a real JVM");
        }
        System.out.println("unreachable");
    }
}
