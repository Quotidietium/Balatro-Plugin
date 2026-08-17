# live-bot — 实机假人验证工具（R220 建立）

mineflayer（MC 协议级假人客户端）驱动的**真实服务器**验证脚本集。
R220 审计轮（2026-08-17）首次建立，用于执行 `note/release/实机验证清单.md`
中纯逻辑测试无法覆盖的 Bukkit/网络/渲染层验证。

## 环境要求

- 真实 Paper 1.21.11 服务器（本轮用 `F:\paper-test-1.21.11`，offline 模式）
- Node.js + `npm install mineflayer`（协议 774 = 1.21.11）
- 服务器插件目录放入 balatro jar

## 假人接入要点（踩坑记录）

1. **offline UUID**：`ops.json` 需写入 `UUID.nameUUIDFromBytes("OfflinePlayer:<名>")`（MD5 v3），
   否则 op 命令全部伪装成 "Unknown command"。
2. **服务器侧实体计数神谕**：op 假人发
   `/execute as @e[type=minecraft:interaction] run say [CNT|interaction]`，
   从聊天回显计数——可见性无关的服务器真值。
3. **私有可见验证**：观察者假人的 `bot.entities` 收不到 `setVisibleByDefault(false)`
   实体的 spawn 包——客户端侧 0 实体即为证明。
4. **点击**：`bot.activateEntity(e)` 发 use_entity 包（`sneaking:false` 硬编码）；
   Shift+右键需手写包 `bot._client.write('use_entity', {target, mouse:0, sneaking:true, hand:0})`。
5. **陈旧实体过滤**：牌桌 Interaction 池复用 + 相位切换后零尺寸残留（服务器侧已摘标签，
   无害），假人按 `metadata[8] > 0`（宽度）过滤活跃命中盒。
6. **聊天按钮**：Paper 1.21.11 组件序列化为 NBT 风格 `click_event.command`
   （非老式 `clickEvent.value`）；从 `message.json` 提取后 `bot.chat(cmd)` 即等价点击。
7. **布局分类（旋转无关）**：牌桌 `right = dir×up = (−fz, 0, fx)`；按钮/手牌按
   `dot(pos−center, right)` 投影排序——**主操作按钮（play/go/reroll）恒在投影最小侧**；
   mineflayer 每次 activateEntity 会 lookAt 漂移视向，绝对 x 排序会翻车。
8. **协议层防线**：>256 字符命令被 Netty 解码拒绝（DecoderException 踢出）；
   `§`（U+00A7）触发 `illegal_characters` 踢出——都是**服务器自带防线**，预期行为。
9. **RCON 环境陷阱（重大，R221 发现）**：**Paper 1.21.11 启用 RCON 后 use_entity
   全灭**（A/B 四组对照：boot1/2/6 无 RCON 交互全部正常，boot3/4 有 RCON 时连
   原版右键上船都不行、服务器侧玩家坐标冻结的假象、mineflayer 确认包正常发出）。
   机理未定位（疑与该 Paper 构建的 RCON 实现有关）。**规约：假人验证一律在
   RCON 关闭的服务器上执行**；需要控制台时用「假人 op 直跑命令」或
   stop-and-restart 替代。相关假阴性排查记录：无交易村民右键无窗口（假阴性
   探针）；soak 脚本的「手数」是乐观计数——在 RCON 死服上会掩盖交互失效，
   必须以状态变化（score/hands/money）为断言。
10. **Interaction 的 `position.y` 是脚底坐标**（R225 check21 踩坑）：命中盒 foot 在
    锚点 y − 半高处（placeInteraction 约定），按牌面锚点绝对值（如消耗品行 0.78）
    过滤会得到 0 个实体——回合视图按「最上行」等相对聚类定位，或用锚点减 hh 推算。
11. **板面世界高度不定，行定位必须相对化**（R225 check21b 踩坑）：牌桌锚定玩家
    眼位（本服出生点地形 y≈-58），绝对 y 过滤全部失效；以最上行（商品行脚底）为
    基准做行间偏移（如持有消耗品行 = 基准 −1.79）才稳。
12. **vanilla `disconnect.spam` 对非 op 假人踢出**（R226 check22 发现）：持续
    ~1-2 条/秒的命令流即可让非 op 客户端被原版聊天反刷屏踢出（op 豁免——A/B
    实证：同速率 op 假人零踢出、/op 后复测 4/4 存活）。**非插件缺陷**（对真实
    玩家宏刷屏是预期防线）。规约：soak/负载假人一律入 ops.json；另注意 mineflayer
    分散出生点会触发「moved too quickly」位置纠正（harness 物理漂移，无害）。

## 脚本清单（R220 战役）

| 脚本 | 验证内容 | 结果 |
|---|---|---|
| check1/check2 | 牌桌生成/私有可见/退出即弃（/quit+断线）/点击链/服务器计数神谕自检 | 通过（check1 神谕配置错误，check2 修正后全过） |
| check3/check4 | 种子复现（同种子两局同手牌）/出牌计分/失败结算 | 通过（Shift 简介断言正则误报，实际「梅花 9」输出成功） |
| check5/check6 | 智能选牌（对/两对/三条+弃牌钓鱼）/清盲进商店 | 通过（旋转无关投影修正后） |
| check7/check8 | 购买成功路径/确认框两步流（click_event 提取→执行→入账）/150 连点 3s 轰炸/30 命令刷屏 | 5/5 通过 |
| check9 | 23+1 合法长度恶意命令/4 假人并发 soak 150s/TPS/内存 | 通过（evil 输入含超长项需分离，见 check10） |
| check10 | 恶意输入分层：插件层（合法长度 23 条全安全）+ 协议层（超长/§ 注入双防线） | 3/3 通过 |
| check21 | R225 #80：目标类消耗品全息链路（买战车→回合选中→确认框 `@id` 快照→使用成功→卡面变钢铁，无「请选择」错） | 10/10 通过 |
| check21b | R225：商店右键持有星球→`[确认使用][确认出售][取消]` 双按钮→商店内使用成功 | 5/5 通过 |
| check22/22b | R226：use 新语法敌意输入电池 26 条 + 单 bot 118 条高频混合 + 4 假人并发负载（TPS/存活/压力后开局） | 26/26 + 3/3 通过 |
| check23 | R228：GUI 向导六步开局 × 目标类消耗品链路交叉（`tarot:hanged @50` 快照→使用成功→效果落地）+ 双通道竞态（命令抢先开局后 GUI 开始被拒、单会话） | 9/9 通过 |
| check24 | R229：种子聊天 60s 窗口七场景（设置回显/取消/无效保留/命令混发不消耗/返回 GUI 清理/双发原子认领/超时放行） | 10/10 通过 |
| check25 | R229：Vault+EssentialsX 3 假人并发——清盲档 $1 精确到账、失败局 Δ$0、stats.txt 新增行==失败局数全 7 段、wins.txt 不变 | 5/5 通过 |
| check26 | R230：离线种子搜索（真实引擎镜像策略 2/4000 命中 W26A1446）→ 命令驱动实机复现**完整通关**——首个底注 Δ=13（3×1+10）、整局 Δ=204（24×1+8×10+100）三档分毫不差 | 4/4 通过 |
| probe/metaprobe/jsonprobe | 盲注 go 点击聚焦探针/交互元数据/聊天 JSON 结构 | 诊断工具 |

`*-results.json` 为各轮断言结果存档。

## 复跑方式

```bash
cd <服务器目录>/bot && npm install mineflayer
node check2.js   # 退出即弃等
node check8.js   # 购买/确认框/轰炸
node check9.js   # 恶意输入 + 4 bot soak
```

服务器需已部署 balatro jar；ops.json 按要点 1 配置 BalBot/BalBot2。
