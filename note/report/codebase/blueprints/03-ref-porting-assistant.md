# Skill Blueprint: ref-porting-assistant（REF 对照移植新内容）

> 自动生成自 codebase-analyzer（2026-08-17）。
> 源模块：engine/joker/BasicJoker.java（150 常量特化模式）+ JokerRegistry +
> tools/gen-golden.mjs 黄金流水线。触发条件：REF/balatro 原版网页更新
> （新小丑/消耗品/Boss/标签）。

---

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 推荐 Skill 名称 | `balatro-ref-porting-assistant` |
| 用途 | 把 REF JS 新增内容逐方法移植为 Java（钩子覆写+元数据+黄金+单测） |
| AI 替代等级 | 🧑‍💻 AI 辅助（20/30） |
| 实施优先级 | 🥈 Strategic |
| 源依据 | 移植红线（note/references/balatro-规则.md §7）；jokermeta 流水线 |

## 2. 触发场景与关键词

- "REF 更新了，新增了 X 个小丑" / "移植新 Boss"
- "对账 REF 发现漂移"（R166 式脚本对账发现新增项）

推荐 description 触发词：
```yaml
description: >-
  Port new Balatro content from the REF pure-logic JS reference into the Java
  engine. Triggered by: "移植小丑", "REF 更新", "port new jokers", "新增内容移植".
```

## 3. 输入输出契约

| 项 | 契约 |
|------|------|
| 输入 | REF/balatro/js/jokers.js（或 data.js/engine.js）中新增/变更的定义源码段 |
| 前置条件 | REF 本地副本已更新；当前 HEAD 全量测试绿；jokermeta.txt 与 REF 旧版对齐 |
| 输出 | ① BasicJoker 新枚举常量（覆写对应钩子）② jokermeta.txt 新行（`JOKER\|key\|稀有度\|售价\|名称`，经 gen-golden.mjs 导出）③ 重生成受影响 golden ④ 语义单测（参照 JokerAccumulateTest 风格）⑤ release note + 版本号 |
| 不变式 | 流名与 stream 调用顺序与 REF 逐字一致（RngStreamInventoryTest 锁会拦截新增调用点）；钩子集合与 REF 对照 0 缺失 0 多余；desc 文案三处同步 |
| 人工审核点 | 流序红线、与真版规则的偏差判定（是否属于第 7+ 处有意修正——需联网核实+用户拍板）、desc 中文措辞 |
| 错误场景 | REF 定义本身有 bug → 不静默照搬，按 6 处既有先例流程处理（wiki 依据+用户确认+golden 重生成） |

## 4. 依赖清单

| 依赖 | 用途 | 来源 |
|------|------|------|
| Joker 接口（23 钩子） | 移植目标面 | engine/Joker.java:13-134 |
| BasicJoker 枚举模式 | 常量特化实现样板 | engine/joker/BasicJoker.java |
| JokerRegistry.register | 运行期注册（若不走枚举） | engine/joker/JokerRegistry.java:50-52 |
| tools/gen-golden.mjs | jokermeta/golden 重生成（Node vm 沙箱跑 REF） | tools/ |
| RngStreamInventoryTest | 流名/调用点守门 | src/test/ |
| 真版规则 | 偏差判定依据 | balatro wiki（联网核实） |

## 5. Skill 工作流设计

```markdown
## Workflow / Steps
1. 对账：diff REF 新旧版，列出新增/变更定义。
2. 逐个移植：按 BasicJoker 既有同型小丑选样板（累积型看 constellation、
   复制类不涉及——复制解析在 Engine.resolveCopy、概率型看 lucky 模式）。
3. 元数据：gen-golden.mjs 导出 jokermeta 新行，核对 5 段格式。
4. 重生成 golden → 全量测试（黄金差异必须全部可归因于新内容）。
5. 单测：新内容语义 + 边界（满槽/禁入/与复制类交互）。
6. 发版收尾：release note + 版本号 + commit。

## Constraints
- Never 改动任何既有流名/调用顺序（种子复现红线）。
- Never 让新 joker 绕过 ORDERED 收录（注册不自动进商店池/gainRandomJoker，
  JokerRegistry.java:47-49 注释）。
- Always 三处同步：实现/golden/desc。
- 遇 REF bug Always 上报用户而非自行修正。
```

## 6. 所需工具权限

`Read`（REF/本库）、`Write`/`Edit`（源码+资源+测试）、`Bash`（node gen-golden.mjs、
gradle、git）。

## 7. 使用示例

### ✅ Do This
```
输入：jokers.js 新增 defJoker({key:" newX", onScore(ctx){ctx.xMult(2)} ...})
输出：BasicJoker.NEW_X 常量覆写 onScore；jokermeta 行；golden 重生成后
     EngineGoldenTest 全过（既有场景逐位不变）；新增 NewXTest ×2
```
### ❌ Not This
```
错误：移植时"顺手优化"实现方式导致流消耗顺序变化 → 黄金测试红，
     种子复现破坏，违反移植红线
```

## 8. 参考材料

- 移植规范：note/references/balatro-规则.md（§1 移植策略/§7 复现要点）
- 有意修正先例：note/release/逻辑审计.md（6 处，含依据链接）
- 累积钩子先例：逻辑审计.md #13/#14（constellation/hologram 遗漏教训）
