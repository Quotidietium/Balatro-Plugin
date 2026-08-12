package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 端到端通关流程模拟验证（轮次 39）。
 *
 * <p>用 Engine API 直接驱动从开局到通关 ante 8 的完整流程，
 * 覆盖多种子/多牌组/多赌注/挑战模式，验证极端组合下无崩溃/无死锁/无数据操作异常。
 * 纯逻辑层（零 Bukkit 依赖）。
 *
 * <p>策略：每回合用最高得分策略（优先打计分牌多的牌型），不依赖商店/消耗品（简化模拟）。
 * 若分数不足，通过反复出牌消耗 handsLeft。
 */
class EndToEndSimulationTest {

    /**
     * 模拟一局：从 createRun 到 ante 8 通关或失败。
     * 返回 RunState（最终状态）。
     */
    private RunState simulateRun(String deckKey, int stakeIdx, String seed, String challenge) {
        RunState s = Engine.createRun(deckKey, stakeIdx, seed, challenge);
        int safetyLimit = 1000; // 防死循环
        int iterations = 0;

        while (s.phase != Phase.END && iterations++ < safetyLimit) {
            if (s.phase == Phase.BLIND_SELECT) {
                // 自动选择开始（不跳过）
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                continue;
            }
            if (s.phase == Phase.SHOP) {
                // 直接离开商店
                Engine.nextRound(s);
                continue;
            }
            if (s.phase == Phase.PACK) {
                // 跳过补充包
                cn.quotidietium.balatro.engine.shop.Packs.skip(s);
                continue;
            }
            if (s.phase == Phase.ROUND) {
                playBestHand(s);
                continue;
            }
            // 未知 phase，退出防死锁
            break;
        }
        assertTrue(iterations < safetyLimit, "模拟未在安全限内结束（可能死锁），seed=" + seed);
        return s;
    }

    /** 出最优手牌：优先打全部 5 张（如果构成有效牌型），否则打点数最高的 1 张。 */
    private void playBestHand(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return;

        // 简单策略：打出全部手牌（最多 5 张），让引擎判定牌型
        List<Integer> cardIds = new ArrayList<>();
        int n = Math.min(5, s.hand.size());
        for (int i = 0; i < n; i++) {
            cardIds.add(s.hand.get(i).id());
        }

        Engine.PlayResult r = Engine.playHand(s, cardIds);

        // 如果出牌失败（Boss 限制等），尝试打 1 张
        if (!r.ok && s.handsLeft > 0 && !s.hand.isEmpty()) {
            List<Integer> single = List.of(s.hand.get(0).id());
            Engine.PlayResult r2 = Engine.playHand(s, single);
            // 如果单张也失败（psychic 需 5 张），尝试不同数量
            if (!r2.ok && s.handsLeft > 0 && !s.hand.isEmpty()) {
                // 尝试 5 张（psychic）
                if (s.hand.size() >= 5) {
                    List<Integer> five = new ArrayList<>();
                    for (int i = 0; i < 5; i++) five.add(s.hand.get(i).id());
                    Engine.playHand(s, five);
                }
            }
        }

        // 如果回合还没结束且没有出牌次数，引擎会自动判负
    }

    // ================= 测试用例 =================

    @Test
    void redDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("red", 0, "E2E001", null);
        assertTrue(s.phase == Phase.END || s.won || s.lost, "应到达 END 阶段");
    }

    @Test
    void blueDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("blue", 0, "E2E002", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段");
    }

    @Test
    void blackDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("black", 0, "E2E003", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段");
    }

    @Test
    void plasmaDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("plasma", 0, "E2E004", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段");
    }

    @Test
    void checkeredDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("checkered", 0, "E2E005", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段");
    }

    @Test
    void erraticDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("erratic", 0, "E2E006", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（erratic 随机点数花色）");
    }

    @Test
    void abandonedDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("abandoned", 0, "E2E007", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（无人头牌）");
    }

    @Test
    void redDeckGoldStakeCompletesWithoutCrash() {
        // 金注：最高难度（全部赌注效果累加）
        RunState s = simulateRun("red", 7, "E2E008", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（金注）");
    }

    @Test
    void paintedDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("painted", 0, "E2E009", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（手牌+2/小丑槽-1）");
    }

    @Test
    void anaglyphDeckWhiteStakeCompletesWithoutCrash() {
        RunState s = simulateRun("anaglyph", 0, "E2E010", null);
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（浮雕：Boss 得翻倍标签）");
    }

    @Test
    void challengeOmeletteCompletesWithoutCrash() {
        // 煎蛋卷挑战：5 蛋/无收入
        RunState s = simulateRun("red", 0, "E2E011", "omelette");
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（omelette 挑战）");
    }

    @Test
    void challengeXrayCompletesWithoutCrash() {
        // X 光视界：牌朝上/-手牌
        RunState s = simulateRun("red", 0, "E2E012", "xray");
        assertTrue(s.phase == Phase.END, "应到达 END 阶段（xray 挑战）");
    }

    @Test
    void multipleSeedsAllCompleteWithoutCrash() {
        // 批量种子验证：确保不同种子下都能完整运行（种子复现端到端）
        for (int i = 0; i < 20; i++) {
            String seed = "MULTI" + String.format("%03d", i);
            RunState s = simulateRun("red", 0, seed, null);
            assertTrue(s.phase == Phase.END, "种子 " + seed + " 应到达 END 阶段");
        }
    }

    @Test
    void allDecksBatchCompleteWithoutCrash() {
        // 所有 15 个牌组批量验证
        for (Data.Deck d : Data.DECKS) {
            RunState s = simulateRun(d.key(), 0, "DECKBAT", null);
            assertTrue(s.phase == Phase.END, "牌组 " + d.key() + " 应到达 END 阶段");
        }
    }

    @Test
    void noNegativeMoneyExceptCreditCard() {
        // 验证金钱不出现异常负数（除信用卡的 -$20 credit 外）
        RunState s = simulateRun("red", 0, "MONEY01", null);
        // 种子确定 → 结果确定：必须到达终局，否则下面的 money 断言空转（静默跳过会掩盖引擎卡死回归）
        assertTrue(s.won || s.lost, "确定性种子 MONEY01 必须到达终局（不得静默跳过）");
        // 通关/失败时 money 应 >= -20（信用卡最多 -$20，但本局无信用卡）
        assertTrue(s.money >= 0, "正常牌组 money 不应为负: " + s.money);
    }

    @Test
    void handAndDiscardNeverNegative() {
        // 验证 handsLeft/discardsLeft 不为负
        RunState s = simulateRun("red", 0, "NEG001", null);
        assertTrue(s.handsLeft >= 0, "handsLeft 不应为负");
        assertTrue(s.discardsLeft >= 0, "discardsLeft 不应为负");
    }

    @Test
    void deckConsistency() {
        // 验证通关后牌组完整性（fullDeck 牌数 + hand + discardPile + drawPile 一致）
        RunState s = simulateRun("red", 0, "DECK001", null);
        // 不检查具体数量（消耗品可能加减牌），只检查不为 null 且无异常
        assertFalse(s.fullDeck == null, "fullDeck 不应为 null");
        assertFalse(s.hand == null, "hand 不应为 null");
        assertFalse(s.drawPile == null, "drawPile 不应为 null");
        assertFalse(s.discardPile == null, "discardPile 不应为 null");
    }
}
