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
`compiler.wasm-runtime.js`, `compile-classlib-teavm.bin`, `runtime-classlib-teavm.bin`, `versions.json`, `javadoc.bin` and `jdk-src.tar`. Set `WASM_OPT` to use
another `wasm-opt`; without one on `PATH` the script downloads binaryen 133. `SG` overrides the ast-grep command
(default `npx -y -p @ast-grep/cli@0.45.3 ast-grep`). `GRADLE_ARGS` passes extra Gradle arguments. `javadoc.bin` and
`jdk-src.tar` are built from one JDK `src.zip`, the one next to the `java` on `PATH` (`JDK_SRC_ZIP` selects another archive): `javadoc.bin` holds byte ranges into `jdk-src.tar`, so `build.sh` passes the archive to Gradle as `-PjdkSrcZip` after `GRADLE_ARGS`.

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
  sources and writes `javadoc.bin` for the public `java.*`/`javax.*` API: `{files, keys, at}`, where `at` holds a file index, a start byte and an end byte per key, the range of the `/** */` comment inside `jdk-src.tar`. The text is not stored a second time: the index is 0.19 MB with brotli where the slimmed comment text was 1.33 MB. A reader takes the comment out of the tar, decodes `\uXXXX` escapes, removes the `/**`, the closing `*/` and the leading `*` of each line the way javac does, and keeps the lines up to the first tag other than `@param`, `@return`, `@throws`, `@exception` and `@deprecated`. All 18,962 entries rebuilt that way equal the text the previous `javadoc.bin` stored; one entry (`StringUTF16#putCharSB`) is dropped, because its previous text was a row of slashes from a non-`/** */` comment.
- `scripts/jdk-src.sh`: `jdk-src.tar` holds the `java.base` `java/**` and `javax/**` sources that `DocIndex` reads (without `module-info.java` and `package-info.java`), path-relative to the module (`java/lang/String.java`), sorted, with zeroed owner and mtime so the tar is reproducible. 1,695 files, 29.8 MB raw and 3.21 MB with brotli.
- `patches/archive-uncompressed.yml`: upstream writes `compile-classlib-teavm.bin` and `runtime-classlib-teavm.bin` as one
  gzip stream, which the release step then brotli-compresses again (no gain) and the compiler inflates in Wasm. The
  archives are now plain, so only the transport compresses them: the runtime classlib goes from 2.31 MB to 1.65 MB with
  brotli, the SDK from 208 KB to 149 KB, and `setSdk` plus `setTeaVMClasslib` take 60 ms instead of 136 ms (Node). The
  archive builder and reader change together; a bin built before this does not load.
- `patches/snapshot-class-source.yml` (with `overlay/.../ClassSnapshot.java`): upstream drops its parsed class source on
  every `compile()` and `addSourceFile()`, so each build parses the classlib bytecode again. The class source now
  lives until the classlib or the class path changes. `runtime-classlib-teavm.bin` carries one extra entry,
  `classlib-holders.bin`: the classlib classes (with unoptimized programs, in TeaVM's `ClassIO` format) reached by
  `overlay/compiler/src/snapshot/corpus`, built by compiling the corpus against the plain classlib and packed by
  `packClasslib`. `setTeaVMClasslib` takes the entry out of the archive, so there is no second setter and no call
  order. Measured on Node: the first TeaVM step of Hello drops from 1.9 s to 0.8 s, Files from 6.3 s to 4.3 s; classes
  outside the corpus are parsed as before. The entry carries a checksum of the other classlib entries and any other
  classlib ignores it. `snapshot.ts check` (CI step `snapshot`) compiles the corpus on the plain and the packed
  classlib, runs both on a stub WASI
  host (no files, no stdin; `System.exit` unwinds from a fiber after `main` returns, so it is caught process-wide) and
  requires the same stdout, stderr and exit code. The modules are not byte-identical: class ids and `teavm_line`
  depend on the order classes are visited. Optimized programs must not be cached instead: TeaVM's program cache
  reuses results that depend on the whole program, and a cached `AbstractStringBuilder` trapped with `illegal cast`.
- `patches/optimization-option.yml`: upstream always compiles programs at TeaVM's `ADVANCED` level.
  `generateWebAssembly({optimization})` now takes `simple`, `advanced` or `full` (default `advanced`; any other value
  throws). Warm TeaVM step on Node, `simple` against `advanced`: Fmt 0.9 s against 1.7 s, Files 2.8 s against 4.0 s,
  Scan3 1.9 s against 2.4 s, with modules 14 to 19% larger; `full` was twice as slow as `advanced` and larger. On a
  CPU-bound program (recursion, sieve, `HashMap`, list sort, `StringBuilder`) the compiled code ran in 409 to 588 ms at
  `simple`, 386 to 693 ms at `advanced` and 275 to 296 ms at `full`. `snapshot.ts check` also builds every corpus
  program at `simple` and requires the same output as `advanced`.
- `patches/obfuscation-option.yml`: upstream reads the `obfuscated` option of `generateWebAssembly` and then forces the
  Wasm target to `setObfuscated(false)`, so every program carried its full name section whatever the host passed. The
  option now applies (default `true`). Hello goes from 74.7 KB to 42.4 KB (brotli 25.0 to 18.2 KB) and Scan3 from
  753 KB to 432 KB (199 to 139 KB); compile time, instantiate time, run time and the text of an uncaught-exception
  stack trace are unchanged. Pass `obfuscated: false` to keep Java names in the Wasm name section.
- `patches/build-gradle.patch`, task `patchRuntime`: the generated `compiler.wasm-runtime.js` registers a
  `FinalizationRegistry` held value `{ ref, queue }`, which is strong, so a `WeakHashMap` value that reaches its key
  kept every javac `Context` alive. Both are now held through `WeakRef`s: heap after a forced GC stays at 35 MB over 60
  `analyze()` runs, where it grew 2.5 MB per run. The task fails when an anchor stops matching exactly once.
- `patches/build-gradle.patch`, `optimizeWasm`: `--strip-debug` only. Of about 45 single binaryen passes tried,
  `inlining-optimizing` and `simplify-globals-optimizing` break the compiler (so do `-O1` and `-Os`). A combination of
  the passes that work took the module from 4.67 to 4.23 MB with the same speed; it is not enabled, because CI has not
  run against it.
- A single rule file or patch does not build on its own.
