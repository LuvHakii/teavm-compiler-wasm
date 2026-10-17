import { parentPort, workerData } from "node:worker_threads";
import { bytes, compileProgram, readCorpus, run, setup } from "./common.ts";

const { args: [, wasmDir, classlibDir, corpusDir, file], shard, shards } = workerData;
const { load, classlib, createCompiler } = await setup(wasmDir, classlibDir);
const { programs, sources } = await readCorpus(corpusDir);
const packed = await bytes(file);
if (packed.length <= classlib.length) throw new Error("the packed classlib carries no snapshot");
const plain = createCompiler(classlib);
const snapshotted = createCompiler(packed);
let failed = 0;
let resized = 0;
for (const [i, source] of sources.entries()) {
  if (i % shards !== shard) continue;
  const expected = await run(load, compileProgram(plain, source));
  const advanced = compileProgram(snapshotted, source);
  const simple = compileProgram(snapshotted, source, "simple");
  const actual = await run(load, advanced);
  const quick = await run(load, simple);
  if (advanced.length !== simple.length) resized++;
  const same = expected === actual && expected === quick;
  if (!same) failed++;
  console.log(`${same ? "same" : "DIFFERENT"} ${programs[i]}`);
  if (!same) console.log(`  plain:       ${expected.slice(0, 300)}\n  snapshot:    ${actual.slice(0, 300)}\n  simple:      ${quick.slice(0, 300)}`);
}
if (failed) throw new Error(`${failed} programs behave differently with the snapshot or the simple level`);
parentPort!.postMessage(resized);
