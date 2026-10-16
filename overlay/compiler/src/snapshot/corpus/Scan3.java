import java.util.Scanner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] a) {
        System.out.println(new Scanner("a b  c").tokens().collect(Collectors.joining("|")));
        System.out.println(new Scanner("x1 y22 z333").findAll("\\d+").map(m -> m.group()).collect(Collectors.joining(",")));
        Scanner g = new Scanner("1,234,567 -8,000.5 +3");
        System.out.println(g.nextInt() + " " + g.nextDouble() + " " + g.nextInt());
        Scanner p = new Scanner("abc 123");
        System.out.println(p.hasNext(Pattern.compile("[a-z]+")) + " " + p.hasNext("\\d+") + " " + p.next("[a-z]+") + " " + p.hasNextInt());
        Scanner d = new Scanner("a--b-c").useDelimiter("-");
        System.out.println(d.next() + "[" + d.next() + "]" + d.next() + d.hasNext());
        Scanner r = new Scanner("1 2 3 4");
        System.out.println(r.tokens().mapToInt(Integer::parseInt).sum());
    }
}
