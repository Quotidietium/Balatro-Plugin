package cn.quotidietium.balatro.api.service;

import java.util.UUID;

/**
 * 过关奖励策略（内部钩子，由会话层在对应节点直接调用）。
 * 默认 {@code EconomyReward}（0.4.60 起）经 {@link EconomyService} 按配置发放
 * （config {@code reward.economy.*}，可整体关闭或按档清零）；也可替换为发物品/执行命令的实现。
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
