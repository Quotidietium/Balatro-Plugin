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

function loadBalatro(files) {
  const ctx = {};
  vm.createContext(ctx);
  for (const f of files) {
    const src = fs.readFileSync(path.join(REF_JS, f), 'utf8');
    vm.runInContext(src, ctx, { filename: f });
  }
  return ctx;
}

function ensureDir(dir) { fs.mkdirSync(dir, { recursive: true }); }

function writeText(name, lines) {
  ensureDir(OUT);
  const file = path.join(OUT, name);
  fs.writeFileSync(file, lines.join('\n') + '\n', 'utf8');
  console.log('written ' + file);
}

function genRng() {
  const { makeStream } = loadBalatro(['rng.js']);
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
console.log('done.');
