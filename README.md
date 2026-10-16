# teavm-compiler-wasm

Patches for [teavm-javac](https://github.com/konsoletyper/teavm-javac) that make its WasmGC compiler usable as an
embedded Java toolchain: a WASI-backed runtime for compiled programs, editor services on top of javac, and a bundle
export. The upstream is pinned as the `teavm-javac` submodule.

## Layout

- `patches/*.yml`: ast-grep rules applied to the submodule. Each rule states how many times it must match
  (`metadata.expect`), so a bump that moves a site fails instead of passing silently.
- `patches/*.patch`: Gradle and TOML edits, which ast-grep has no grammar for (`groovy is not supported!`).
- `overlay/`: new files, copied over the submodule after the rules run.
- `overlay/compiler/derive/*.yml`: ast-grep rules applied during the Gradle build to TeaVM classlib sources extracted from the
  `teavm-classlib` and `teavm-core` source jars (`compiler/derive.gradle`). The `files:` globs of the rules choose
  which sources are extracted.

## Build

```sh
scripts/build.sh [output-dir]
```

It applies the rules, the `.patch` files (it fails when a hunk applies at an offset) and the overlay to the
submodule, then runs `:compiler:exportAssets`. The output directory (default `out/`) receives `compiler.wasm`,
`compiler.wasm-runtime.js`, `compile-classlib-teavm.bin`, `runtime-classlib-teavm.bin`, `versions.json` and `javadoc.bin`. Set `WASM_OPT` to use
another `wasm-opt`; without one on `PATH` the script downloads binaryen 133. `SG` overrides the ast-grep command
(default `npx -y -p @ast-grep/cli@0.45.3 ast-grep`). `GRADLE_ARGS` passes extra Gradle arguments. `javadoc.bin` is
built from the JDK `src.zip` (`-PjdkSrcZip` selects another archive).

## Caveats

- `overlay/compiler/src/wasiRuntime` and `overlay/compiler/derive/*.yml` replace the compiled-program runtime: a WASI file system with
  a working directory taken from `PWD`, `File.toPath()`, a basic `FileChannel`, `Scanner`, string formatting and
  number conversion (Schubfach), and a console backed by WASI. `TSystem`, `Fiber`, `TFile`, `TRandomAccessFile`,
  `TReader`, `TPrintStream`, `TUTF8Encoder`, `TDecimalFormatParser`, `TDefaultFileSystemProvider`,
  `DoubleAnalyzer` and `TDouble` are derived from the TeaVM sources instead of copied. Regenerating the classlib
  archives is required after any change here.
- `patches/compiler-editor-services.yml` exports analysis, completion (members, types, keywords, snippets and
  auto-import edits), hover, definition, main-class discovery and output-to-source mapping from the compiler.
  Positions are zero-based UTF-16 offsets. Completion analyses a copy of the sources with a placeholder identifier
  and never changes the canonical state. Doc keys use `Owner[#member(ErasedParamTypes)]` with simple type names. It
  also registers `Object.clone` and `java.lang.reflect.Array` dependency plugins: the classlib registers them through
  `@PluggableDependency` and `TeaVMPluginUtil.handleNatives`, which are skipped when TeaVM runs compiled by TeaVM, so
  without them virtual calls on a cloned object or a reflective array item reach a null vtable slot.
- `patches/filemanager-output-classpath.yml` makes compiled output visible on the class path and records which
  source produced each class. `patches/stdlib-hidden-types.yml` and `patches/stdlib-nest-members.yml` keep TeaVM
  internal types and broken nest metadata out of the javac SDK stubs (without the nest rules,
  `Map.Entry` does not resolve). `patches/main-method-launchable.yml` restricts main discovery to
  `public static void main(String[])`; without it `static void main()` and non-public mains count as runnable.
- `patches/build-gradle.patch` adds the `wasiRuntime` source set, the class excludes that stop the upstream classlib
  from shadowing it, and the `exportAssets` task (optional `-PwasmOpt` runs `wasm-opt --strip-debug` with an
  explicit feature list, because the module has no `target_features` section; `-O1` and `-Os` make the compiler trap
  with `unreachable` in `semantic.mjs`, so the output is 4.62 MB instead of 4.0 MB). `patches/checkstyle-version.patch`
  adds the `checkstyle` version that upstream's `settings.gradle` reads but its catalog lacks.
- `patches/harness-wasi.yml` and the overlay tests add a WASI-based browser harness and `semantic.mjs`, a script that
  exercises the exported editor services against a built compiler. `overlay/tools/DocIndex.java` parses the JDK
  sources and writes `javadoc.bin` for the public `java.*`/`javax.*` API.
- `patches/build-gradle.patch`, task `patchRuntime`: the generated `compiler.wasm-runtime.js` registers a
  `FinalizationRegistry` held value `{ ref, queue }`, which is strong, so a `WeakHashMap` value that reaches its key
  kept every javac `Context` alive. Both are now held through `WeakRef`s: heap after a forced GC stays at 35 MB over 60
  `analyze()` runs, where it grew 2.5 MB per run. The task fails when an anchor stops matching exactly once.
- `patches/build-gradle.patch`, `optimizeWasm`: `--strip-debug` only. Of about 45 single binaryen passes tried,
  `inlining-optimizing` and `simplify-globals-optimizing` break the compiler (so do `-O1` and `-Os`). A combination of
  the passes that work took the module from 4.67 to 4.23 MB with the same speed; it is not enabled, because CI has not
  run against it.
- A single rule file or patch does not build on its own.
