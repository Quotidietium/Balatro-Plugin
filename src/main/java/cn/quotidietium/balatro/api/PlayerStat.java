package cn.quotidietium.balatro.api;

import java.util.UUID;

/**
 * 按玩家聚合的排行榜条目（0.3.9 新增）。
 *
 * <p>排行榜按玩家聚合显示：每玩家一行，含「最高到达底注（含无尽）」与「累计通关次数」。
 * 排序：最高底注降序 → 通关次数降序 → 玩家名称升序（相同时并列）。
 *
 * @param playerId   玩家 UUID
 * @param playerName 玩家名称（排序兜底用；可能为 null/空，取时需处理）
 * @param bestAnte   该玩家所有记录中的最大 anteReached（含无尽模式 ante 9+）
 * @param winCount   该玩家累计通关 ante 8 的次数（独立计数器）
 */
public record PlayerStat(UUID playerId, String playerName, int bestAnte, int winCount) {
}
