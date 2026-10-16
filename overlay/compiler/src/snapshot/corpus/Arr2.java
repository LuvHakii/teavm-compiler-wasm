import java.lang.reflect.Array;

public class Main {
    abstract static class A {
        abstract int f();
    }

    static class B extends A {
        int f() {
            return 1;
        }
    }

    static class C extends A {
        int f() {
            return 2;
        }
    }

    public static void main(String[] args) throws Exception {
        Object arr = Array.newInstance(A.class, 2);
        Array.set(arr, 0, new B());
        Array.set(arr, 1, new C());
        A[] typed = (A[]) arr;
        System.out.println("reflect " + typed[0].f() + typed[1].f());
        Object got = Array.get(typed, 1);
        System.out.println("get " + ((A) got).f() + " len " + Array.getLength(typed));
    }
}
