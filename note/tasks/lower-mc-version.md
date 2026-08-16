# 分支任务：lower-mc-version — 降低 Minecraft 版本需求

> 建分支：2026-08-16，自 main（6174c6a，v0.4.39）。
> 来源：用户指令——「降低 Balatro 插件对 Minecraft 的版本需求，在不改变展示方式、
> 插件功能的情况下，降低所需版本」。

## 目标与约束

- **目标**：把插件可加载的最低 Minecraft 版本从 1.21.11 降到技术下限。
- **约束（用户明示）**：
  1. 展示方式不变——世界全息渲染（TextDisplay/Interaction 实体 + 右键交互）；
  2. 插件功能不变——命令/全息/GUI 向导/服务/API 扩展面全部保留。

## 技术路线（先清点、后动手）

1. 全量清点 main 源码的 Bukkit/Paper/Adventure/JOML API 面（import + 方法调用点）。
2. 逐项确定最低引入版本 → 全局下限。
3. `compileOnly` 换到下限版本的 paper-api（编译期强制不使用更新 API）。
4. 字节码 `--release` 降到下限服务端可用的 Java（17），保证老服务端可加载。
5. `plugin.yml` `api-version` 同步下调。
6. 验证：`./gradlew build` 全量测试 + 真实服务端无头启动（上限端 Papo 1.21.11 +
   下限端 Paper 1.19.4，沿用 R84 无头验证模式）。

## API 清点结论（2026-08-16，建分支时）

| API | 最低版本 | 用途 |
|---|---|---|
| TextDisplay / Display / Interaction 实体 + Transformation(JOML) | **1.19.4** | 全息牌桌与点击命中 |
| Entity#setVisibleByDefault / Player#showEntity | 1.19.3 | 私有可见 |
| io.papermc.paper.event.player.AsyncChatEvent | 1.16.5+ | 种子聊天输入 |
| createInventory(holder,size,Component) / ItemMeta Adventure 重载 | 1.16.5+ | GUI 向导 |
| 其余（Sound/Scheduler/事件/命令/物品/Material 全表） | ≤1.17 | 常规面 |

- 未使用任何 1.19.4 之后的 API（无 `org.bukkit.inventory.MenuType`、`InventoryView`、
  `ServerBuildInfo`、1.20+ Material；`gui.MenuType` 是本项目自有枚举）——
  **硬下限 = 1.19.4**，即全息展示方式的引入版本，与「不改展示方式」约束自洽。
- 源码无 Java 21 专属 API（唯一 `.reversed()` 均为 `Comparator` 的 Java 8 方法）
  → 字节码可降 **--release 17**（1.19.4~1.20.4 服务端要求 Java 17；
  Java 17 字节码在 Java 21 服务端照常加载运行，上限端不受影响）。
- Papo 感知：代码本就未调用 `ServerBuildInfo.papoVersion()`，无兼容负担。

## 结果

（完成后回填：变更清单 / 验证记录 / 版本号）
