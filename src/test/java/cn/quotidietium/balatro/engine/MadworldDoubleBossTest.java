package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 疯狂世界（madworld）双 Boss 挑战回归测试（轮次 R4）。
 *
 * <p>移植对照 REF engine.js：chooseBoss 在 mods.doubleBoss 下抽取两个不同 Boss 入队；
 * 击败第一个 Boss 后无商店间隔、立即以 blindSelect/boss 接第二个 Boss（endRound 提前 return）。
 */
class MadworldDoubleBossTest {

    @Test
    void chooseBossQueuesTwoDistinctBosses() {
        for (String seed : new String[]{"MADW001", "MADW002", "MADW003", "MADW004", "MADW005"}) {
            RunState s = Engine.createRun("red", 0, seed, "madworld");
            assertEquals(2, s.bossQueue.size(), "madworld 开局应排队 2 个 Boss（seed=" + seed + "）");
            assertNotEquals(s.bossQueue.get(0), s.bossQueue.get(1), "两个 Boss 应不同（seed=" + seed + "）");
        }
    }

    @Test
    void normalRunQueuesSingleBoss() {
        RunState s = Engine.createRun("red", 0, "NORMAL1", null);
        assertEquals(1, s.bossQueue.size(), "普通局只排 1 个 Boss");
    }

    @Test
    void firstBossDefeatChainsToSecondBossWithoutShop() {
        RunState s = Engine.createRun("red", 0, "MADCHAIN1", "madworld");
        String boss1 = s.bossQueue.get(0);
        String boss2 = s.bossQueue.get(1);

        // 直接开始第一个 Boss 盲注（跳过小/大盲，聚焦双 Boss 链）
        s.nextBlind = "boss";
        assertTrue(Engine.selectBlind(s, Data.BlindType.BOSS, false));
        assertEquals(boss1, s.bossQueue.get(0));

        winCurrentBlind(s);
        assertEquals(Phase.BLIND_SELECT, s.phase, "击败第一个 Boss 后应直接进入盲注选择（无商店间隔）");
        assertEquals("boss", s.nextBlind, "下一盲注应为第二个 Boss");
        assertEquals(1, s.bossQueue.size(), "队列应只剩第二个 Boss");
        assertEquals(boss2, s.bossQueue.get(0), "队首应为第二个 Boss");

        // 开始并击败第二个 Boss → 进入商店、底注清空、队列清空
        assertTrue(Engine.selectBlind(s, Data.BlindType.BOSS, false));
        winCurrentBlind(s);
        assertEquals(Phase.SHOP, s.phase, "击败第二个 Boss 后应进入商店");
        assertTrue(s.bossQueue.isEmpty(), "双 Boss 均已击败，队列应空");
    }

    @Test
    void nonMadworldBossGoesStraightToShop() {
        RunState s = Engine.createRun("red", 0, "NORMALBOSS1", null);
        s.nextBlind = "boss";
        assertTrue(Engine.selectBlind(s, Data.BlindType.BOSS, false));
        winCurrentBlind(s);
        assertEquals(Phase.SHOP, s.phase, "普通局击败 Boss 后应进入商店");
    }

    /**
     * 把目标分降为 1 后打一手必胜牌：隔离计分波动与 Boss 差异，专注测试双 Boss 链式推进。
     * 兼容限制型 Boss：bell 强制包含指定牌；psychic 强制 5 张；eye/mouth 首手必合法。
     */
    private static void winCurrentBlind(RunState s) {
        assertEquals(Phase.ROUND, s.phase, "应处于出牌回合");
        s.blindTarget = 1;
        int safety = 50;
        while (s.phase == Phase.ROUND && safety-- > 0) {
            List<Integer> ids = new ArrayList<>();
            if (s.bellCardId != null) ids.add(s.bellCardId); // 翠绿铃：必须包含被强制的牌
            int want = s.mods.must5 || "psychic".equals(s.bossQueue.isEmpty() ? null : s.bossQueue.get(0))
                    ? 5 : Math.min(5, s.hand.size());
            for (Card c : s.hand) {
                if (ids.size() >= want) break;
                if (!ids.contains(c.id())) ids.add(c.id());
            }
            if (ids.isEmpty()) break;
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.ok && s.phase != Phase.ROUND) return; // 达标胜出（endRound 已推进阶段）
            if (r.ok && s.roundScore >= s.blindTarget) return;
            // 被 Boss 拒绝或分数不足（不可能：target=1）：换最少张数再试
            List<Integer> one = new ArrayList<>();
            if (s.bellCardId != null) {
                one.add(s.bellCardId);
            } else if (!s.hand.isEmpty()) {
                one.add(s.hand.get(0).id());
            }
            if (!one.isEmpty() && ids.size() != 1) {
                Engine.playHand(s, one);
            }
        }
        throw new AssertionError("无法在限定步数内赢下当前盲注（Boss=" + s.bossQueue + "）");
    }
}
