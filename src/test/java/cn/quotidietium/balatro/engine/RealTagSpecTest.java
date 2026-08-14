package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 标签真版语义回归（R127，第 57 处修复族；Tags Wiki）。
 *
 * <p>修改：Standard/Charm/Meteor/Buffoon=立即免费开 **Mega** 包（x2 档）；Ethereal=立即免费
 * 幽灵包；Economy=金钱翻倍至多 +$40；Uncommon/Rare/四版本标签的指定小丑**免费**。
 */
class RealTagSpecTest {

    private static RunState tag(String seed, String key) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.gainTag(s, key);
        return s;
    }

    @Test
    void packTagsOpenMegaPacksImmediately() {
        for (String[] kv : new String[][]{
                {"standard", "standard3"}, {"charm", "arcana3"}, {"meteor", "celestial3"},
                {"buffoon", "buffoon3"}, {"ethereal", "spectral1"}}) {
            RunState s = tag("RTAG" + kv[0], kv[0]);
            assertEquals(Phase.PACK, s.phase, kv[0] + " 应立即进入补充包");
            assertEquals(kv[1], s.pack.def.key, kv[0] + " 应开 " + kv[1] + "（Mega/幽灵）");
            if (!kv[0].equals("ethereal")) {
                assertEquals(2, s.pack.def.choose, kv[0] + "：Mega 档必须选 2（R135 修正）");
                assertEquals(kv[0].equals("buffoon") ? 4 : 5, s.pack.def.size,
                        kv[0] + "：Mega 档 5 张（Buffoon 4 张）");
            }
        }
    }

    @Test
    void economyDoublesMoneyCappedAt40() {
        assertEquals(8, tag("RECO1", "economy").money, "$4 翻倍 → $8");
        RunState rich = Engine.createRun("red", 0, "RECO2", null);
        rich.money = 100;
        Engine.gainTag(rich, "economy");
        assertEquals(140, rich.money, "$100 → +$40 封顶");
        // R131 真版（Economy Tag Wiki）：负余额【归零】而非停留负值
        RunState neg = Engine.createRun("red", 0, "RECO3", null);
        neg.money = -5;
        Engine.gainTag(neg, "economy");
        assertEquals(0, neg.money, "$-5 → $0（真版负余额归零）");
    }

    @Test
    void rarityAndEditionTagJokersAreFree() {
        for (String key : new String[]{"uncommon", "rare"}) {
            RunState s = tag("RFRE" + key, key);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            s.roundScore = s.blindTarget;
            Engine.playHand(s, java.util.List.of(s.hand.get(0).id()));
            boolean foundFree = false;
            for (var c : s.shop.cards) {
                if ("joker".equals(c.kind) && c.joker != null) {
                    int expectedRarity = "uncommon".equals(key) ? 1 : 2;
                    if (cn.quotidietium.balatro.engine.joker.JokerRegistry.rarityOf(c.joker.def.key()) == expectedRarity) {
                        assertEquals(0, c.price, key + " 标签的指定小丑应免费");
                        foundFree = true;
                    }
                }
            }
            assertTrue(foundFree, key + " 商店应含指定稀有度小丑");
        }
        // 版本标签同理由 golden 的 nextshop 标志与 Shop 分支保证；此处抽 foil 行为验证
        RunState s = tag("RFREfoil", "foil");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.roundScore = s.blindTarget;
        Engine.playHand(s, java.util.List.of(s.hand.get(0).id()));
        boolean sawFoilFree = false;
        for (var c : s.shop.cards) {
            if ("joker".equals(c.kind) && c.joker != null && c.joker.edition == Data.Edition.FOIL) {
                assertEquals(0, c.price, "闪膜标签小丑应免费");
                sawFoilFree = true;
            }
        }
        assertTrue(sawFoilFree, "商店应含闪膜小丑");
    }
}
