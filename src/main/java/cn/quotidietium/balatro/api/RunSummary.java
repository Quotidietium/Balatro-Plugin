package cn.quotidietium.balatro.api;

import java.util.UUID;

/**
 * 一局结束时的摘要，用于统计/排行榜。由会话层在 {@code BalatroRunEndEvent} 时构造。
 *
 * @param playerId    玩家
 * @param won         是否通关（ante 8）
 * @param anteReached 到达的底注序号
 * @param seed        种子（可复现）
 * @param deckKey     牌组
 * @param stakeIdx    赌注档位（0..7）
 * @param epochMilli  结束时间（毫秒）
 */
public record RunSummary(UUID playerId, boolean won, int anteReached, String seed, String deckKey, int stakeIdx,
                         long epochMilli) {
}
