package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 真版疯狂世界（Mad World）机制回归（R122，第 52 处修复）。
 *
 * <p>真版（balatrowiki.org Challenge #6）：Extra Hands 不产金 + 无利息；开局永恒负片
 * 「空想性错觉」与永恒「名片」；牌组仅 2~9 共 32 张；Boss「植物」禁现。
 * REF 的 madworld（双 Boss/无小大盲）与真版完全不同且描述与实现自相矛盾（startAnte 无条件
 * nextBlind="small"）——R91 漏检的同族第 4 例，按 R102 既定授权重定义为真版。
 */
class MadworldRealMechanicsTest {

    @Test
    void deckIs32CardsRanksTwoToNine() {
        RunState s = Engine.createRun("red", 0, "MWR1", "madworld");
        assertEquals(32, s.fullDeck.size(), "仅 2~9 共 32 张");
        for (Card c : s.fullDeck) {
            assertTrue(c.rank() >= 2 && c.rank() <= 9, "点数带外: " + c.rank());
        }
    }

    @Test
    void startsWithEternalNegativePareidoliaAndEternalBusiness() {
        RunState s = Engine.createRun("red", 0, "MWR2", "madworld");
        assertEquals(2, s.jokers.size());
        var p = s.jokers.get(0);
        var b = s.jokers.get(1);
        assertEquals("pareidolia", p.def.key());
        assertTrue(p.eternal, "空想性错觉永恒");
        assertEquals(Data.Edition.NEGATIVE, p.edition, "空想性错觉为负片（自带槽）");
        assertEquals("business", b.def.key());
        assertTrue(b.eternal, "名片永恒");
        assertFalse(b.eternal && b.edition != null && b.edition == Data.Edition.NEGATIVE
                && !"business".equals(b.def.key()), "占位");
    }

    @Test
    void economyBansBlockHandPayAndInterestButNotBlindReward() {
        RunState s = Engine.createRun("red", 0, "MWR3", "madworld");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertTrue(s.mods.noHandPay && s.mods.noInterest, "双封禁 mod 生效");
        long before = s.money; // $4
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        // 奖励 $3 保留 + 名片×空想性错觉联动（allFace 下计分牌 1/4 给 $2——真版 Mad World 核心）
        assertTrue(s.money >= before + 3, "盲注奖励保留（≥$3）：" + (s.money - before));

        // 纯封禁路径：移除两张开局小丑后赢盲 → 精确 +$3（奖励保留、两手/利息封死）
        RunState bare = Engine.createRun("red", 0, "MWR4", "madworld");
        bare.jokers.clear();
        Engine.selectBlind(bare, Data.BlindType.SMALL, false);
        long b0 = bare.money;
        bare.roundScore = bare.blindTarget;
        Engine.playHand(bare, List.of(bare.hand.get(0).id()));
        assertEquals(b0 + 3, bare.money, "无小丑时精确 $4→$7（奖励保留，两手/利息封禁）");
    }

    @Test
    void plantBossNeverAppears() {
        for (int i = 0; i < 200; i++) {
            RunState s = Engine.createRun("red", 0, "MWBOSS" + i, "madworld");
            assertFalse("plant".equals(s.bossQueue.get(0)),
                    "植物禁现（seed=" + i + " 实际=" + s.bossQueue.get(0) + "）");
            assertFalse(s.mods.doubleBoss, "真版疯狂世界无双 Boss");
        }
    }
}
