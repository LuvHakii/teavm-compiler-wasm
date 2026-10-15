package org.teavm.javac;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ErroneousTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Scope;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.model.JavacTypes;
import com.sun.tools.javac.parser.Tokens.TokenKind;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

final class SemanticAnalysis {
    private final JavacTrees trees;
    private final JavacElements elements;
    private final JavacTypes types;
    private final Map<String, JCCompilationUnit> units = new LinkedHashMap<>();
    private final Map<String, String> texts = new LinkedHashMap<>();
    private final Map<Element, TreePath> declarations = new LinkedHashMap<>();
    private final Set<String> classNames = new LinkedHashSet<>();
    private final Set<String> packageNames = new LinkedHashSet<>();

    private enum Position { TOP_LEVEL, MEMBER, STATEMENT, EXPRESSION, TYPE_ARGUMENT }

    private static final Set<TokenKind> MODIFIERS = EnumSet.of(TokenKind.ABSTRACT, TokenKind.FINAL, TokenKind.NATIVE,
            TokenKind.PRIVATE, TokenKind.PROTECTED, TokenKind.PUBLIC, TokenKind.STATIC, TokenKind.STRICTFP,
            TokenKind.SYNCHRONIZED, TokenKind.TRANSIENT, TokenKind.VOLATILE, TokenKind.DEFAULT);
    private static final Set<TokenKind> TOP_LEVEL_MODIFIERS = EnumSet.of(TokenKind.ABSTRACT, TokenKind.FINAL,
            TokenKind.PUBLIC, TokenKind.STRICTFP);
    private static final Set<TokenKind> DECLARATIONS = EnumSet.of(TokenKind.CLASS, TokenKind.INTERFACE,
            TokenKind.ENUM);
    private static final Set<TokenKind> PRIMITIVES = EnumSet.of(TokenKind.BOOLEAN, TokenKind.BYTE, TokenKind.CHAR,
            TokenKind.DOUBLE, TokenKind.FLOAT, TokenKind.INT, TokenKind.LONG, TokenKind.SHORT, TokenKind.VOID);
    private static final Set<TokenKind> STATEMENTS = EnumSet.of(TokenKind.ASSERT, TokenKind.BREAK,
            TokenKind.CONTINUE, TokenKind.DO, TokenKind.FOR, TokenKind.IF, TokenKind.RETURN, TokenKind.SWITCH,
            TokenKind.SYNCHRONIZED, TokenKind.THROW, TokenKind.TRY, TokenKind.WHILE);
    private static final Set<TokenKind> EXPRESSIONS = EnumSet.of(TokenKind.NEW, TokenKind.THIS, TokenKind.SUPER,
            TokenKind.TRUE, TokenKind.FALSE, TokenKind.NULL, TokenKind.SWITCH);
    private static final Set<TokenKind> HEADERS = EnumSet.of(TokenKind.PACKAGE, TokenKind.IMPORT);
    private static final String[][] STATEMENT_SNIPPETS = {
        {"sout", "System.out.println", "System.out.println(${1});"},
        {"serr", "System.err.println", "System.err.println(${1});"},
        {"fori", "indexed for loop", "for (int ${1:i} = 0; ${1:i} < ${2:length}; ${1:i}++) {\n\t${3}\n}"},
        {"foreach", "enhanced for loop", "for (${1:var} ${2:item} : ${3:items}) {\n\t${4}\n}"},
        {"ifelse", "if / else", "if (${1:condition}) {\n\t${2}\n} else {\n\t${3}\n}"},
        {"trycatch", "try / catch", "try {\n\t${1}\n} catch (${2:Exception} ${3:e}) {\n\t${4}\n}"}
    };
    private static final String[][] MEMBER_SNIPPETS = {
        {"main", "public static void main", "public static void main(String[] args) {\n\t${1}\n}"}
    };
    private static final String[] MEMBER_CONTEXTUAL = {"record", "sealed"};
    private static final String[] STATEMENT_CONTEXTUAL = {"var", "yield", "record"};
    private static final String[] TOP_LEVEL_CONTEXTUAL = {"record", "sealed"};

    SemanticAnalysis(Context context, List<JCCompilationUnit> parsed, Map<String, FileData> sources,
            Map<String, FileData> classpath, Map<String, FileData> sdk) {
        trees = JavacTrees.instance(context);
        elements = JavacElements.instance(context);
        types = JavacTypes.instance(context);
        sources.forEach((name, file) -> texts.put(name, new String(file.data, StandardCharsets.UTF_8)));
        for (var map : List.of(classpath, sdk)) {
            for (String path : map.keySet()) {
                if (path.endsWith(".class")) {
                    classNames.add(path.substring(0, path.length() - 6).replace('/', '.'));
                    int separator = path.lastIndexOf('/');
                    while (separator >= 0) {
                        packageNames.add(path.substring(0, separator).replace('/', '.'));
                        separator = path.lastIndexOf('/', separator - 1);
                    }
                }
            }
        }
        for (var unit : parsed) {
            units.put(unit.sourcefile.getName(), unit);
            new TreePathScanner<Void, Void>() {
                private void record() {
                    var path = getCurrentPath();
                    var element = trees.getElement(path);
                    if (element != null) {
                        declarations.put(element, path);
                    }
                }
                @Override public Void visitVariable(VariableTree tree, Void unused) {
                    record();
                    return super.visitVariable(tree, unused);
                }
                @Override public Void visitMethod(MethodTree tree, Void unused) {
                    record();
                    return super.visitMethod(tree, unused);
                }
                @Override public Void visitClass(ClassTree tree, Void unused) {
                    record();
                    return super.visitClass(tree, unused);
                }
            }.scan(unit, null);
        }
    }

    String text(String fileName) {
        var text = texts.get(fileName);
        if (text == null) {
            throw new IllegalArgumentException("Unknown source: " + fileName);
        }
        return text;
    }

    private void validate(String fileName, int offset) {
        if (offset < 0 || offset > text(fileName).length()) {
            throw new IllegalArgumentException("Invalid UTF-16 offset: " + offset);
        }
    }

    int[] prefix(String fileName, int offset) {
        validate(fileName, offset);
        var text = text(fileName);
        int from = offset;
        int to = offset;
        while (from > 0 && Character.isJavaIdentifierPart(text.codePointBefore(from))) {
            from -= Character.charCount(text.codePointBefore(from));
        }
        while (to < text.length() && Character.isJavaIdentifierPart(text.codePointAt(to))) {
            to += Character.charCount(text.codePointAt(to));
        }
        return new int[] {from, to};
    }

    private TreePath path(String fileName, int offset) {
        validate(fileName, offset);
        var unit = units.get(fileName);
        if (unit == null) {
            return null;
        }
        var positions = trees.getSourcePositions();
        var best = new TreePath[] {new TreePath(unit)};
        var width = new long[] {Long.MAX_VALUE};
        new TreePathScanner<Void, Void>() {
            @Override public Void scan(Tree tree, Void unused) {
                if (tree == null) {
                    return null;
                }
                long from = positions.getStartPosition(unit, tree);
                long to = positions.getEndPosition(unit, tree);
                if (from >= 0 && to >= from && from <= offset && offset <= to && to - from <= width[0]) {
                    width[0] = to - from;
                    best[0] = tree == unit ? new TreePath(unit) : new TreePath(getCurrentPath(), tree);
                }
                return super.scan(tree, unused);
            }
            @Override public Void visitErroneous(ErroneousTree tree, Void unused) {
                for (var child : tree.getErrorTrees()) {
                    scan(child, unused);
                }
                return null;
            }
        }.scan(unit, null);
        return best[0];
    }

    private int[] range(TreePath path) {
        var unit = path.getCompilationUnit();
        var positions = trees.getSourcePositions();
        long from = positions.getStartPosition(unit, path.getLeaf());
        long to = positions.getEndPosition(unit, path.getLeaf());
        if (from < 0 || to < from || to > text(unit.getSourceFile().getName()).length()) {
            return null;
        }
        return new int[] {(int) from, (int) to};
    }

    EditorHover hover(String fileName, int offset) {
        var path = path(fileName, offset);
        if (path == null) {
            return null;
        }
        var element = trees.getElement(path);
        var type = trees.getTypeMirror(path);
        var range = range(path);
        if (range == null || element == null || type == null || type.getKind() == TypeKind.ERROR) {
            return null;
        }
        return new EditorHover(detail(element, type), documentation(element), docKeys(element), range[0], range[1]);
    }

    EditorLocation definition(String fileName, int offset) {
        var path = path(fileName, offset);
        if (path == null) {
            return null;
        }
        var element = trees.getElement(path);
        if (element == null) {
            return null;
        }
        var declaration = declarations.get(element);
        if (declaration == null) {
            declaration = trees.getPath(element);
        }
        if (declaration == null || !units.containsKey(declaration.getCompilationUnit().getSourceFile().getName())) {
            return null;
        }
        var range = range(declaration);
        return range == null ? null : new EditorLocation(declaration.getCompilationUnit().getSourceFile().getName(),
                range[0], range[1]);
    }

    void captureMains(Map<String, MainClass[]> result) {
        for (var entry : units.entrySet()) {
            var mains = new ArrayList<MainClass>();
            new TreePathScanner<Void, Void>() {
                @Override public Void visitMethod(MethodTree tree, Void unused) {
                    var element = trees.getElement(getCurrentPath());
                    if (element instanceof ExecutableElement method && isMain(method)) {
                        var owner = (TypeElement) method.getEnclosingElement();
                        mains.add(new MainClass(elements.getBinaryName(owner).toString(),
                                owner.getQualifiedName().toString()));
                    }
                    return super.visitMethod(tree, unused);
                }
            }.scan(entry.getValue(), null);
            mains.sort(Comparator.comparing(MainClass::getBinaryName));
            result.put(entry.getKey(), mains.toArray(MainClass[]::new));
        }
    }

    private boolean isMain(ExecutableElement method) {
        var modifiers = method.getModifiers();
        if (!method.getSimpleName().contentEquals("main") || !modifiers.contains(Modifier.PUBLIC)
                || !modifiers.contains(Modifier.STATIC) || method.getReturnType().getKind() != TypeKind.VOID
                || method.getParameters().size() != 1) {
            return false;
        }
        var parameter = method.getParameters().get(0).asType();
        return parameter instanceof ArrayType array && array.getComponentType() instanceof DeclaredType component
                && component.asElement() instanceof TypeElement type
                && type.getQualifiedName().contentEquals("java.lang.String");
    }

    static void checkLaunchable(TypeElement type) {
        for (Element current = type; current instanceof TypeElement owner; current = owner.getEnclosingElement()) {
            if (owner.getModifiers().contains(Modifier.PRIVATE) || owner.getNestingKind() == NestingKind.LOCAL
                    || owner.getNestingKind() == NestingKind.ANONYMOUS) {
                throw new IllegalArgumentException("Inaccessible or unnameable main class: " + type);
            }
        }
    }

    private String detail(Element element, TypeMirror type) {
        String owner = element.getEnclosingElement() == null ? "" : element.getEnclosingElement().toString();
        String name = (owner.isEmpty() ? "" : owner + ".") + element.getSimpleName();
        if (type instanceof ExecutableType method) {
            var parameters = method.getParameterTypes().stream().map(Object::toString)
                    .collect(java.util.stream.Collectors.joining(", "));
            return name + "(" + parameters + "): " + method.getReturnType();
        }
        return name + ": " + type;
    }

    private boolean accessible(Scope scope, Element element, DeclaredType receiver) {
        if (element instanceof TypeElement type) {
            return trees.isAccessible(scope, type);
        }
        return receiver == null || trees.isAccessible(scope, element, receiver);
    }

    private void add(Map<String, EditorCompletion> result, Scope scope, Element element, DeclaredType receiver,
            boolean staticOnly, String prefix, int from, int to) {
        String name = element.getSimpleName().toString();
        if (name.isEmpty() || !name.startsWith(prefix)
                || element.getKind() == ElementKind.CONSTRUCTOR || !accessible(scope, element, receiver)) {
            return;
        }
        if (staticOnly && !element.getModifiers().contains(Modifier.STATIC) && !element.getKind().isClass()
                && !element.getKind().isInterface()) {
            return;
        }
        TypeMirror type = element.asType();
        if (receiver != null && element.getEnclosingElement() instanceof TypeElement) {
            type = types.asMemberOf(receiver, element);
        }
        String detail = signature(element, type);
        String owner = owner(element);
        result.putIfAbsent(name + ":" + detail + ":" + owner, new EditorCompletion(name, element.getKind().name(),
                detail, owner, documentation(element), docKeys(element), from, to));
    }

    private String signature(Element element, TypeMirror type) {
        if (element instanceof PackageElement) {
            return "package";
        }
        if (element instanceof TypeElement) {
            return element.getKind().name().toLowerCase();
        }
        if (type instanceof ExecutableType executable) {
            var parameters = new ArrayList<String>();
            for (var parameter : executable.getParameterTypes()) {
                parameters.add(shorten(parameter.toString()));
            }
            return "(" + String.join(", ", parameters) + "): " + shorten(executable.getReturnType().toString());
        }
        return shorten(type.toString());
    }

    private String owner(Element element) {
        var enclosing = element.getEnclosingElement();
        if (enclosing instanceof TypeElement type) {
            return type.getQualifiedName().toString();
        }
        if (enclosing instanceof PackageElement pack) {
            return pack.getQualifiedName().toString();
        }
        return enclosing == null ? null : enclosing.getSimpleName().toString();
    }

    private String documentation(Element element) {
        var path = declarations.get(element);
        if (path == null) {
            path = trees.getPath(element);
        }
        if (path == null) {
            return null;
        }
        String doc = trees.getDocComment(path);
        return doc == null || doc.trim().isEmpty() ? null : doc;
    }

    private String docKeys(Element element) {
        var keys = new ArrayList<String>();
        addDocKey(keys, element);
        if (element.getKind() == ElementKind.METHOD && element instanceof ExecutableElement method
                && element.getEnclosingElement() instanceof TypeElement owner) {
            collectOverridden(method, owner, keys, new HashSet<>());
        }
        return keys.isEmpty() ? null : String.join("\n", keys);
    }

    private void collectOverridden(ExecutableElement method, TypeElement owner, List<String> keys,
            Set<TypeElement> visited) {
        for (var supertype : types.directSupertypes(owner.asType())) {
            if (!(supertype instanceof DeclaredType declared) || !(declared.asElement() instanceof TypeElement type)
                    || !visited.add(type)) {
                continue;
            }
            for (var member : type.getEnclosedElements()) {
                if (member instanceof ExecutableElement candidate && candidate.getKind() == ElementKind.METHOD
                        && candidate.getSimpleName().equals(method.getSimpleName())
                        && elements.overrides(method, candidate, owner)) {
                    addDocKey(keys, candidate);
                }
            }
            collectOverridden(method, type, keys, visited);
        }
    }

    private void addDocKey(List<String> keys, Element element) {
        String key = docKey(element);
        if (key != null && !keys.contains(key)) {
            keys.add(key);
        }
    }

    private String docKey(Element element) {
        if (element instanceof TypeElement type) {
            return elements.getBinaryName(type).toString();
        }
        if (!(element.getEnclosingElement() instanceof TypeElement owner)) {
            return null;
        }
        String prefix = elements.getBinaryName(owner) + "#";
        return switch (element.getKind()) {
            case FIELD, ENUM_CONSTANT -> prefix + element.getSimpleName();
            case METHOD, CONSTRUCTOR -> {
                var parameters = new ArrayList<String>();
                for (var parameter : ((ExecutableElement) element).getParameters()) {
                    parameters.add(shorten(types.erasure(parameter.asType()).toString()));
                }
                yield prefix + (element.getKind() == ElementKind.CONSTRUCTOR ? "<init>" : element.getSimpleName())
                        + "(" + String.join(",", parameters) + ")";
            }
            default -> null;
        };
    }

    static String shorten(String type) {
        var out = new StringBuilder();
        var chain = new StringBuilder();
        for (int i = 0; i <= type.length(); i++) {
            char c = i < type.length() ? type.charAt(i) : ' ';
            if (Character.isJavaIdentifierPart(c) || c == '.') {
                chain.append(c);
                continue;
            }
            if (chain.length() > 0) {
                int dot = chain.lastIndexOf(".");
                out.append(dot >= 0 && dot < chain.length() - 1 ? chain.substring(dot + 1) : chain.toString());
                chain.setLength(0);
            }
            if (i < type.length()) {
                out.append(c);
            }
        }
        return out.toString();
    }

    EditorCompletion[] complete(String fileName, int offset, String placeholder, int from, int to, String prefix) {
        var found = path(fileName, offset);
        var path = found;
        while (path != null && !(path.getLeaf() instanceof IdentifierTree identifier
                && identifier.getName().contentEquals(placeholder)) && !(path.getLeaf() instanceof MemberSelectTree select
                && select.getIdentifier().contentEquals(placeholder))) {
            path = path.getParentPath();
        }
        var result = new LinkedHashMap<String, EditorCompletion>();
        if (path == null || !insideClass(path)) {
            addKeywords(result, path == null ? positionAround(found) : positionOf(path), prefix, from, to);
            return sorted(result, placeholder);
        }
        var scope = trees.getScope(path);
        if (path.getLeaf() instanceof MemberSelectTree select) {
            var receiverPath = new TreePath(path, select.getExpression());
            var receiverElement = trees.getElement(receiverPath);
            var receiverType = trees.getTypeMirror(receiverPath);
            if (receiverType instanceof DeclaredType receiver && receiver.asElement() instanceof TypeElement type) {
                for (var member : elements.getAllMembers(type)) {
                    add(result, scope, member, receiver, receiverElement instanceof TypeElement, prefix, from, to);
                }
            } else if (receiverElement instanceof PackageElement pack) {
                for (var candidate : pack.getEnclosedElements()) {
                    add(result, scope, candidate, null, false, prefix, from, to);
                }
                addNamedCandidates(result, scope, pack.getQualifiedName().toString(), prefix, from, to);
            }
        } else {
            var method = scope.getEnclosingMethod();
            boolean staticOnly = method != null && method.getModifiers().contains(Modifier.STATIC);
            for (var enclosing = scope; enclosing != null; enclosing = enclosing.getEnclosingScope()) {
                for (var element : enclosing.getLocalElements()) {
                    boolean member = element.getEnclosingElement() instanceof TypeElement;
                    add(result, scope, element, null, staticOnly && member, prefix, from, to);
                }
            }
            var owner = scope.getEnclosingClass();
            if (owner != null && owner.asType() instanceof DeclaredType receiver) {
                for (var member : elements.getAllMembers(owner)) {
                    add(result, scope, member, receiver, staticOnly, prefix, from, to);
                }
            }
            addNamedCandidates(result, scope, "java.lang", prefix, from, to);
            var pack = path.getCompilationUnit().getPackageName();
            addNamedCandidates(result, scope, pack == null ? "" : pack.toString(), prefix, from, to);
            addKeywords(result, positionOf(path), prefix, from, to);
            addImportCandidates(result, path, prefix, from, to);
        }
        return sorted(result, placeholder);
    }

    private EditorCompletion[] sorted(Map<String, EditorCompletion> result, String placeholder) {
        result.values().removeIf(completion -> completion.getName().equals(placeholder));
        var completions = new ArrayList<>(result.values());
        completions.sort(Comparator.comparing(EditorCompletion::getName).thenComparing(EditorCompletion::getDetail));
        return completions.toArray(EditorCompletion[]::new);
    }

    private static boolean insideClass(TreePath path) {
        for (var current = path; current != null; current = current.getParentPath()) {
            if (current.getLeaf() instanceof ClassTree) {
                return true;
            }
        }
        return false;
    }

    private Position positionOf(TreePath placeholder) {
        var parent = placeholder.getParentPath();
        while (parent != null && parent.getLeaf() instanceof ErroneousTree) {
            parent = parent.getParentPath();
        }
        if (parent == null) {
            return Position.TOP_LEVEL;
        }
        var leaf = parent.getLeaf();
        if (leaf instanceof ExpressionStatementTree || leaf instanceof BlockTree) {
            return Position.STATEMENT;
        }
        if (leaf instanceof VariableTree variable) {
            if (variable.getInitializer() == placeholder.getLeaf()) {
                return Position.EXPRESSION;
            }
            var grand = parent.getParentPath();
            return grand != null && grand.getLeaf() instanceof ClassTree ? Position.MEMBER : Position.STATEMENT;
        }
        if (leaf instanceof ClassTree) {
            return Position.MEMBER;
        }
        if (leaf instanceof CompilationUnitTree) {
            return Position.TOP_LEVEL;
        }
        if (leaf instanceof ParameterizedTypeTree) {
            return Position.TYPE_ARGUMENT;
        }
        return Position.EXPRESSION;
    }

    private Position positionAround(TreePath path) {
        for (var current = path; current != null; current = current.getParentPath()) {
            var leaf = current.getLeaf();
            if (leaf instanceof BlockTree) {
                return Position.STATEMENT;
            }
            if (leaf instanceof ClassTree) {
                return Position.MEMBER;
            }
            if (leaf instanceof CompilationUnitTree) {
                return Position.TOP_LEVEL;
            }
        }
        return Position.TOP_LEVEL;
    }

    private void addKeywords(Map<String, EditorCompletion> result, Position position, String prefix, int from,
            int to) {
        var names = new ArrayList<String>();
        switch (position) {
            case TOP_LEVEL -> {
                collect(names, HEADERS);
                collect(names, TOP_LEVEL_MODIFIERS);
                collect(names, DECLARATIONS);
                names.addAll(List.of(TOP_LEVEL_CONTEXTUAL));
            }
            case MEMBER -> {
                collect(names, MODIFIERS);
                collect(names, DECLARATIONS);
                collect(names, PRIMITIVES);
                names.addAll(List.of(MEMBER_CONTEXTUAL));
            }
            case STATEMENT -> {
                collect(names, STATEMENTS);
                collect(names, EXPRESSIONS);
                collect(names, PRIMITIVES);
                collect(names, DECLARATIONS);
                names.add(TokenKind.FINAL.name);
                names.addAll(List.of(STATEMENT_CONTEXTUAL));
            }
            case EXPRESSION -> collect(names, EXPRESSIONS);
            case TYPE_ARGUMENT -> { }
        }
        for (String name : names) {
            if (name.startsWith(prefix)) {
                result.putIfAbsent(name + ":keyword", new EditorCompletion(name, "KEYWORD", "keyword", null, null, null,
                        from, to));
            }
        }
        String[][] snippets = switch (position) {
            case STATEMENT -> STATEMENT_SNIPPETS;
            case MEMBER -> MEMBER_SNIPPETS;
            default -> new String[0][];
        };
        for (var snippet : snippets) {
            if (snippet[0].startsWith(prefix)) {
                result.putIfAbsent(snippet[0] + ":snippet", new EditorCompletion(snippet[0], snippet[1], snippet[2],
                        from, to));
            }
        }
    }

    private static void collect(List<String> names, Set<TokenKind> kinds) {
        for (var kind : kinds) {
            if (!names.contains(kind.name)) {
                names.add(kind.name);
            }
        }
    }

    private static boolean isType(String kind) {
        return kind.equals("CLASS") || kind.equals("INTERFACE") || kind.equals("ENUM") || kind.equals("RECORD")
                || kind.equals("ANNOTATION_TYPE");
    }

    private void addImportCandidates(Map<String, EditorCompletion> result, TreePath path, String prefix, int from,
            int to) {
        if (prefix.isEmpty()) {
            return;
        }
        var unit = path.getCompilationUnit();
        var positions = trees.getSourcePositions();
        long end = -1;
        for (var imported : unit.getImports()) {
            end = Math.max(end, positions.getEndPosition(unit, imported));
        }
        String before;
        String after = ";";
        int insertAt;
        if (end >= 0) {
            insertAt = (int) end;
            before = "\nimport ";
        } else if (unit.getPackage() != null) {
            insertAt = (int) positions.getEndPosition(unit, unit.getPackage());
            before = "\n\nimport ";
        } else {
            insertAt = 0;
            before = "import ";
            after = ";\n\n";
        }
        if (insertAt > from) {
            return;
        }
        var taken = new HashSet<String>();
        for (var completion : result.values()) {
            if (isType(completion.getKind())) {
                taken.add(completion.getName());
            }
        }
        String currentPackage = unit.getPackageName() == null ? "" : unit.getPackageName().toString();
        for (String name : classNames) {
            int dot = name.lastIndexOf('.');
            if (dot <= 0 || name.indexOf('$') >= 0) {
                continue;
            }
            String pack = name.substring(0, dot);
            String simple = name.substring(dot + 1);
            if (!simple.startsWith(prefix) || taken.contains(simple) || pack.equals("java.lang")
                    || pack.equals(currentPackage) || pack.startsWith("jdk.") || pack.startsWith("sun.")
                    || pack.startsWith("com.sun.")) {
                continue;
            }
            var type = elements.getTypeElement(name);
            if (type == null || !type.getModifiers().contains(Modifier.PUBLIC)) {
                continue;
            }
            String kind = type.getKind().name();
            result.putIfAbsent(simple + ":import:" + name, new EditorCompletion(simple, kind, kind.toLowerCase(),
                    pack, documentation(type), docKeys(type), name, before + name + after, insertAt, from, to));
        }
    }

    private void addNamedCandidates(Map<String, EditorCompletion> result, Scope scope, String packageName,
            String prefix, int from, int to) {
        String qualifiedPrefix = packageName.isEmpty() ? "" : packageName + ".";
        for (String name : classNames) {
            if (!name.startsWith(qualifiedPrefix)) {
                continue;
            }
            String simple = name.substring(qualifiedPrefix.length());
            if (!simple.contains(".") && simple.startsWith(prefix)) {
                var type = elements.getTypeElement(name);
                if (type != null) {
                    add(result, scope, type, null, false, prefix, from, to);
                }
            }
        }
        for (String name : packageNames) {
            if (name.startsWith(qualifiedPrefix)) {
                String simple = name.substring(qualifiedPrefix.length());
                if (!simple.contains(".") && simple.startsWith(prefix)) {
                    var candidate = elements.getPackageElement(name);
                    if (candidate != null) {
                        add(result, scope, candidate, null, false, prefix, from, to);
                    }
                }
            }
        }
        for (Element declaration : declarations.keySet()) {
            if (declaration instanceof TypeElement type
                    && type.getEnclosingElement() instanceof PackageElement pack
                    && pack.getQualifiedName().contentEquals(packageName)) {
                add(result, scope, type, null, false, prefix, from, to);
            }
        }
        var pack = elements.getPackageElement(packageName);
        if (pack != null) {
            for (var candidate : pack.getEnclosedElements()) {
                add(result, scope, candidate, null, false, prefix, from, to);
            }
        }
    }
}
