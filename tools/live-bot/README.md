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

## 脚本清单（R220 战役）

| 脚本 | 验证内容 | 结果 |
|---|---|---|
| check1/check2 | 牌桌生成/私有可见/退出即弃（/quit+断线）/点击链/服务器计数神谕自检 | 通过（check1 神谕配置错误，check2 修正后全过） |
| check3/check4 | 种子复现（同种子两局同手牌）/出牌计分/失败结算 | 通过（Shift 简介断言正则误报，实际「梅花 9」输出成功） |
| check5/check6 | 智能选牌（对/两对/三条+弃牌钓鱼）/清盲进商店 | 通过（旋转无关投影修正后） |
| check7/check8 | 购买成功路径/确认框两步流（click_event 提取→执行→入账）/150 连点 3s 轰炸/30 命令刷屏 | 5/5 通过 |
| check9 | 23+1 合法长度恶意命令/4 假人并发 soak 150s/TPS/内存 | 通过（evil 输入含超长项需分离，见 check10） |
| check10 | 恶意输入分层：插件层（合法长度 23 条全安全）+ 协议层（超长/§ 注入双防线） | 3/3 通过 |
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
