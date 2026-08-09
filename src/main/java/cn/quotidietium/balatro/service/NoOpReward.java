package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.RewardService;
import java.util.UUID;

/** 默认奖励：空操作。后续配置为经 EconomyService 发奖/发物品/执行命令。 */
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
