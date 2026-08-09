package cn.quotidietium.balatro.api.service;

import java.util.UUID;

/**
 * 过关奖励策略（内部钩子，由会话层在对应节点直接调用）。
 * 默认 {@code NoOpReward} 不发奖；后续可配置为按盲注/底注/通关经 {@link EconomyService} 发奖，
 * 或发物品/执行命令。
 *
 * <p>外部插件如需挂钩奖励，监听 {@code BalatroBlindResultEvent}/{@code BalatroAnteClearEvent}/
 * {@code BalatroRunEndEvent} 即可，二者并存。
 */
public interface RewardService {

    /** 通过一个盲注后调用。 */
    void onBlindCleared(UUID player, int ante, String blindType);

    /** 通过一个底注（击败其 Boss）后调用。 */
    void onAnteCleared(UUID player, int ante);

    /** 一局结束调用（won=是否通关）。 */
    void onRunEnd(UUID player, boolean won, int anteReached);
}
