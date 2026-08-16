# benchmark — 性能基准子项目

零外部依赖（纯 JDK + 引擎主源集）。所有场景由**固定种子**驱动、每批工作量恒定；
引擎优化只要行为不变（458 测试锁定种子复现），前后数字可直接对比。

## 场景

| 名称 | 覆盖 |
|---|---|
| `rngNext` | Rng.Stream.next() 核心吞吐 |
| `streamLookup` | RunState.stream(name) 命名流查找 |
| `handEval` | HandEval.evaluate 牌型判定（全息实时牌型评估热路径） |
| `playHand` | Engine.playHand 计分管线（5 小丑钩子负载） |
| `discard` | Engine.discard 弃牌管线 |
| `roundCycle` | createRun + 整回合打到商店 |
| `shopGen` | 商店生成 + 两次重掷 |
| `fullRun` | 整局模拟（含商店买小丑，与 E2E 测试同口径） |

## 用法

```bash
# 测量（结果写入 benchmark/results/<label>/<scenario>.txt）
./gradlew :benchmark:run --args="--label baseline"

# 只跑部分场景
./gradlew :benchmark:run --args="--label try1 --scenarios handEval,playHand"

# 对比两个标签（打印 markdown 表 + 几何平均加速比）
./gradlew :benchmark:run --args="--compare baseline current"
```

## 方法学

- 每场景：3 批预热 + 9 批测量，报告 ns/op 的 **中位数**（抗噪声）/最小值/均值/p95。
- 分配字节/op 经 `ThreadMXBean.getThreadAllocatedBytes`（TLAB 估算，看趋势非精确值）。
- `Blackhole.SINK` 汇总全部计算结果防死码消除。
- 对比报告归档于 `note/report/perf/`。

> ⚠ 基线锁定（`--label baseline`）后**不得再改场景批量/种子**，否则前后不可比。
