package cn.quotidietium.balatro.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.api.service.EconomyService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * EconomyReward（#79 修复守门）：默认奖励管线经 EconomyService 发放的精确行为。
 * 覆盖：三档金额精确到账 / 档位清零不发 / 负数配置防笔误不发 / 失败局不发 /
 * 运行期经济实现替换即时生效（Supplier 现取）/ 通关 win 只在 won=true 发。
 */
class EconomyRewardTest {

    /** 记账假经济：可运行期替换引用以验证 Supplier 现取语义。 */
    private static final class FakeEconomy implements EconomyService {
        volatile EconomyService delegate;
        final List<String> ledger = new ArrayList<>();

        @Override
        public long balance(UUID player) { return 0; }

        @Override
        public boolean has(UUID player, long amount) { return false; }

        @Override
        public void deposit(UUID player, long amount) {
            if (delegate != null) { delegate.deposit(player, amount); return; }
            ledger.add(player + ":" + amount);
        }

        @Override
        public void withdraw(UUID player, long amount) { }
    }

    @Test
    void threeTiersDepositExactAmounts() {
        FakeEconomy eco = new FakeEconomy();
        EconomyReward r = new EconomyReward(() -> eco, 1, 10, 100);
        UUID p = UUID.randomUUID();
        r.onBlindCleared(p, 1, "small");
        r.onAnteCleared(p, 1);
        r.onRunEnd(p, true, 8);
        assertEquals(List.of(p + ":1", p + ":10", p + ":100"), eco.ledger);
    }

    @Test
    void lostRunPaysNothing() {
        FakeEconomy eco = new FakeEconomy();
        EconomyReward r = new EconomyReward(() -> eco, 1, 10, 100);
        UUID p = UUID.randomUUID();
        r.onRunEnd(p, false, 3);
        assertEquals(List.of(), eco.ledger);
    }

    @Test
    void zeroAndNegativeTiersDisabled() {
        FakeEconomy eco = new FakeEconomy();
        EconomyReward r = new EconomyReward(() -> eco, 0, -5, 100); // blind 关、ante 笔误为负
        UUID p = UUID.randomUUID();
        r.onBlindCleared(p, 1, "boss");
        r.onAnteCleared(p, 1);
        r.onRunEnd(p, true, 8);
        assertEquals(List.of(p + ":100"), eco.ledger, "仅 win 一笔到账（0 档与负数档不发）");
    }

    @Test
    void runtimeEconomyReplacementTakesEffectImmediately() {
        FakeEconomy primary = new FakeEconomy();
        FakeEconomy secondary = new FakeEconomy();
        FakeEconomy switcher = new FakeEconomy();
        switcher.delegate = primary;
        EconomyReward r = new EconomyReward(() -> switcher, 1, 10, 100);
        UUID p = UUID.randomUUID();
        r.onBlindCleared(p, 2, "big");
        assertEquals(1, primary.ledger.size());
        switcher.delegate = secondary; // 模拟 setEconomy 运行期替换
        r.onBlindCleared(p, 2, "big");
        assertEquals(1, primary.ledger.size(), "替换后不再进旧实现");
        assertEquals(1, secondary.ledger.size(), "新实现立即接账");
    }

    @Test
    void nullEconomyIsSilentlyIgnored() {
        EconomyReward r = new EconomyReward(() -> null, 1, 10, 100);
        r.onAnteCleared(UUID.randomUUID(), 1); // 不抛异常即通过
        r.onRunEnd(UUID.randomUUID(), true, 8);
    }
}
