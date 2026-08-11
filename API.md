# Balatro 开发者 API

本插件提供两层扩展 API，供第三方插件接入，**无需 fork 本插件**：

1. **自定义事件**（`api.event`）——在关键节点（开局/计分/盲注结算/底注通过/本局结束）发出，可监听、部分可取消。**推荐的集成方式。**
2. **服务接口**（`api.service`）——经济 / 统计 / 排行榜 / 奖励四个接口，默认实现可被替换。

> 所有 API 都在 `cn.quotidietium.balatro.api` 包下；事件在主线程同步发出。插件名为 `Balatro`，主类 `cn.quotidietium.balatro.BalatroPlugin`。

## 依赖

在你的插件 `plugin.yml` 中声明：

```yaml
depend: [Balatro]        # 强依赖：必须在 Balatro 之后加载
# 或
softdepend: [Balatro]    # 软依赖：可选接入
```

编译时把本插件 jar 作为 `compileOnly` 依赖即可（API 类已包含在产物 jar 中）。

---

## 一、自定义事件

包：`cn.quotidietium.balatro.api.event`。用标准 `@EventHandler` 监听。

| 事件 | 触发时机 | 可取消 | 主要字段 |
|---|---|:---:|---|
| `BalatroRunStartEvent` | 开始一局 | ✅ | `playerId, seed, deckKey, stakeIdx` |
| `BalatroHandScoreEvent` | 每次出牌计分后 | ❌ | `playerId, handType, score, roundScore, blindTarget, handsLeft` |
| `BalatroBlindResultEvent` | 盲注结算（通过/失败） | ❌ | `playerId, ante, blindType, target, score, cleared` |
| `BalatroAnteClearEvent` | 通过一个底注（击败其 Boss） | ❌ | `playerId, ante` |
| `BalatroRunEndEvent` | 一局结束（通关 ante 8 或失败） | ❌ | `playerId, won, anteReached, seed, deckKey, stakeIdx` |

字段说明：`ante` 为底注序号（1..8，无尽模式为 9+）；`blindType` 为 `small`/`big`/`boss`；`handType` 为牌型 key（`high`/`pair`/`twopair`/`three`/`straight`/`flush`/`full`/`four`/`sflush`/`royal`/`five`/`fhouse`/`ffive`，共 13 种）；`stakeIdx` 为赌注档位（0..7）。

> **无尽模式的 RunEnd 语义**：玩家通关 ante 8 时触发一次（`won=true`）；若随后进入
> 无尽模式并在更高底注失败，会**再触发一次**（`won=false`，`anteReached` 为最终底注）。
> 计次/发奖类集成通常只响应 `won==true` 的那次（`BalatroRunEndEvent.isWon()`）。

### 示例：监听通关并记录

```java
import cn.quotidietium.balatro.api.event.BalatroRunEndEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MyListener implements Listener {
    @EventHandler
    public void onEnd(BalatroRunEndEvent e) {
        if (e.isWon()) {
            // 玩家通关 ante 8
            broadcast(e.getPlayerId() + " 通关小丑牌！种子=" + e.getSeed());
        }
    }
}
```

### 示例：拦截开局

```java
@EventHandler
public void onStart(BalatroRunStartEvent e) {
    if (inDuel(e.getPlayerId())) {
        e.setCancelled(true); // 取消则不开始本局
    }
}
```

---

## 二、服务接口

包：`cn.quotidietium.balatro.api.service`。四个接口，默认实现可运行期替换。

### `EconomyService` — 外部经济（Vault/PlayerPoints 等）

把"过关奖励"发到服务器经济系统。**注意**：与局内金钱（`state.money`）相互独立。

```java
public interface EconomyService {
    long balance(UUID player);
    boolean has(UUID player, long amount);
    void deposit(UUID player, long amount);
    void withdraw(UUID player, long amount);
}
```

### `StatsService` — 局结果统计

```java
public interface StatsService {
    void record(RunSummary summary);
    List<RunSummary> all();
}
```

### `LeaderboardProvider` — 排行榜

两种口径：
- `top(n)`：单局记录，优先通关（`won`），其次到达底注（`anteReached`），最后时间。
- `topAggregated(n)`（0.3.9 新增）：按玩家聚合，每玩家一行。排序：最高底注降序 → 通关次数降序 → 玩家名升序。

```java
public interface LeaderboardProvider {
    List<RunSummary> top(int n);
    RunSummary bestOf(UUID player);
    default List<PlayerStat> topAggregated(int n) { return List.of(); } // 0.3.9 新增
}
```

### `WinCounter` — 通关计数器（0.3.9 新增）

统计每个玩家累计通关 ante 8 的次数，供聚合排行榜使用。默认 `FileWinCounter`（`wins.txt` 持久化）。

```java
public interface WinCounter {
    void increment(UUID player);
    int count(UUID player);
}
```

### `RewardService` — 过关奖励策略

```java
public interface RewardService {
    void onBlindCleared(UUID player, int ante, String blindType);
    void onAnteCleared(UUID player, int ante);
    void onRunEnd(UUID player, boolean won, int anteReached);
}
```

> **奖励挂钩的两种方式**：实现 `RewardService` 替换默认策略；**或**直接监听 `BalatroBlindResultEvent`/`BalatroAnteClearEvent`/`BalatroRunEndEvent`。二者可并存。

### `RunSummary`（record）

```java
public record RunSummary(
    UUID playerId, boolean won, int anteReached,
    String seed, String deckKey, int stakeIdx, long epochMilli
) {}
```

### `PlayerStat`（record，0.3.9 新增）

按玩家聚合的排行榜条目。

```java
public record PlayerStat(UUID playerId, String playerName, int bestAnte, int winCount) {}
```
```

---

## 三、获取与替换服务

通过插件主类取 `Services` 注册表：

```java
BalatroPlugin balatro = (BalatroPlugin) Bukkit.getPluginManager().getPlugin("Balatro");
Services services = balatro.services();
```

读取：

```java
services.economy();       // 当前 EconomyService
services.stats();         // 当前 StatsService
services.leaderboard();   // 当前 LeaderboardProvider
services.reward();        // 当前 RewardService
services.winCounter();    // 当前 WinCounter（0.3.9 新增）
```

运行期替换默认实现（建议在 `onEnable` 中、玩家开始游戏前替换）：

```java
balatro.services().setEconomy(new VaultEconomyAdapter());
balatro.services().setStats(new SqliteStats());
balatro.services().setLeaderboard(new MyLeaderboard());
balatro.services().setReward(new MyRewardService());
balatro.services().setWinCounter(new MyWinCounter());   // 0.3.9 新增
```

**默认实现**：经济为 `NoOpEconomy`（若服务端装了 Vault 则自动切换为 Vault 适配）、统计为文件持久化、排行榜为基于统计的内存排序、奖励为 `NoOpReward`（不发奖）、通关计数器为 `FileWinCounter`（`wins.txt` 持久化）。

> **重绑约定**：`setStats` / `setWinCounter` 会同步把新实现注入默认的 `MemoryLeaderboard`
> （聚合排行依赖二者），替换后立即生效，无需额外操作。若替换为自定义 `LeaderboardProvider`
> 则由其自行决定数据源。

---

## 四、完整示例：通关发金钱奖励

```java
public class RewardPlugin extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onRunEnd(BalatroRunEndEvent e) {
        if (!e.isWon()) return;
        BalatroPlugin balatro = (BalatroPlugin) Bukkit.getPluginManager().getPlugin("Balatro");
        // 通关 ante 8 奖励 1000（经 EconomyService 发放，自动适配 Vault）
        balatro.services().economy().deposit(e.getPlayerId(), 1000);
    }
}
```

---

## 设计原则

- **事件先行**：绝大多数扩展（记录/排名/奖励/实时展示）只需监听事件，不改引擎与渲染。
- **接口稳定**：事件与服务接口定义在 `api` 包，向后兼容；默认实现可被配置或其他插件替换。
- **引擎无感知**：引擎层只经纯逻辑上报事件、不直接依赖 Bukkit 服务；所有 Bukkit 交互集中在事件桥与渲染层。
