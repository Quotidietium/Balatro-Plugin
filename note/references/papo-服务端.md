# 参考分析：papo（目标服务端）

> 定位：本项目插件运行的**服务端**。Papo 是 **Paper 1.21.11 的性能优化 fork**。
> 源码：`REF/Papo/`

## 1. 版本与工具链（`REF/Papo/gradle.properties`）

| 项 | 值 |
|---|---|
| 游戏版本（红线，不可变） | **Minecraft 1.21.11**（`mcVersion=1.21.11`） |
| API 版本 | **1.21.11**（`apiVersion=1.21.11`，用于 `paper-plugin.yml`/`plugin.yml`） |
| Paper API 坐标 | `io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT` |
| Papo 自有版本 | `papoVersion=0.32.1`（独立语义化版本，从 0.1.0 起） |
| Java 工具链 | **JDK 21**（`release=21`，固定） |
| Gradle | **9.4.1**（fork 从上游 9.2.0 提升，因本机网络问题，见 `note/build.md`） |
| paperweight | `2.0.0-beta.19`（须停留此 beta；上游 26.x 的 beta.21 会破坏构建脚本） |
| 产物 | `paper-server/build/libs/Papo-1.21.11-0.32.1.jar` |

## 2. 关键结论：API 零变更，无自定义插件 API

- **没有任何 `papo.*` 自定义包**（`paper-api`/`paper-server` 下无 `papo` 目录、无含 papo 的 java 文件路径）。
- `paper-api/src/` 是**直接提交的源码**（无 patch 机制），整棵树 = 上游 Paper 1.21.11 API + 仅 3 处品牌改动：
  - `io/papermc/paper/ServerBuildInfo.java`：新增 `default Optional<String> papoVersion()`（非 Papo 返回空）；
  - `org/bukkit/Bukkit.java` 的 `getVersionMessage()` 改为读取 `papoVersion()` 显示 "running Papo version …"；
  - `paper-api/src/test/.../TestServerBuildInfo.java`：上述测试桩。
- Papo 的所有定制都在 **`paper-server/patches/features/`**（~209 个补丁），是**服务端（NMS）性能优化**：
  热路径去分配（NBT/网络编码/碰撞/寻路）、**事件零监听器时跳过构造与触发**（行为等价，有监听器时与原版一致）、
  枚举 `values()` 缓存、`Identifier.toString()` 懒缓存、区域文件压缩级别可配、Inflater/Deflater ThreadLocal 池化、
  0209 的 per-chunk 掉落物上限（`paper-world.yml`，默认 -1 关闭）等。
- **红线**（每版 release 说明都强调）：API 零变更、行为等价（包顺序/内容不变）、游戏版本固定 1.21.11。

> 推论：**本项目就是一个普通 Paper 1.21.11 插件**。"基于 Papo 的 API" = 标准 Paper 1.21.11 API。
> 不要依赖任何 `papo.*` 包（不存在）。Papo 仅是"插件跑在哪个服务端上"，不是编译期依赖。
> 唯一可选的 Papo 感知调用：`ServerBuildInfo.buildInfo().papoVersion()`（运行时显示用）。

## 3. 插件要用到的标准 Paper 1.21.11 API（GUI/物品/事件）

> 全部是 `org.bukkit.*` / `io.papermc.paper.*` 标准包，非 Papo 专属。

- **箱子 GUI**：`Bukkit.createInventory(InventoryHolder, int size, Component title)`（size 为 9 的倍数，9~54）；
  或较新的 `org.bukkit.inventory.MenuType.GENERIC_9X1 … GENERIC_9X6` + `InventoryViewBuilder`。
- **打开/关闭**：`Player.openInventory(Inventory)`、`HumanEntity.closeInventory()`、`InventoryView.close()`。
- **点击处理**：`org.bukkit.event.inventory.InventoryClickEvent`（含 `ClickType`/`InventoryAction`/`SlotType`/`rawSlot`，
  `setCancelled(boolean)`）；`InventoryDragEvent`/`InventoryCloseEvent`/`InventoryOpenEvent`。
  ⚠ 处理器内**不要同步调用** `openInventory`/`closeInventory`，须 `BukkitScheduler.runTask(...)` 下一 tick 执行。
- **物品**：`ItemStack` + `ItemMeta`（`displayName(Component)`/`lore(List<Component>)`/`ItemFlag`/`setCustomModelData`）；
  Paper 增强了 `io.papermc.paper.inventory.*`（数据组件相关）。
- **文本**：Kyori Adventure `Component`（Paper 原生，非旧式 `§` 颜色码）。
- **每玩家状态**：`Map<UUID, RunState>` 挂在 `JavaPlugin` 上，`PlayerQuitEvent`/`InventoryCloseEvent` 清理。

## 4. Display 实体 API（若沿用 doudizhu 全息渲染）

doudizhu 已验证可行的 1.19.4+ Display 实体 API（1.21.11 同样支持）：
- `world.spawnEntity(anchor, EntityType.TEXT_DISPLAY)` / `ItemDisplay`；
- `setTransformationMatrix(Matrix4f)`（`org.joml.Matrix4f`）做世界定位；
- `setBillboard` / `setBrightness` / `setViewRange` / `setAlignment` / `setBackgroundColor`；
- 私有可见：`setVisibleByDefault(false)` + `player.showEntity(plugin, e)` / `hideEntity(plugin, e)`；
- 追踪/清理：`entity.addScoreboardTag(...)` + `setPersistent(false)`。

## 5. Papo 自身的 note 目录（约定来源）

`REF/Papo/note/`：`build.md`（构建环境/补丁流程）、`optimizations.md`（逐批优化日志）、
`release/<版本>.md`（每版改动）、`report/perf/*.md`（JMH 微基准报告）、`check_patch_counts.py`/`make_patch.py`（维护脚本）。
本项目 `note/` 的目录约定即参照此组织（见 `note/README.md`）。

## 6. 构建环境备忘（来自 `REF/Papo/note/build.md`）

- JDK 21 在 PATH 可用即可（Gradle toolchains 自动探测，无需手设 `JAVA_HOME`）。
- 首次 `./gradlew` 需联网下载 Gradle 发行版与依赖（`repo.papermc.io`、Mojang CDN）；
  Papo 因本机网络把 wrapper 提升到 9.4.1（已提交）。
- 插件侧构建（本项目）依赖 `repo.papermc.io` 拉 `paper-api:1.21.11-R0.1-SNAPSHOT`。
