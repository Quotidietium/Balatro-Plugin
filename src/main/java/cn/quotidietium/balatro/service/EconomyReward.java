package cn.quotidietium.balatro.service;

import cn.quotidietium.balatro.api.service.EconomyService;
import cn.quotidietium.balatro.api.service.RewardService;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 默认奖励策略（0.4.60 起）：把过关节点经 {@link EconomyService} 发放到服务器经济
 * （对齐项目计划书 §7.2「默认转发到 EconomyService（可关闭）」——此前 NoOpReward
 * 为空操作，清单 #14「奖励到账」承诺落空，属表述-实现不符，本类补全）。
 *
 * <p>金额来自 config.yml 的 {@code reward.economy.*}（默认 盲注 1 / 底注 10 / 通关 100，
 * 全部可配为 0 单独关闭某档；{@code enabled: false} 时整体回落 NoOpReward）。
 * 经济实现经 {@link Supplier} 在每次发放时现取——运行期 setEconomy 替换后立即生效。
 * 无 Vault 时 EconomyService 为 NoOp，deposit 无副作用，本策略等同空操作。
 */
public final class EconomyReward implements RewardService {

    private final Supplier<EconomyService> economy;
    private final long blind;
    private final long ante;
    private final long win;

    public EconomyReward(Supplier<EconomyService> economy, long blind, long ante, long win) {
        this.economy = economy;
        this.blind = blind;
        this.ante = ante;
        this.win = win;
    }

    @Override
    public void onBlindCleared(UUID player, int anteIdx, String blindType) {
        deposit(player, blind);
    }

    @Override
    public void onAnteCleared(UUID player, int anteIdx) {
        deposit(player, ante);
    }

    @Override
    public void onRunEnd(UUID player, boolean won, int anteReached) {
        if (won) deposit(player, win);
    }

    private void deposit(UUID player, long amount) {
        if (amount <= 0) return; // 0/负数=该档关闭；负数一律不发（防配置笔误倒扣）
        EconomyService e = economy.get();
        if (e == null) return;
        e.deposit(player, amount);
    }
}
