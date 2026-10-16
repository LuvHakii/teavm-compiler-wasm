import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface Imp {
    String module();
    String name() default "n";
}

public class Main {
    @Imp(module = "m")
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void f() {}

    @Deprecated(since = "1")
    public static void main(String[] a) {
        f();
        System.out.println("annotations ok");
    }
}
