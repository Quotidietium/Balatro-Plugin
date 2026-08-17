# Skill Blueprint 索引

> 自动生成自 codebase-analyzer（2026-08-17）。每个 Blueprint 是自包含的 Skill 设计
> 规格，可直接作为 skill-for-skills 或手动创建 Skill 的输入。

| # | Blueprint | 组件 | AI 等级 | 优先级 | 文件 |
|---|-----------|------|---------|--------|------|
| 1 | release-note-writer | 版本发布说明撰写 | 🤖 完全 AI 化（26/30） | 🥇 Quick Win | [01-release-note-writer.md](01-release-note-writer.md) |
| 2 | audit-round-executor | 审计轮执行 | 🧑‍💻 AI 辅助（16/30） | 🥈 Strategic | [02-audit-round-executor.md](02-audit-round-executor.md) |
| 3 | ref-porting-assistant | REF 对照移植新内容 | 🧑‍💻 AI 辅助（20/30） | 🥈 Strategic | [03-ref-porting-assistant.md](03-ref-porting-assistant.md) |

## 实施路线图

### 立即实施（Quick Win）
1. **release-note-writer** — 输入零增量（细粒度 commit log 已承载全部语义），
   每版必用，预期节省每版 30-60 分钟。

### 规划实施（Strategic）
2. **audit-round-executor** — 对本项目已发生 224 轮的实践做制度化固化；
   需人工介入选题优先级与决策拍板。
3. **ref-porting-assistant** — 仅 REF 原版更新时触发；黄金重生成兜底正确性。

### 明确不实施
- **运行时 AI 化** — 确定性引擎结构性排除（种子复现红线），见
  [../04-AI替代方案.md](../04-AI替代方案.md) §2.7。
