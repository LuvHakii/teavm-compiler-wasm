import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Scanner;

public class Main {
    static void p(String label, Object value) {
        System.out.println(label + " = " + value);
    }

    static void step(String label, Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            System.out.println(label + " threw " + e.getClass().getSimpleName());
        }
    }

    static String slurp(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                sb.append((char) c);
            }
            return sb.toString();
        }
    }

    public static void main(String[] args) throws Exception {
        File seed = new File("in.txt");
        p("seed exists/file/dir", seed.exists() + "/" + seed.isFile() + "/" + seed.isDirectory());
        p("seed length", seed.length());
        p("seed name", seed.getName());
        p("seed content", slurp(seed).replace("\n", "|"));
        p("missing exists", new File("nope.txt").exists());
        p("missing length", new File("nope.txt").length());

        File dir = new File("tmpdir");
        p("mkdir", dir.mkdir());
        p("mkdir again", dir.mkdir());
        p("isDirectory", dir.isDirectory());
        p("mkdirs nested", new File(dir, "a/b/c").mkdirs());
        File f = new File(dir, "one.txt");
        p("createNewFile", f.createNewFile());
        p("createNewFile again", f.createNewFile());
        p("empty length", f.length());

        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("hello".getBytes("UTF-8"));
            out.write(' ');
            out.write("wörld\n".getBytes("UTF-8"));
        }
        p("after write", slurp(f).length());
        try (FileOutputStream out = new FileOutputStream(f, true)) {
            out.write("appended\n".getBytes("UTF-8"));
        }
        p("after append length", f.length());
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write("truncated\n".getBytes("UTF-8"));
        }
        p("after truncate", slurp(f).replace("\n", "|"));

        try (FileInputStream in = new FileInputStream(f)) {
            p("available", in.available());
            p("skip", in.skip(3));
            p("read after skip", (char) in.read());
            byte[] rest = new byte[100];
            p("read rest", in.read(rest, 0, 100));
            p("read eof", in.read());
        }

        File text = new File(dir, "text.txt");
        try (PrintWriter w = new PrintWriter(new FileWriter(text))) {
            w.println("alpha");
            w.println("beta");
            w.printf("%d %s%n", 3, "gamma");
        }
        try (BufferedReader r = new BufferedReader(new FileReader(text))) {
            String line;
            while ((line = r.readLine()) != null) {
                p("line", line);
            }
        }
        try (Scanner s = new Scanner(text)) {
            p("scanner next", s.next());
            p("scanner rest", s.nextLine().length() + "/" + s.nextLine() + "/" + s.nextInt());
        }

        String[] names = dir.list();
        Arrays.sort(names);
        p("list", Arrays.toString(names));
        File[] files = dir.listFiles();
        p("listFiles", files.length);
        p("list of file", f.list());

        new File(dir, "raf.bin").createNewFile(); // TeaVM's "rw" does not create a missing file
        try (RandomAccessFile raf = new RandomAccessFile(new File(dir, "raf.bin"), "rw")) {
            raf.write(new byte[] {1, 2, 3, 4, 5, 6});
            p("raf length", raf.length());
            raf.seek(2);
            p("raf read", raf.read());
            p("raf pointer", raf.getFilePointer());
            raf.seek(0);
            raf.writeInt(0x01020304);
            raf.seek(0);
            p("raf readInt", raf.readInt());
            raf.setLength(3);
            p("raf length after set", raf.length());
        }

        File renamed = new File(dir, "renamed.txt");
        p("renameTo", f.renameTo(renamed));
        p("old gone", f.exists());
        p("renamed content", slurp(renamed).replace("\n", "|"));
        p("rename missing", new File(dir, "zzz").renameTo(new File(dir, "yyy")));

        p("delete file", renamed.delete());
        p("delete again", renamed.delete());
        p("delete non-empty dir", new File(dir, "a").delete());
        p("delete empty dir c", new File(dir, "a/b/c").delete());
        p("delete empty dir b", new File(dir, "a/b").delete());
        p("delete empty dir a", new File(dir, "a").delete());

        step("FileInputStream missing", () -> {
            try {
                new FileInputStream("missing.txt");
            } catch (java.io.FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });
        step("FileOutputStream in missing dir", () -> {
            try {
                new FileOutputStream("nodir/x.txt");
            } catch (java.io.FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        });

        p("abs", new File("rel/x.txt").getAbsolutePath().endsWith("/rel/x.txt"));
        p("parent", new File("rel/x.txt").getParent());
        p("path", new File("rel", "x.txt").getPath());

        for (String n : dir.list()) {
            new File(dir, n).delete();
        }
        p("cleanup", dir.delete());
        p("final exists", dir.exists());
    }
}
