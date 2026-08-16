# 分支任务：lower-mc-version — 降低 Minecraft 版本需求

> 建分支：2026-08-16，自 main（6174c6a，v0.4.39）。
> **分支定位（用户明示，2026-08-16）：独立分支，不与 main 合并。**
> main 保持 1.21.11 基线独立演进；本分支是并行的「低版本兼容」产物线，
> 版本号 0.4.40 为本分支局部编号——main 日后发版若用到同号需自行错开。
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

## 结果（2026-08-16 完成，发版 v0.4.40）

- **变更清单**（4 文件）：
  1. `build.gradle.kts`：paper-api `1.21.11` → `1.19.4-R0.1-SNAPSHOT`；`--release 21` → `17`；版本 0.4.40。
  2. `plugin.yml`：`api-version: '1.21.11'` → `'1.19'`。
  3. `GuiItems.glint()`：`setEnchantmentGlintOverride`（1.20.5+ API，编译期清点时唯一超出 1.19.4 的调用）
     → `SILK_TOUCH` 附魔 + `HIDE_ENCHANTS` 伪光效（1.13~1.21 通用，视觉等价；SILK_TOUCH 常量名
     在 1.19.4 枚举与 1.20.5+ 接口字段同名，跨版本字段引用安全）。
  4. `PluginYmlConsistencyTest`：api-version 锁同步为 `'1.19'`。
- **验证**：
  - `./gradlew build`：**458 测试 0 失败 0 错误**（与 0.4.39 持平）；产物字节码 major 61（Java 17）。
  - 上限端无头启动（Papo bundler 1.21.11，Java 21）：插件经 Paper PluginRemapper 重映射
    （spigot-mapped 产物 → mojmap 运行时的标准兼容机制，218ms）后加载/启用零异常，Done 15.2s。
  - 下限端无头启动（Paper 1.19.4 build 550）：直接加载/启用零异常，Done 6.1s。
  - 两端均用最终 0.4.40 jar 复验（验证记录见 `note/release/0.4.40.md`）。
- **环境备注**：
  - Paper 1.19.4 paperclip 首跑需从 Mojang CDN 下载原版 jar，本机 Java TLS 过不了（网络拦截），
    已用 curl 从 piston-data 预取放入 `cache/mojang_1.19.4.jar` 绕过。
  - 下限端服务端在 Java 21 上启动（本机无 Java 17）；17 字节码在 17+ JVM 均可加载，不构成风险。
  - 会话中 `versions/1.21.11/` 下的 7 个 Papo/构建 jar 疑似被外部进程清除（本会话命令未触碰该目录），
    上限端验证改用 `REF/Papo/paper-server/build/libs/paper-bundler-1.21.11-*.jar`，已向用户报告。
