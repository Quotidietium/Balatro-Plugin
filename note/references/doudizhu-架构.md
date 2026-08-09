# 参考分析：doudizhu（实现方式参考）

> 定位：这是一个已有的 MC 扑克牌（斗地主）插件，是本项目"在 MC 里做卡牌游戏"的**架构范式参考**。
> 注意：斗地主是多桌多人对局，Balatro 是单人 Roguelike，二者的会话模型不同，但渲染/交互/分层模式可直接借鉴。
> 源码：`REF/doudizhu/`

## 1. 构建与声明

- 构建：**Maven**（`REF/doudizhu/pom.xml`），`groupId=com.fentai, artifactId=doudizhu, version=5.2.2`。
- Java **17**；依赖 `io.papermc.paper:paper-api:1.19.4-R0.1-SNAPSHOT`（`provided`）。
  注释明确：编译目标 1.19.4，产物可在 **1.19.4 ~ 1.21.11+** 运行（Paper 向后兼容）。
- 资源过滤只对 `*.yml` 做变量替换，二进制资源（如 `.nbs` 曲谱）原样拷贝，避免编码损坏。
- `plugin.yml`（`REF/doudizhu/src/main/resources/plugin.yml`）：`api-version: '1.19'`，命令 `doudizhu`（别名 `ddz`），
  权限 `doudizhu.play`（默认 true）/ `doudizhu.admin`（默认 op），
  `softdepend: [GSit, CMI, Vault, PlayerPoints, PlaceholderAPI, CraftEngine, ItemsAdder]`。

## 2. 分层结构（`com.fentai.doudizhu`）

| 包 | 职责 |
|---|---|
| （根）`DoudizhuPlugin` | 主类：装配配置/渲染器/桌管理器，注册命令与监听器，清理残留实体 |
| `game/` | 游戏模型与规则：`Card/Rank/Suit`、`Combo/ComboType/BombKind`、`Rules`、`Deck`、`GameTable`、`DoudizhuTable`、`TableManager`、`GamePhase`、`Seat`、`HintFinder`、`EconomyRules` |
| `render/` `renderer/` | 渲染层：`DisplayUtil`（TextDisplay 生成）、`GameRenderer`（含射线检测）、`CardTableRenderer`/`SecondRenderer`/`ThirdRenderer`/`HoloRenderer` |
| `listener/` | 交互：`CardClickListener`（右键射线交互 + 节流）、`DisplayRecoveryListener`（实体恢复）、`TableProtectionListener`（区域保护） |
| `config/` | 配置：多个 yml 包装类 + `ConfigCompleter`（热补齐字段） |
| `integration/` | 集成：经济（Vault/PlayerPoints）、PlaceholderAPI、坐骑/坐下辅助 `SitHelper`、押注流水 `WagerJournal` |
| `command/` | `DoudizhuCommand`（执行器 + Tab 补全合一） |
| `audio/` `skin/` `stats/` | 音效（NBS 曲谱）、牌皮、统计/排行榜 |

## 3. 渲染方式（核心可借鉴点）

- **不用箱子 GUI**，而是在世界中生成 **`TextDisplay` 实体**做全息渲染（`REF/doudizhu/src/main/java/com/fentai/doudizhu/render/DisplayUtil.java`）：
  - `spawnBase/spawnRect/spawnText/spawnComponent` 生成带背景色/文字/对齐/亮度的 TextDisplay；
  - 用 **`org.joml.Matrix4d/Matrix4f` 世界变换矩阵**定位每张卡牌/牌桌组件；
  - 每个 display 打 **scoreboard tag**（如 `doudizhu_card`、`doudizhu_game_<id>`）用于追踪与清理；
  - `setPersistent(false)` 非持久、`setBrightness(15,15)` 满亮度、`setViewRange(64)`；
  - 私有可见性：`setVisibleByDefault(false)` + `player.showEntity/hideEntity` 实现"只对特定玩家可见"。
- 实体生命周期管理：`cleanupStaleDisplays()` 按 tag 在所有世界扫除残留；`onEnable` 延迟任务里 `recoverDisplays`。
- 启停钩子：`onDisable` 里 `shutdownAll()` + 停曲谱 + 关闭 skin/stats 资源。

## 4. 交互方式

- `CardClickListener`（`REF/doudizhu/.../listener/CardClickListener.java`）监听 `PlayerInteractEvent`：
  - 仅主手 `EquipmentSlot.HAND` + 右键；最大交互距离 `MAX_DISTANCE=8.0`；
  - 通过 `GameRenderer` 的 **射线检测**判断点中什么：`raycastJoin`（加入桌）、`raycastHand`（手牌，返回 `CardHit.cardId`）、`raycastButton`（按钮，返回 `ButtonHit.id`）；
  - **点击节流**：`throttle()` 每玩家 120ms 内只认一次，防抖；
  - 命中即 `consumeInteraction`（取消事件）+ 播放 `UI_BUTTON_CLICK` 音效（不同动作不同音调）。
- 退出/重连：`PlayerQuitEvent`/`PlayerJoinEvent` 由桌 `handleQuit/handleJoin` 处理状态恢复。

## 5. 游戏模型（可直接对照本项目）

- `Card`（`REF/doudizhu/.../game/Card.java`）：`final Rank rank; final Suit suit; final int id;`，按 rank 后按 suit 比较；**相等性以唯一 `id` 为准**（同名同点的两张牌也能区分）。
  → Balatro 同样需要唯一 id（一张牌可能被增强/改花色/复制，不能只靠 rank+suit 标识）。
- 牌型/规则：`Combo/ComboType/BombKind/Rules`、癞子解析 `LaiziComboParser`、提示 `HintFinder`。
- 桌/会话：`GameTable`（座位、阶段）、`DoudizhuTable`（封装渲染器与流程）、`TableManager`（**多桌并发**管理 + 持久化加载）、`GamePhase`（WAITING/BIDDING/PLAYING…）。

## 6. 配置与热更新

- 多个 yml（`config/first/second/third-left/third-right/table/hologram/voice/card-skins`），各自有包装类。
- `ConfigCompleter.ensureXxx()` 在启动时**补齐新增字段**（向后兼容旧配置）。
- `saveResourceIfMissing()`：仅在缺失时释放默认资源，不覆盖玩家已改配置。
- `reload()`：重新加载全部配置 + `reloadTableOptions()`（热更新桌的经济设置）+ `reloadRenderers()`。

## 7. 对本项目的可借鉴/需调整

| 点 | 借鉴 / 调整 |
|---|---|
| 渲染（TextDisplay + 矩阵 + tag + 私有可见） | Balatro 面板更多更复杂（小丑区/消耗品区/手牌/出牌区/商店/补充包），全息渲染工作量大 → **见"渲染方式"决策** |
| 射线交互 + 节流 + 音效反馈 | 若用 GUI 则改为 `InventoryClickEvent`（更简单、更稳） |
| 游戏模型分层（model / rules / table / manager / phase） | **直接借鉴**这套分层 |
| 多桌并发的 TableManager | Balatro 单人 → 改为 `Map<UUID, RunState>`（每玩家一局） |
| 配置热补齐 + 缺失才释放 | **直接借鉴** |
| 唯一 id 的 Card | **必须借鉴**（Balatro 牌有增强/版本/蜡封，状态多变） |
