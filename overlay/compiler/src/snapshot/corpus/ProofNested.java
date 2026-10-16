import java.util.*;

public class Main {
    public static void main(String[] a) {
        Map<String, Integer> m = new HashMap<>();
        m.put("a", 1);
        for (Map.Entry<String, Integer> e : m.entrySet()) System.out.println(e.getKey() + e.getValue());
        AbstractMap.SimpleEntry<String, String> se = new AbstractMap.SimpleEntry<>("k", "v");
        System.out.println(se);
        Iterator<Integer> it = new ArrayList<>(List.of(1, 2)).iterator();
        while (it.hasNext()) System.out.println(it.next());
    }
}
