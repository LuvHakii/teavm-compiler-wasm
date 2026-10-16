import { readdir, readFile, writeFile } from "node:fs/promises";
import { join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const [command, wasmDir, classlibDir, corpusDir, file] = process.argv.slice(2);

const realFetch = globalThis.fetch;
globalThis.fetch = async (url: any, ...rest: any[]) =>
  typeof url === "string" && !url.includes("://")
    ? new Response(await readFile(url), { headers: { "content-type": "application/wasm" } })
    : realFetch(url, ...rest);

const { load } = await import(pathToFileURL(resolve(wasmDir, "compiler.wasm-runtime.js")).href);
const mod = await load(join(wasmDir, "compiler.wasm"), {});
const bytes = async (path: string) => new Int8Array(await readFile(path));
const sdk = await bytes(join(classlibDir, "compile-classlib-teavm.bin"));
const classlib = await bytes(join(classlibDir, "runtime-classlib-teavm.bin"));

function createCompiler(classlibBytes: Int8Array, record = false) {
  const compiler = mod.exports.createCompiler();
  compiler.setSdk(sdk);
  compiler.setTeaVMClasslib(classlibBytes);
  if (record) compiler.setRecordClassSnapshot(true);
  return compiler;
}

function launcher(main: string) {
  return `public final class $Launcher {
    public static void main(String[] args) {
        try {
            ${main.replaceAll("/", ".")}.main(args);
        } catch (Throwable t) {
            System.err.print("Exception in thread \\"main\\" ");
            t.printStackTrace();
            System.exit(1);
        }
    }
}
`;
}

function compileProgram(compiler: any, source: string): Uint8Array {
  compiler.clearSourceFiles();
  compiler.clearOutputFiles();
  compiler.addSourceFile("Main.java", source);
  if (!compiler.compile()) throw new Error("javac failed");
  const [main] = compiler.detectMainClasses();
  compiler.addSourceFile("$Launcher.java", launcher(main));
  if (!compiler.compile()) throw new Error("javac failed on the launcher");
  if (!compiler.generateWebAssembly({ outputName: "app", mainClass: "$Launcher", obfuscated: true })) {
    throw new Error("TeaVM failed");
  }
  const wasm = compiler.getWebAssemblyOutputFile("app.wasm");
  return new Uint8Array(wasm.buffer, wasm.byteOffset, wasm.length);
}

const ENOSYS = 52;
const EBADF = 8;
async function run(wasm: Uint8Array) {
  let memory: WebAssembly.Memory;
  const out: number[] = [];
  const err: number[] = [];
  let code = 0;
  let exited = false;
  const wasi = WebAssembly.Module.imports(new WebAssembly.Module(wasm))
    .filter((i) => i.module === "wasi_snapshot_preview1");
  const imports: Record<string, (...args: any[]) => number> = {
    ...Object.fromEntries(wasi.map((i) => [i.name, () => ENOSYS])),
    args_sizes_get: (count: number, size: number) => {
      const view = new DataView(memory.buffer);
      view.setUint32(count, 0, true);
      view.setUint32(size, 0, true);
      return 0;
    },
    environ_sizes_get: (count: number, size: number) => imports.args_sizes_get(count, size),
    fd_prestat_get: () => EBADF,
    fd_read: (_fd: number, _iovs: number, _count: number, nread: number) => {
      new DataView(memory.buffer).setUint32(nread, 0, true);
      return 0;
    },
    fd_write: (fd: number, iovs: number, count: number, nwritten: number) => {
      const view = new DataView(memory.buffer);
      let total = 0;
      for (let i = 0; i < count; i++) {
        const start = view.getUint32(iovs + i * 8, true);
        const length = view.getUint32(iovs + i * 8 + 4, true);
        (fd === 2 ? err : out).push(...new Uint8Array(memory.buffer, start, length));
        total += length;
      }
      view.setUint32(nwritten, total, true);
      return 0;
    },
    clock_time_get: (_id: number, _precision: bigint, time: number) => {
      new DataView(memory.buffer).setBigUint64(time, BigInt(Date.now()) * 1000000n, true);
      return 0;
    },
    random_get: (start: number, length: number) => {
      new Uint8Array(memory.buffer, start, length).fill(7);
      return 0;
    },
    proc_exit: (status: number) => {
      exited = true;
      code = status;
      throw new Error("exit");
    },
  };
  const ignoreExit = (e: Error) => {
    if (!exited) throw e;
  };
  process.on("uncaughtException", ignoreExit);
  const app = await load(wasm, {
    installImports(i: any) {
      memory = i.env.memory;
      i.wasi_snapshot_preview1 = imports;
    },
  });
  try {
    app.exports.main([]);
    const stopped = app.instance.exports.teavm_stopped;
    for (let waited = 0; stopped && !stopped() && !exited && waited < 10000; waited += 5) {
      await new Promise((resolve) => setTimeout(resolve, 5));
    }
  } catch (e) {
    if (!exited) throw e;
  } finally {
    await new Promise((resolve) => setTimeout(resolve, 0));
    process.off("uncaughtException", ignoreExit);
  }
  const text = new TextDecoder();
  return JSON.stringify({ out: text.decode(new Uint8Array(out)), err: text.decode(new Uint8Array(err)), code });
}

const programs = (await readdir(corpusDir)).filter((name) => name.endsWith(".java")).sort();
const sources = await Promise.all(programs.map((name) => readFile(join(corpusDir, name), "utf8")));

if (command === "build") {
  const compiler = createCompiler(classlib, true);
  sources.forEach((source) => compileProgram(compiler, source));
  const snapshot = compiler.exportClassSnapshot();
  await writeFile(file, new Uint8Array(snapshot.buffer, snapshot.byteOffset, snapshot.length));
  console.log(`snapshot: ${snapshot.length} bytes from ${programs.length} programs`);
} else if (command === "check") {
  const packed = await bytes(file);
  if (packed.length <= classlib.length) throw new Error("the packed classlib carries no snapshot");
  const plain = createCompiler(classlib);
  const snapshotted = createCompiler(packed);
  let failed = 0;
  for (const [i, source] of sources.entries()) {
    const expected = await run(compileProgram(plain, source));
    const actual = await run(compileProgram(snapshotted, source));
    if (expected !== actual) failed++;
    console.log(`${expected === actual ? "same" : "DIFFERENT"} ${programs[i]}`);
    if (expected !== actual) console.log(`  plain:       ${expected.slice(0, 300)}\n  with snapshot: ${actual.slice(0, 300)}`);
  }
  if (failed) throw new Error(`${failed} programs behave differently with the snapshot`);
} else {
  throw new Error("usage: snapshot.ts build <wasm dir> <classlib dir> <corpus dir> <snapshot out>\n       snapshot.ts check <wasm dir> <classlib dir> <corpus dir> <packed classlib>");
}
