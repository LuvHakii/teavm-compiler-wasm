import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

public class DocIndex {
    private static final Set<String> KEPT_TAGS = Set.of("param", "return", "throws", "exception", "deprecated");

    public static void main(String[] args) throws Exception {
        Path sourceZip = Path.of(args[0]);
        Path output = Path.of(args[1]);
        List<JavaFileObject> sources = new ArrayList<>();
        try (var zip = new ZipFile(sourceZip.toFile())) {
            var entries = Collections.list(zip.entries());
            for (ZipEntry entry : entries) {
                String name = entry.getName();
                if (!name.startsWith("java.base/java/") && !name.startsWith("java.base/javax/")) {
                    continue;
                }
                if (!name.endsWith(".java") || name.endsWith("module-info.java") || name.endsWith("package-info.java")) {
                    continue;
                }
                String text = new String(zip.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
                sources.add(new SimpleJavaFileObject(java.net.URI.create("mem:///" + name), JavaFileObject.Kind.SOURCE) {
                    @Override public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return text;
                    }
                });
            }
        }
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = javac.getStandardFileManager(null, null, null);
        var task = (JavacTask) javac.getTask(null, fileManager, diagnostic -> {}, List.of("-proc:none"), null, sources);
        var trees = DocTrees.instance(task);
        Map<String, String> docs = new TreeMap<>();
        List<String> duplicates = new ArrayList<>();
        for (var unit : task.parse()) {
            String pack = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
            new TreePathScanner<Void, Void>() {
                private final Deque<String> owners = new ArrayDeque<>();
                private final Deque<Map<String, String>> variables = new ArrayDeque<>();
                private final Deque<Boolean> interfaces = new ArrayDeque<>();

                private String owner() {
                    var parts = new ArrayList<>(owners);
                    Collections.reverse(parts);
                    return (pack.isEmpty() ? "" : pack + ".") + String.join("$", parts);
                }

                private Map<String, String> bounds(List<? extends TypeParameterTree> parameters) {
                    var result = new HashMap<String, String>();
                    for (var parameter : parameters) {
                        var bounds = parameter.getBounds();
                        result.put(parameter.getName().toString(),
                                bounds.isEmpty() ? "Object" : simple(bounds.get(0).toString()));
                    }
                    return result;
                }

                private String resolve(String type) {
                    String base = type.replace("[]", "");
                    String dimensions = type.substring(base.length());
                    for (var scope : variables) {
                        if (scope.containsKey(base)) {
                            return scope.get(base) + dimensions;
                        }
                    }
                    return type;
                }

                private boolean visible(java.util.Set<Modifier> modifiers) {
                    return (!interfaces.isEmpty() && interfaces.peek()) || modifiers.contains(Modifier.PUBLIC)
                            || modifiers.contains(Modifier.PROTECTED);
                }

                private void record(String key) {
                    String doc = trees.getDocComment(getCurrentPath());
                    if (doc != null && !doc.isBlank()) {
                        String slim = slim(doc);
                        if (!slim.isBlank() && !slim.strip().equals("{@inheritDoc}")) {
                            if (docs.put(key, slim) != null) {
                                duplicates.add(key);
                            }
                        }
                    }
                }

                @Override public Void visitClass(ClassTree tree, Void unused) {
                    boolean isInterface = tree.getKind() == Tree.Kind.INTERFACE
                            || tree.getKind() == Tree.Kind.ANNOTATION_TYPE;
                    boolean outerInterface = !interfaces.isEmpty() && interfaces.peek();
                    boolean exposed = outerInterface || tree.getModifiers().getFlags().contains(Modifier.PUBLIC)
                            || tree.getModifiers().getFlags().contains(Modifier.PROTECTED);
                    if (tree.getModifiers().getFlags().contains(Modifier.PRIVATE)) {
                        return null;
                    }
                    owners.push(tree.getSimpleName().toString());
                    variables.push(bounds(tree.getTypeParameters()));
                    interfaces.push(isInterface);
                    if (exposed) {
                        record(owner());
                    }
                    super.visitClass(tree, unused);
                    interfaces.pop();
                    variables.pop();
                    owners.pop();
                    return null;
                }

                @Override public Void visitMethod(MethodTree tree, Void unused) {
                    if (!visible(tree.getModifiers().getFlags())) {
                        return null;
                    }
                    variables.push(bounds(tree.getTypeParameters()));
                    String parameters = tree.getParameters().stream()
                            .map(parameter -> resolve(simple(parameter.getType().toString())))
                            .collect(Collectors.joining(","));
                    String name = tree.getName().contentEquals("<init>") ? "<init>" : tree.getName().toString();
                    record(owner() + "#" + name + "(" + parameters + ")");
                    variables.pop();
                    return null;
                }

                @Override public Void visitVariable(VariableTree tree, Void unused) {
                    if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree
                            && visible(tree.getModifiers().getFlags())) {
                        record(owner() + "#" + tree.getName());
                    }
                    return null;
                }
            }.scan(unit, null);
        }
        var json = new StringBuilder("{");
        for (var entry : docs.entrySet()) {
            if (json.length() > 1) {
                json.append(',');
            }
            json.append('"').append(escape(entry.getKey())).append("\":\"").append(escape(entry.getValue()))
                    .append('"');
        }
        json.append('}');
        Files.createDirectories(output.getParent());
        Files.writeString(output, json, StandardCharsets.UTF_8);
        System.out.println("javadoc entries: " + docs.size() + ", bytes: " + json.length() + ", colliding keys: "
                + duplicates.size());
        duplicates.stream().limit(20).forEach(key -> System.out.println("  collision " + key));
    }

    static String simple(String type) {
        String cleaned = type.replace("...", "[]").replace("final ", "");
        var out = new StringBuilder();
        int depth = 0;
        boolean annotation = false;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (c == '@') {
                annotation = true;
                continue;
            }
            if (annotation) {
                if (Character.isWhitespace(c)) {
                    annotation = false;
                }
                continue;
            }
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0 && !Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        String base = out.toString();
        String dimensions = "";
        while (base.endsWith("[]")) {
            dimensions += "[]";
            base = base.substring(0, base.length() - 2);
        }
        int dot = Math.max(base.lastIndexOf('.'), base.lastIndexOf('$'));
        return (dot >= 0 ? base.substring(dot + 1) : base) + dimensions;
    }

    static String slim(String doc) {
        var kept = new StringBuilder();
        boolean keep = true;
        for (String line : doc.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("@")) {
                int end = 1;
                while (end < trimmed.length() && Character.isLetter(trimmed.charAt(end))) {
                    end++;
                }
                keep = KEPT_TAGS.contains(trimmed.substring(1, end));
            }
            if (keep) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString().strip();
    }

    static String escape(String text) {
        var out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
