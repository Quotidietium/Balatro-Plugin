# 代码库分析报告（2026-08-17）

> 由 codebase-analyzer 工作流生成：5 个并行深度分析通道逐行通读全部 60 个主源码文件
> （含 4 个资源文件、构建配置、基准子项目与测试面汇总），行号均经抽查核实。
> 本报告描述 **v0.4.60** 时点的代码事实；后续代码演进后应重新生成或在各报告头标注漂移。

## 项目快照

| 项 | 值 |
|---|---|
| 项目 | Balatro — Minecraft 小丑牌插件（`cn.quotidietium.balatro:balatro`） |
| 技术栈 | Java 21（`--release 21`）、Gradle Kotlin DSL、Paper API 1.21.11（compileOnly）、JUnit 5 |
| 版本 | 0.4.60（唯一版本源 `build.gradle.kts:6`，经 processResources 注入 plugin.yml） |
| 主源码 | 60 个 `.java`（约 1.2 万行；最大 Engine.java 1294 行、RoundBoard.java 1144 行、BasicJoker.java 1351 行） |
| 测试 | 149 个测试文件 / 530 用例全过；13 个 golden 黄金文件 |
| 构建设施 | `benchmark/` JMH 式基准子项目（11 场景）；`tools/gen-golden.mjs` 黄金生成；`tools/live-bot/` 实机假人 |
| CI/CD | 无（Git 仓库 + 细粒度提交 + 全量测试守门替代） |
| 分析模式 | 完整分析（60 主文件全覆盖通读 + 测试面汇总级） |

## 报告目录

| # | 报告 | 内容 |
|---|---|---|
| 1 | [01-架构分析.md](01-架构分析.md) | 分层结构、包全景、依赖规则、设计模式清单、核心调用链（函数级） |
| 2 | [02-运行原理.md](02-运行原理.md) | 启动装配序列、一局生命周期、状态机全图、出牌计分数据流、错误处理矩阵 |
| 3 | [03-工作流分析.md](03-工作流分析.md) | 开发/发布/测试/基准/审计/实机六类工作流、业务决策树、异常恢复路径 |
| 4 | [04-AI替代方案.md](04-AI替代方案.md) | 各维护工作流 AI 可替代性评估、ROI 矩阵、改造路线图 |
| — | [blueprints/](blueprints/) | 3 份 Skill Blueprint（发布说明撰写 / 审计轮执行 / REF 对照移植） |

## 核心发现

1. **分层纪律严格且可验证**：`engine/` 16+5 文件零 Bukkit import（纯 POJO），依赖方向
   单向（engine ← session ← render/listener/command/gui），api/ 是独立稳定面——这是
   530 个测试与种子复现红线得以成立的结构基础。
2. **性能手法成体系**：位打包（Card 22bit，Card.java:26-33）、ThreadLocal 草稿区
   （HandEval.java:42）、折叠建流（Rng.java:73-176）、静态共享池（Shop.java:22-62）、
   实体池+差量更新（RoundBoard.java:151-152, 280-295）——且每处都有等价性守门测试。
3. **安全防线纵深**：交互归属白名单（RoundBoard.ownsInteraction）→ 150ms 节流 →
   dispatch 异常隔离（BoardListener.java:74-80）→ 命令层 TOCTOU 期望身份校验
   （BalatroCommand.java:579-592）→ fire*/safeService 双层第三方异常隔离
   （BalatroPlugin.java:128-177 / GameSession.java:308-314）。
4. **文档与代码高度同步**：审计方法学（note/审计方法学.md）的 11 把类别锁 + 9 条不变量
   均有对应测试类；表述-实现三向核对（代码/golden/desc）是制度化流程。
5. **无 CI 但守门不弱**：无 .github/workflows；替代物是「全量测试 + 变异验证类别锁 +
   冷构建复验（R174）+ sinkΔ 逐位一致」的手动但可证明的验证体系。

## 关键建议（详见 04 报告）

1. 最高 ROI 的 AI 化点是**版本发布说明撰写**与**审计轮执行**（后者本项目已实际由 AI
   深度参与 224 轮，蓝图是对既有实践的制度化固化）。
2. 实机验证清单 #18（命中盒重叠真实客户端抽查）是当前唯一未闭合验证面。
3. 若引入 CI，最小可行管线 = `./gradlew clean test` + 产物 jar 完整性检查（测试面
   已就绪，无框架迁移成本）。
