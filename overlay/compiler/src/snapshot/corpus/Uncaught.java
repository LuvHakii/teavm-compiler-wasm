public class Main {
    static int depth(int n) {
        return depth(n + 1) + 1;
    }

    public static void main(String[] args) {
        System.out.println("start");
        if (args.length == 0) {
            throw new IllegalStateException("boom");
        }
        System.out.println(depth(0));
    }
}
