package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.RewardService;
import java.util.UUID;

/** 空操作奖励：config {@code reward.economy.enabled=false} 时的默认。历史上曾长期作为
 * 默认实现（表述-实现不符 #79），0.4.60 起默认改为 {@link EconomyReward}；本类保留供
 * 显式关闭与第三方实现参照。 */
public final class NoOpReward implements RewardService {
    @Override
    public void onBlindCleared(UUID player, int ante, String blindType) {
    }

    @Override
    public void onAnteCleared(UUID player, int ante) {
    }

    @Override
    public void onRunEnd(UUID player, boolean won, int anteReached) {
    }
}
