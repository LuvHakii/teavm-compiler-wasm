import { writeFile } from "node:fs/promises";
import { availableParallelism } from "node:os";
import { Worker } from "node:worker_threads";
import { compileProgram, readCorpus, setup } from "./common.ts";

const args = process.argv.slice(2);
const [command, wasmDir, classlibDir, corpusDir, file] = args;

if (command === "build") {
  const { classlib, createCompiler } = await setup(wasmDir, classlibDir);
  const { programs, sources } = await readCorpus(corpusDir);
  const compiler = createCompiler(classlib, true);
  sources.forEach((source) => compileProgram(compiler, source));
  const snapshot = compiler.exportClassSnapshot();
  await writeFile(file, new Uint8Array(snapshot.buffer, snapshot.byteOffset, snapshot.length));
  console.log(`snapshot: ${snapshot.length} bytes from ${programs.length} programs`);
} else if (command === "check") {
  const shards = availableParallelism();
  const resized = await Promise.all(Array.from({ length: shards }, (_, shard) =>
    new Promise<number>((done, fail) => {
      const worker = new Worker(new URL("./snapshot.worker.ts", import.meta.url), { workerData: { args, shard, shards } });
      worker.on("message", done);
      worker.on("error", fail);
      worker.on("exit", (code) => code && fail(new Error(`shard ${shard} exited ${code}`)));
    })));
  if (!resized.some(Boolean)) throw new Error("the optimization option changed no module");
} else {
  throw new Error("usage: snapshot.ts build <wasm dir> <classlib dir> <corpus dir> <snapshot out>\n       snapshot.ts check <wasm dir> <classlib dir> <corpus dir> <packed classlib>");
}
