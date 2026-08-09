// gen-golden.mjs — 运行原版 REF/balatro/js 的纯逻辑模块，产出黄金值文本供 Java 移植单测断言。
// 用法： node tools/gen-golden.mjs
// 输出： src/test/resources/golden/*.txt （行文本，便于 Java 无依赖解析）
// 注意：REF/ 被忽略（本地），本脚本仅本地使用；产出的文本提交进仓库，测试自包含。
//
// 文本格式：每段以 "<TAG> <args...>" 起头，随后每行一个值，段尾 "END"。

import fs from 'node:fs';
import vm from 'node:vm';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REF_JS = path.resolve(__dirname, '..', 'REF', 'balatro', 'js');
const OUT = path.resolve(__dirname, '..', 'src', 'test', 'resources', 'golden');

// 把多个纯逻辑脚本合并为单个脚本运行（共享顶层词法作用域），并在末尾把指定符号挂到 globalThis 取回。
// 用 typeof 守卫，避免未加载的符号（如未加载 engine.js 时的 Engine）抛 ReferenceError。
function loadBalatro(files, exportNames) {
  const ctx = {};
  vm.createContext(ctx);
  const bundle = files.map((f) => fs.readFileSync(path.join(REF_JS, f), 'utf8')).join('\n;\n');
  const grab = exportNames.map((n) => `${n}: typeof ${n} !== "undefined" ? ${n} : undefined`).join(', ');
  vm.runInContext(bundle + `\n;globalThis.__out = { ${grab} };\n`, ctx, { filename: 'bundle.js' });
  return ctx.__out;
}

function ensureDir(dir) { fs.mkdirSync(dir, { recursive: true }); }

function writeText(name, lines) {
  ensureDir(OUT);
  const file = path.join(OUT, name);
  fs.writeFileSync(file, lines.join('\n') + '\n', 'utf8');
  console.log('written ' + file);
}

function genRng() {
  const { makeStream } = loadBalatro(['rng.js'], ['makeStream']);
  const seeds = ['TEST', 'BALATRO', 'ABCD1234', 'Z'];
  const streamNames = ['deckbuild', 'shop', 'boss', 'round', 'joker', 'x'];
  const L = [];

  for (const seed of seeds) {
    for (const sn of streamNames) {
      const s = makeStream(seed, sn);
      L.push(`NEXT ${seed}|${sn}`);
      for (let i = 0; i < 30; i++) L.push(String(s.next()));
      L.push('END');
    }
  }
  for (const seed of seeds) {
    const s = makeStream(seed, 'rng');
    L.push(`RANGE ${seed}`);
    for (let i = 0; i < 20; i++) L.push(String(s.range(2, 14)));
    L.push('END');
  }
  for (const seed of seeds) {
    const s = makeStream(seed, 'pick');
    const base = []; for (let i = 0; i < 52; i++) base.push(i);
    L.push(`PICK ${seed}`);
    for (let i = 0; i < 20; i++) L.push(String(s.pick(base)));
    L.push('END');
  }
  for (const seed of seeds) {
    const s = makeStream(seed, 'shuf');
    const arr = []; for (let i = 0; i < 52; i++) arr.push(i);
    s.shuffle(arr);
    L.push(`SHUF ${seed}`);
    for (const v of arr) L.push(String(v));
    L.push('END');
  }
  for (const seed of seeds) {
    const s = makeStream(seed, 'chance');
    L.push(`CHANCE ${seed}`);
    for (let i = 0; i < 20; i++) L.push(s.chance(0.25) ? '1' : '0');
    L.push('END');
  }
  for (const seed of seeds) {
    const s = makeStream(seed, 'weighted');
    const items = [{ w: 1 }, { w: 2 }, { w: 3 }, { w: 0 }];
    L.push(`WEIGHTED ${seed}`);
    for (let i = 0; i < 20; i++) L.push(String(s.weighted(items).w));
    L.push('END');
  }

  writeText('rng.txt', L);
}

genRng();

// ============ DATA 黄金值 ============
function genData() {
  const { DATA } = loadBalatro(['data.js'], ['DATA']);
  const L = [];
  for (let r = 2; r <= 14; r++) L.push(`RANKNAME ${r} ${DATA.rankName(r)}`);
  for (let r = 2; r <= 14; r++) L.push(`RANKCHIPS ${r} ${DATA.rankChips(r)}`);
  for (let ante = 1; ante <= 12; ante++) L.push(`BLINDBASE ${ante} ${DATA.blindBase(ante)}`);
  L.push('HANDS');
  for (const k of Object.keys(DATA.HANDS)) {
    const h = DATA.HANDS[k];
    L.push(`HAND ${k} ${h.name} ${h.chips} ${h.mult} ${h.lchips} ${h.lmult} ${h.order}`);
  }
  L.push('END');
  for (const t of ['small', 'big', 'boss']) {
    L.push(`BLIND ${t} ${DATA.BLIND_MULT[t]} ${DATA.BLIND_REWARD[t]}`);
  }
  L.push('SUITS');
  for (const s of DATA.SUITS) L.push(`SUIT ${s.key} ${s.name} ${s.symbol} ${s.color}`);
  L.push('END');
  for (const k of Object.keys(DATA.ENHANCEMENTS)) {
    const e = DATA.ENHANCEMENTS[k];
    L.push(`ENH|${k}|${e.name}|${e.desc}`);
  }
  for (const k of Object.keys(DATA.EDITIONS)) {
    const e = DATA.EDITIONS[k];
    L.push(`EDITION|${k}|${e.name}|${e.desc}|${e.chance}`);
  }
  for (const k of Object.keys(DATA.SEALS)) {
    const s = DATA.SEALS[k];
    L.push(`SEAL|${k}|${s.name}|${s.desc}`);
  }
  writeText('data.txt', L);
}
genData();

console.log('done.');
