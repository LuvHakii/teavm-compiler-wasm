import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

const out = pathToFileURL(resolve(process.argv[2]) + "/");
const { load } = await import(new URL("compiler.wasm-runtime.js", out).href);
const app = await load(new Uint8Array(await readFile(new URL("compiler.wasm", out))));
const compiler = app.exports.createCompiler();
compiler.setSdk(new Int8Array(await readFile(new URL("compile-classlib-teavm.bin", out))));
compiler.setTeaVMClasslib(new Int8Array(await readFile(new URL("runtime-classlib-teavm.bin", out))));
let diagnostics = [];
compiler.onDiagnostic(diagnostic => diagnostics.push({
    fileName: diagnostic.fileName,
    start: diagnostic.startPosition,
    end: diagnostic.endPosition,
    severity: diagnostic.severity,
    message: diagnostic.message
}));

const util = `package pkg;
public class Util extends Base<String> {
    private int hidden;
    public String add(int value) { return "result" + value; }
}
class Base<T> { public T inherited(T value) { return value; } }
`;
const main = `import pkg.Util;
public class Main {
    public static void main(String[] args) {
        String marker = "雪😀";
        Util util = new Util();
        String result = util.add(1);
        System.out.println(result);
    }
}
`;
compiler.addSourceFile("Main.java", main);
compiler.addSourceFile("pkg/Util.java", util.replace("String add", "int add").replace('"result" + value', "value"));
assert.equal(compiler.analyze(), false);
const error = diagnostics.find(d => d.fileName === "Main.java" && d.severity === "error");
assert.equal(error.start, main.indexOf("util.add(1)"));
assert.equal(error.end, main.indexOf("util.add(1)") + "util.add(1)".length);
compiler.addSourceFile("pkg/Util.java", util);
diagnostics = [];
assert.equal(compiler.analyze(), true);
assert.equal(diagnostics.filter(d => d.severity === "error").length, 0);
assert.deepEqual(Array.from(compiler.listOutputFiles()), []);
assert.deepEqual(Array.from(compiler.listWebAssemblyOutputFiles()), []);
const methodOffset = main.indexOf("util.add") + 6;
assert.match(compiler.hover("Main.java", methodOffset).detail, /pkg\.Util\.add: \(int\)java\.lang\.String/);
const declaration = compiler.definition("Main.java", methodOffset);
assert.equal(declaration.fileName, "pkg/Util.java");
assert.match(util.slice(declaration.from, declaration.to), /String add\(int value\)/);
assert.equal(compiler.definition("Main.java", main.indexOf("println") + 2), null);
assert.throws(() => compiler.hover("Main.java", -1));
assert.throws(() => compiler.hover("Missing.java", 0));

for (const prefix of ["", "ad"]) {
    const draft = main.replace("util.add(1)", `util.${prefix}(1)`);
    compiler.addSourceFile("Main.java", draft);
    compiler.analyze();
    const before = diagnostics.length;
    const from = draft.indexOf(`util.${prefix}(1)`) + 5;
    const completion = Array.from(compiler.complete("Main.java", from + prefix.length));
    const add = completion.find(item => item.name === "add");
    assert.equal(add.from, from);
    assert.equal(add.to, from + prefix.length);
    assert.equal(add.kind, "METHOD");
    assert(!completion.some(item => item.name === "hidden"));
    if (!prefix) {
        assert.match(completion.find(item => item.name === "inherited").detail, /\(String\): String/);
    }
    assert.equal(diagnostics.length, before);
    const afterToken = draft.indexOf("result);" ) + 1;
    const definition = compiler.definition("Main.java", afterToken);
    assert.equal(definition.fileName, "Main.java");
    assert.equal(definition.from, draft.indexOf("String result"));
    assert.match(compiler.hover("Main.java", afterToken).detail, /result: java.lang.String/);
    assert.equal(compiler.mainClassesIn("Main.java")[0].binaryName, "Main");
}

function completeAt(text, token) {
    compiler.clearSourceFiles();
    compiler.clearOutputFiles();
    compiler.addSourceFile("Main.java", text);
    compiler.analyze();
    return Array.from(compiler.complete("Main.java", text.lastIndexOf(token) + token.length));
}
assert(completeAt("class Main { void f() { java.util.Arr value; } }", "java.util.Arr").some(c => c.name === "ArrayList"));
assert(completeAt("class Main { void f() { java.ut value; } }", "java.ut").some(c => c.name === "util"));
assert(completeAt("class Main { void f() { java.util.Arr; } }", "java.util.Arr").some(c => c.name === "ArrayList"));
const staticMembers = completeAt("class Main { int instanceOnly; static int $allowed; static void f() { int value = $a; } }", "$a");
assert(staticMembers.some(c => c.name === "$allowed"));
assert(!staticMembers.some(c => c.name === "instanceOnly"));
assert(completeAt("class Main { static void f() { Str value; } }", "Str").some(c => c.name === "String"));

const keyword = (items, name) => items.find(c => c.name === name && c.kind === "KEYWORD");
const statements = completeAt("class Main { void f() { whi } }", "whi");
assert(keyword(statements, "while"));
assert(!keyword(statements, "public"));
assert(keyword(completeAt("class Main { void f() { va } }", "va"), "var"));
assert(keyword(completeAt("class Main { void f() { int x = nu } }", "nu"), "null"));
assert(!keyword(completeAt("class Main { void f() { int x = whi } }", "whi"), "while"));
assert(keyword(completeAt("class Main { pub }", "pub"), "public"));
assert(keyword(completeAt("class Main { st }", "st"), "static"));
assert(keyword(completeAt("class Main { priv }", "priv"), "private"));
assert(keyword(completeAt("pa", "pa"), "package"));
assert(keyword(completeAt("import java.util.List;\n\npub", "pub"), "public"));
assert(!completeAt("class Main { void f() { String s = \"\"; s.whi } }", "s.whi").some(c => c.kind === "KEYWORD"));

assert(!completeAt("class Main { java.util.List<ne> x; }", "ne").some(c => c.kind === "KEYWORD"));
assert(!completeAt("class Main { void f() { java.util.List<nu> x; } }", "nu").some(c => c.kind === "KEYWORD"));
const snippet = (items, name) => items.find(c => c.name === name && c.kind === "SNIPPET");
assert.match(snippet(completeAt("class Main { void f() { sou } }", "sou"), "sout").snippet, /^System\.out\.println\(\$\{1\}\);$/);
assert(snippet(completeAt("class Main { void f() { for } }", "for"), "foreach"));
assert(!snippet(completeAt("class Main { void f() { int x = sou } }", "sou"), "sout"));
assert.match(snippet(completeAt("class Main { ma }", "ma"), "main").snippet, /^public static void main\(String\[\] args\) \{/);
assert(!snippet(completeAt("class Main { void f() { ma } }", "ma"), "main"));

const bare = completeAt("class Main { void f() { Object o = new ArrayL } }", "ArrayL").find(c => c.importName === "java.util.ArrayList");
assert.equal(bare.name, "ArrayList");
assert.equal(bare.importAt, 0);
assert.equal(bare.importText, "import java.util.ArrayList;\n\n");
const withImports = "package p;\n\nimport java.util.List;\n\nclass Main { void f() { Object o = new ArrayL } }";
const afterImport = completeAt(withImports, "ArrayL").find(c => c.importName === "java.util.ArrayList");
assert.equal(afterImport.importAt, withImports.indexOf("List;") + 5);
assert.equal(afterImport.importText, "\nimport java.util.ArrayList;");
const afterPackage = completeAt("package p;\n\nclass Main { void f() { Object o = new ArrayL } }", "ArrayL").find(c => c.importName);
assert.equal(afterPackage.importAt, "package p;".length);
assert.equal(afterPackage.importText, "\n\nimport java.util.ArrayList;");
const imported = completeAt("import java.util.List;\n\nclass Main { void f() { Lis x; } }", "Lis").filter(c => c.name === "List");
assert.equal(imported.length, 1);
assert.equal(imported[0].importName, null);
assert(!completeAt("class Main { void f() { StringB x; } }", "StringB").some(c => c.importName));
assert(!completeAt("class Main { void f() { ArrayList x; } }", "").some(c => c.importName));

const docs = "class Doc {\n    /** Adds one.\n     * @param n the number */\n    static int plus(int n) { return n + 1; }\n    int g() { return plus(1) + \"x\".length(); }\n}\n";
compiler.clearSourceFiles();
compiler.addSourceFile("Doc.java", docs);
compiler.analyze();
const plus = compiler.hover("Doc.java", docs.lastIndexOf("plus") + 1);
assert.match(plus.documentation, /Adds one\.[\s\S]*@param n the number/);
assert.equal(plus.docKeys, "Doc#plus(int)");
const length = compiler.hover("Doc.java", docs.indexOf("length") + 1);
assert.equal(length.documentation, null);
assert.equal(length.docKeys.split("\n")[0], "java.lang.String#length()");
assert(length.docKeys.split("\n").includes("java.lang.CharSequence#length()"));
const printed = completeAt("class Main { void f() { System.out.pri } }", "System.out.pri").filter(c => c.name === "println");
assert(printed.every(c => c.owner === "java.io.PrintStream"));
assert(printed.some(c => c.docKeys.split("\n")[0] === "java.io.PrintStream#println(String)"));
assert(printed.some(c => c.detail === "(String): void"));
const append = completeAt("class Main { void f() { new StringBuilder().app } }", "new StringBuilder().app").find(c => c.name === "append" && c.detail === "(String): StringBuilder");
assert(append.docKeys.split("\n").some(k => k.endsWith("#append(String)") && k !== "java.lang.StringBuilder#append(String)"));

compiler.clearSourceFiles();
compiler.clearOutputFiles();
compiler.addSourceFile("Odd.java", `package pkg; class Other {
    public static void main(String[] args) {}
    static class Inner { public static void main(String[] args) {} }
    Object anonymous = new Object() {};
}
class Dollar$Name { public static void main(String[] args) {} }
class PrivateOwner { private static class PrivateMain { public static void main(String[] args) {} } }
`);
assert.equal(compiler.compile(), true);
const mains = Array.from(compiler.mainClassesIn("Odd.java"));
assert(mains.some(m => m.binaryName === "pkg.Other$Inner" && m.sourceName === "pkg.Other.Inner"));
assert(mains.some(m => m.binaryName === "pkg.Dollar$Name" && m.sourceName === "pkg.Dollar$Name"));
for (const name of compiler.listOutputFiles()) {
    assert.equal(compiler.getOutputSourceFile(name), "Odd.java");
}
assert.equal(compiler.mainClass("pkg.Other$Inner").sourceName, "pkg.Other.Inner");
assert.equal(compiler.mainClass("pkg.Dollar$Name").sourceName, "pkg.Dollar$Name");
assert.throws(() => compiler.mainClass("pkg.PrivateOwner$PrivateMain"));
assert.equal(compiler.mainClass("Missing"), null);
compiler.clearSourceFiles();
compiler.addSourceFile("pkg/$Launcher.java", "package pkg; public class $Launcher { public static void main(String[] args) { Other.Inner.main(args); Dollar$Name.main(args); } }");
assert.equal(compiler.compile(), true);
assert(compiler.listOutputFiles().includes("pkg/Other$Inner.class"));
compiler.clearSourceFiles();
compiler.addSourceFile("Main.java", "class Main { Missing dependency; }");
assert.equal(compiler.compile(), false);
assert.deepEqual(Array.from(compiler.listOutputFiles()), []);
assert.equal(compiler.getOutputSourceFile("pkg/Other.class"), null);
compiler.disposeAnalysis();
compiler.disposeAnalysis();
assert.throws(() => compiler.complete("Main.java", 0));
console.log("javac semantic regressions passed");
