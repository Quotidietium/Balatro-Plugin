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
  for (const t of DATA.TAROT) L.push(`TAROT|${t.key}|${t.name}|${t.desc}`);
  for (const p of DATA.PLANETS) L.push(`PLANET|${p.key}|${p.name}|${p.hand}|${p.desc}`);
  for (const s of DATA.SPECTRAL) L.push(`SPECTRAL|${s.key}|${s.name}|${s.desc}`);
  for (const p of DATA.PACKS) L.push(`PACK|${p.key}|${p.type}|${p.name}|${p.size}|${p.choose}|${p.cost}`);
  for (const v of DATA.VOUCHERS) L.push(`VOUCHER|${v.key}|${v.name}|${v.desc}|${v.base}|${v.pair || v.requires || '-'}`);
  for (const r of DATA.RARITY) L.push(`RARITY|${r.key}|${r.name}|${r.w}`);
  for (const d of DATA.DECKS) L.push(`DECK|${d.key}|${d.name}|${d.desc}`);
  for (const s of DATA.STAKES) L.push(`STAKE|${s.key}|${s.name}|${s.desc}`);
  for (const t of DATA.TAGS) L.push(`TAG|${t.key}|${t.name}|${t.desc}`);
  for (const c of DATA.CHALLENGES) L.push(`CHALLENGE|${c.key}|${c.name}|${c.desc}`);
  writeText('data.txt', L);
}
genData();

// ============ ENGINE 黄金值 ============
// 牌型判定（纯逻辑）+ 固定种子下 small 盲注整回合出牌序列。
// 全在"回合内 / 非 Boss"，避开 0.1.0 未实现的 Boss 干扰效果与商店差异。

function genEvalHands() {
  const { Engine } = loadBalatro(['rng.js', 'data.js', 'jokers.js', 'engine.js'], ['Engine']);
  const sets = [
    [[14, 0]],
    [[5, 0], [5, 1]],
    [[5, 0], [5, 1], [9, 2], [9, 3]],
    [[7, 0], [7, 1], [7, 2]],
    [[2, 0], [3, 1], [4, 2], [5, 3], [6, 0]],
    [[14, 0], [13, 0], [12, 0], [11, 0], [10, 0]],
    [[14, 0], [2, 1], [3, 2], [4, 3], [5, 0]],
    [[10, 0], [11, 1], [12, 2], [13, 3], [14, 0]],
    [[2, 0], [3, 0], [4, 0], [5, 0], [7, 0]],
    [[2, 0], [3, 0], [4, 0], [5, 0], [6, 0]],
    [[7, 0], [7, 1], [7, 2], [9, 3], [9, 0]],
    [[3, 0], [3, 1], [3, 2], [3, 3], [5, 0]],
    [[14, 1], [14, 2], [14, 3], [14, 0]],
    [[6, 0], [6, 1], [6, 2], [6, 3], [9, 0]],
    [[8, 0], [8, 1], [8, 2], [2, 3], [3, 0]],
  ];
  const L = [];
  for (const s of sets) {
    const cards = s.map(([r, su]) => Engine.makeCard(r, su));
    const res = Engine.evaluateHand({ flags: {} }, cards);
    const scoring = res.scoring.map((c) => `${c.rank}.${c.suit}`).join(',');
    L.push(`EVAL ${s.map(([r, su]) => `${r}.${su}`).join(',')} => ${res.type} | ${scoring}`);
  }
  writeText('eval.txt', L);
}

function genEngineRound() {
  const { Engine } = loadBalatro(['rng.js', 'data.js', 'jokers.js', 'engine.js'], ['Engine']);
  const seeds = ['GOLDEN1', 'GOLDEN2', 'GOLDEN3'];
  const L = [];
  for (const seed of seeds) {
    const st = Engine.createRun({ deck: 'red', stake: 0, seed });
    Engine.selectBlind(st, 'small', false);
    L.push(`ROUND ${seed} target=${st.blindTarget} handSize=${st.handSizeRound} hands=${st.handsLeft} discards=${st.discardsLeft}`);
    L.push('HAND ' + st.hand.map((c) => `${c.rank}.${c.suit}`).join(','));
    let safety = 0;
    while (st.phase === 'round' && safety++ < 20) {
      const ids = st.hand.slice(0, Math.min(5, st.hand.length)).map((c) => c.id);
      const r = Engine.playHand(st, ids);
      L.push(`PLAY ok=${r.ok ? 1 : 0} type=${r.type || '-'} score=${r.score || 0} won=${r.won ? 1 : 0} lost=${r.lost ? 1 : 0} rs=${st.roundScore} hl=${st.handsLeft}`);
      if (!r.ok || r.won || r.lost) break;
    }
    L.push('ENDROUND');
  }
  writeText('engine.txt', L);
}

genEvalHands();
genEngineRound();

// ============ JOKER 黄金值 ============
// 给开局授予指定小丑后，驱动 small 盲注整回合，对比计分（验证小丑效果与计分管线）。
function genJokers() {
  const { Engine, JOKERS } = loadBalatro(['rng.js', 'data.js', 'jokers.js', 'engine.js'], ['Engine', 'JOKERS']);
  function grantJoker(state, key) {
    const def = JOKERS.find((j) => j.key === key);
    state.jokers.push({ def, debuff: false, debuffHand: false, edition: null, extra: {} });
  }
  const cases = [
    ['joker', 'GOLDEN1'],
    ['greedy', 'GOLDEN1'],
    ['jolly', 'GOLDEN1'],
    ['sly', 'GOLDEN1'],
    ['half', 'GOLDEN1'],
    ['banner', 'GOLDEN1'],
    ['misprint', 'GOLDEN1'],
    ['raisedfist', 'GOLDEN1'],
    ['fibonacci', 'GOLDEN1'],
    ['scaryface', 'GOLDEN1'],
    ['supernova', 'GOLDEN1'],
    ['ridebus', 'GOLDEN1'],
    ['runner', 'GOLDEN1'],
    ['bull', 'GOLDEN1'],
    ['abstract', 'GOLDEN1'],
    ['smiley', 'GOLDEN1'],
    ['walkie', 'GOLDEN1'],
    ['scholar', 'GOLDEN1'],
    ['blue', 'GOLDEN1'],
    ['green', 'GOLDEN1'],
    ['todo', 'GOLDEN1'],
    ['delayed', 'GOLDEN1'],
    ['square', 'GOLDEN1'],
    ['evensteven', 'GOLDEN1'],
    ['oddtodd', 'GOLDEN1'],
    ['golden', 'GOLDEN1'],
    ['popcorn', 'GOLDEN1'],
    ['icecream', 'GOLDEN1'],
    ['business', 'GOLDEN1'],
    ['faceless', 'GOLDEN1'],
    ['stencil', 'GOLDEN1'],
    ['stuntman', 'GOLDEN1'],
    ['fourfingers', 'GOLDEN1'],
    ['burglar', 'GOLDEN1'],
    ['chad', 'GOLDEN1'],
    ['moon', 'GOLDEN1'],
    ['seeingdouble', 'GOLDEN1'],
    ['space', 'GOLDEN1'],
    ['blackboard', 'GOLDEN1'],
    ['hiker', 'GOLDEN1'],
    ['cardsharp', 'GOLDEN1'],
    ['baron', 'GOLDEN1'],
    ['obelisk', 'GOLDEN1'],
    ['photograph', 'GOLDEN1'],
    ['acrobat', 'GOLDEN1'],
    ['sock', 'GOLDEN1'],
    ['troubadour', 'GOLDEN1'],
    ['ramen', 'GOLDEN1'],
    ['ancient', 'GOLDEN1'],
    ['smeared', 'GOLDEN1'],
    ['gem', 'GOLDEN1'],
    ['arrowhead', 'GOLDEN1'],
    ['onyx', 'GOLDEN1'],
    ['flowerpot', 'GOLDEN1'],
    ['wee', 'GOLDEN1'],
    ['oops', 'GOLDEN1'],
    ['idol', 'GOLDEN1'],
    ['bootstraps', 'GOLDEN1'],
    ['duo', 'GOLDEN1'],
    ['order', 'GOLDEN1'],
    ['tribe', 'GOLDEN1'],
    ['triboulet', 'GOLDEN1'],
    ['hittheroad', 'GOLDEN1'],
  ];
  const L = [];
  for (const [jkey, seed] of cases) {
    const st = Engine.createRun({ deck: 'red', stake: 0, seed });
    grantJoker(st, jkey);
    Engine.selectBlind(st, 'small', false);
    L.push(`JROUND ${jkey} ${seed} target=${st.blindTarget}`);
    L.push('HAND ' + st.hand.map((c) => `${c.rank}.${c.suit}`).join(','));
    let safety = 0;
    while (st.phase === 'round' && safety++ < 20) {
      const ids = st.hand.slice(0, Math.min(5, st.hand.length)).map((c) => c.id);
      const r = Engine.playHand(st, ids);
      L.push(`PLAY ok=${r.ok ? 1 : 0} type=${r.type || '-'} score=${r.score || 0} won=${r.won ? 1 : 0} lost=${r.lost ? 1 : 0} rs=${st.roundScore} hl=${st.handsLeft}`);
      if (!r.ok || r.won || r.lost) break;
    }
    L.push('ENDROUND');
  }
  writeText('jokers.txt', L);
}
genJokers();

console.log('done.');
