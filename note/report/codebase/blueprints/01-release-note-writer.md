# Skill Blueprint: release-note-writer（版本发布说明撰写）

> 自动生成自 codebase-analyzer（2026-08-17）。
> 源模块：note/release/ 发布流程（73 份既有说明）+ git 细粒度提交规约。

---

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 推荐 Skill 名称 | `balatro-release-note-writer` |
| 用途 | 从 git 历史与构建结果生成符合项目四要素结构的版本发布说明 |
| AI 替代等级 | 🤖 完全 AI 化（26/30） |
| 实施优先级 | 🥇 Quick Win |
| 源依据 | note/release/0.1.0.md~0.4.60.md（结构样板）、note/README.md 约定 |

## 2. 触发场景与关键词

- "发版了，写 release note" / "写 0.4.61 的版本说明"
- "版本改动整理到 note/release"
- 提交信息含新版本号且 note/release/<版本号>.md 尚不存在时

推荐 description 触发词：
```yaml
description: >-
  Write version release notes for the Balatro-Plugin project from git history.
  Triggered by: "写发布说明", "release note", "发版说明", "版本说明".
```

## 3. 输入输出契约

| 项 | 契约 |
|------|------|
| 输入 | ① `git log <上一版本commit>..HEAD --oneline`（细粒度）② `git diff --stat` ③ `./gradlew build` 射出（测试数）④ 上一份 release note（语气/结构参照） |
| 前置条件 | build.gradle.kts:6 版本号已更新；构建与测试全绿；工作树干净 |
| 输出 | `note/release/<版本号>.md`，含四节：**基线**（MC/Paper 版本+轮次背景）、**改动**（按 fix/test/docs 分组，每条可追溯 commit）、**兼容性**（默认行为变更/API 变更/配置迁移）、**验证**（测试数、实机/基准证据） |
| 不变式 | 每条改动对应至少一笔真实 commit；验证节只写实际执行过的验证；数字（测试数/版本号）从构建输出与 build.gradle.kts 现取，禁止凭记忆 |
| 错误场景 | commit 语义不明 → 在产物中标注「待确认」并提示用户，不得猜测（用户全局红线：歧义须问） |

## 4. 依赖清单

| 依赖 | 用途 | 来源 |
|------|------|------|
| git log/diff | 改动事实源 | 仓库本地 |
| build.gradle.kts | 版本号唯一源 | :6 |
| 上一份 release note | 结构与语气模板 | note/release/ |
| 测试输出 | 验证节数据 | ./gradlew build |

## 5. Skill 工作流设计

```markdown
## Workflow / Steps
1. 确定版本区间：上一 release 对应 commit..HEAD；读取新版本号。
2. 收集事实：git log --oneline 全列 + diff --stat + 测试输出摘要。
3. 分组改写：按 fix/test/docs/性能 前缀分组，融合为面向读者的条目（保留轮次号引用）。
4. 兼容性判断：扫描 diff 是否触及 config.yml/plugin.yml/api/ 默认行为路径。
5. 按四要素成文；风格对齐上一份（中文、简洁祈使、证据内联）。
6. 写入 note/release/<版本号>.md。

## Constraints
- Never 编造验证证据（只引用实际构建/实机输出）。
- Never 写 co-author 或协作者署名（用户全局规则）。
- Always 保持与既有 73 份说明一致的四节结构。
- 若发版含行为变更，Always 在兼容性节给出配置迁移路径。
```

## 6. 所需工具权限

`Read`（git 输出/模板）、`Write`（note/release/）、`Bash`（git log/diff/gradle）。

## 7. 使用示例

### ✅ Do This
```
输入：git log 显示 3 笔（fix #80 修复 + test 回归 + docs 更新），0.4.61
输出：note/release/0.4.61.md，含基线/改动三条/兼容性(无)/验证(530+2 测试全过)
```
### ❌ Not This
```
错误：凭对话记忆写"实测通过"但本会话未运行构建 → 违反禁编造红线
```

## 8. 参考材料

- 结构样板：note/release/0.4.60.md（含修复+实机验证+兼容性三域的完整范例）
- 约定：note/README.md「版本发布说明」节
