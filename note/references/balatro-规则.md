# 参考分析：balatro（目标玩法规则）

> 定位：Balatro（小丑牌）的完整玩法已被提取成一套**纯逻辑网页**（HTML+JS，无外部依赖），
> 是本项目要实现的**目标规则**。引擎与数据分离、全部随机走种子流（可复现），非常适合移植到 Java。
> 源码：`REF/balatro/`（`index.html` + `js/{rng,data,jokers,engine,game}.js` + `css/style.css`）。

## 1. 模块划分

| 文件 | 职责 |
|---|---|
| `js/rng.js` | 种子随机：字符串→32 位哈希（FNV-1a）+ mulberry32 PRNG + **命名随机流** |
| `js/data.js` | 静态数据表（花色/点数/牌型/盲注/牌组/赌注/Boss/标签/优惠券/塔罗/星球/幻灵/增强/版本/蜡封/补充包/挑战） |
| `js/jokers.js` | 小丑定义：`defJoker({...})` 注册 + 各类钩子 |
| `js/engine.js` | 游戏引擎（纯逻辑）：状态机、牌组构建、计分、商店、补充包、消耗品、胜负 |
| `js/game.js` | UI/渲染胶水层（DOM 操作），移植时**整层丢弃**，用 MC 渲染替换 |

> 移植策略：`rng / data / engine / jokers` 四个纯逻辑模块**逐方法翻译为 Java**；
> `game.js`（DOM 渲染）丢弃，由 MC 端的渲染/交互层重写。

## 2. 核心循环（状态机）

```
blindSelect（选/跳过盲注） → round（出牌回合） → shop（商店）
   →（可选 pack 开补充包）→ 下一盲注 → … → 通关(ante 8) / 失败 → end
```

- `phase` 取值：`blindSelect / round / shop / pack / end`。
- 盲注顺序：每底注须按 **small → big → boss** 面对；**跳过**是唯一绕过方式（boss 不可跳过）。
- 8 个底注（ante 1~8）通关即胜利；可进入**无尽模式**（ante 9+，分数指数增长）。

## 3. 计分模型（核心：chips × mult）

- 一手牌得分 = `(基础筹码 + 各计分牌筹码 + 各加成) × (基础倍率 + 各倍率加成)`。
  - 牌型提供基础 **chips/mult**；牌型可被**星球牌升级**（`handLevels`，每级 +lchips/+lmult）。
  - 每张**计分牌**（参与牌型的牌）按点数加筹码（2~10=点数，J/Q/K=10，A=11），并触发**增强/版本/蜡封**效果。
- 计分流程（`engine.js` `playHand`/`scoreOneCard`，约 `js/engine.js:648-955`）：
  1. 判定最佳牌型 `scoreHand`（含万能牌适配、顺子滑动判定、石头牌永远计分）；
  2. 计分牌逐张：基础筹码 + 增强（如奖励牌+30、玻璃×2）+ 版本（闪膜+50/镭射+10/多彩×1.5）+ 蜡封（金+$3、红重触发）；
  3. 持有牌效果（钢铁牌在手 ×1.5、哑剧重触发）；
  4. 小丑结算（`onScore`/`onScoreCard`/`onHeld`，含蓝图/头脑风暴复制）；
  5. 出牌后处理（Boss：钩子弃牌、牙齿扣钱、手臂牌型降级；记录本底注打过的牌给"支柱"）。
- **等离子牌组**特殊：结算时筹码与倍率先取平均再相乘。
- 回合结算 `endRound`（`js/engine.js:1035`）：盲注奖励金 + 剩余出牌×$1 + 利息（每$5 计$1，有上限）+ 手中黄金牌/蓝色蜡封 + 小丑回合结束钩子 + 租赁/易腐小丑结算。

## 4. 状态对象（`createRun`，`js/engine.js:31`）

关键字段（移植时即 Java `RunState` 类的字段）：
```
seed, deckKey, stakeIdx, challenge, mods{},
phase(blindSelect/round/shop/pack/end), money, ante, bossKey, blindType,
jokerSlots(5), consumableSlots(2), handSizeBase(8), handsBase(4), discardsBase(3),
interestCap(5), shopSlots(2),
jokers[], consumables[], vouchers[], tags[],
fullDeck[], drawPile[], hand[], discardPile[],
handLevels{}, handPlayedCount{}, usedPlanets{},
handsLeft, discardsLeft, roundScore, blindTarget, ...
```
- 牌组/赌注/挑战的效果在 `createRun` 里一次性应用到 `state`（如蓝牌组 +1 出牌、黑牌组 +1 槽 -1 出牌、蓝注 -1 弃牌…）。

## 5. 小丑钩子模型（`js/jokers.js`）

注册：`defJoker({ key, name, desc, rarity, cost, 钩子... })`。钩子签名（`ctx` 为计分上下文）：
- `onScore(ctx)` / `onScoreCard(ctx, card)` — 计分时
- `onHeld(...)` — 持有牌效果
- `onPlayHand(state, info)` — 出牌后（常用于累积型小丑，把状态存 `joker.extra`）
- `onDiscard` / `onEndRound` / `onShop` 等
- `ctx` API：`addChips/addMult`、`handIs(type)`、`isSuit(card,suit)`、`isFace(card)`、`playedCards`、`state`、`rngInt(a,b)`、`joker.extra`（持久小丑状态）。
- 复制类小丑（蓝图/头脑风暴）经 `resolveCopy` 解析实际生效实例。

> 移植为 Java 时，建议用**接口 + 注册表**或**枚举 + 策略**承载钩子，`extra` 用可变字段或 `Map`。

## 6. 内容数据表清单（`js/data.js`，移植时即静态数据）

| 类别 | 数量 | 说明 |
|---|---|---|
| 花色 | 4 | 黑桃/红桃/梅花/方块 |
| 点数 | 2~14 | J=11 Q=12 K=13 A=14；牌面筹码 2~10=点数、JQK=10、A=11 |
| 牌型 | 13 | 高牌/对子/两对/三条/顺子/同花/葫芦/四条/同花顺/皇家同花顺/五条/同花葫芦/同花五条（含 chips/mult/升级成长） |
| 牌组 | 15 | red/blue/yellow/green/black/magic/nebula/ghost/abandoned/checkered/zodiac/painted/anaglyph/plasma/erratic |
| 赌注 | 8 | white→gold，效果累加 |
| Boss 盲注 | 28 | 各种全局/出牌副作用 |
| 跳过标签 | 24 | 跳过盲注获得的增益标签 |
| 优惠券 | 32（16 对） | 有升级链（base + pair） |
| 塔罗牌 | 22 | 消耗品，改手牌/给牌 |
| 星球牌 | 12 | 消耗品，升级对应牌型 |
| 幻灵牌 | 18 | 消耗品，强力但有代价 |
| 增强 | 8 | 奖励/倍率/万能/玻璃/钢铁/石头/黄金/幸运 |
| 版本 | 5 | 闪膜/镭射/多彩/负片（+原版） |
| 蜡封 | 4 | 金/红/蓝/紫 |
| 补充包 | 13 | arcana/celestial/standard/buffoon/spectral 各档 |
| 挑战模式 | 20 | 带 `mods` 修饰 |
| 稀有度 | 4 | common(70)/uncommon(25)/rare(5)/legendary(0，不靠权重) |

> 上述数字即首版可实现的**内容规模上限**；MVP 可只实现子集（见 MVP 决策）。

## 7. 随机与可复现（`js/rng.js`）

- 字符串种子 → FNV-1a 32 位哈希 → mulberry32 PRNG。
- **命名随机流** `makeStream(runSeed, stream)`：不同用途（如 `deckbuild`/`shop`/`boss`）用不同流名，互不干扰；
  **同一种子 + 同一调用序列 ⇒ 完全相同结果**（种子局可复现/分享）。
- 流 API：`next()`、`range(min,max)`、`pick(arr)`、`chance(p)`、`shuffle(arr)`（Fisher–Yates）、`weighted(items)`。
- 种子字符串默认 8 位（字母表去除易混淆字符），用户可自定义输入。

> 移植要点：**必须保持钩子内 `stream(name)` 的调用顺序与原版一致**，否则种子不复现；这是移植正确性的关键约束。
