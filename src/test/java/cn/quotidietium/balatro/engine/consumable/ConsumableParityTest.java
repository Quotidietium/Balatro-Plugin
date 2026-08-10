package cn.quotidietium.balatro.engine.consumable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 消耗品移植一致性回归（对照 engine.js）：
 * <ul>
 *   <li>wheel/aura 的版本为 1/3 均匀抽取（曾误用商店权重 50/35/15）；</li>
 *   <li>immolate 销毁后补满手牌（drawUpTo）；</li>
 *   <li>trimHand 允许手牌临时溢出至上限 +3（cryptid 满手复制不作废）；</li>
 *   <li>destroyCard 尊重 allFace（pareidolia 时销毁任意牌触发 onFaceDestroyed）。</li>
 * </ul>
 */
class ConsumableParityTest {

    @Test
    void auraEditionIsUniformNotShopWeighted() {
        int foil = 0, holo = 0, poly = 0;
        int n = 300;
        for (int i = 0; i < n; i++) {
            RunState s = Engine.createRun("red", 0, "AURA" + i);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            s.consumables.add(new Consumable("spectral", "aura"));
            int tid = s.hand.get(0).id();
            Consumables.Result r = Consumables.use(s, 0, List.of(tid));
            assertTrue(r.ok, "aura 应可使用");
            Data.Edition e = null;
            for (Card c : s.hand) if (c.id() == tid) e = c.edition();
            assertNotNull(e, "aura 应附加版本");
            switch (e) {
                case FOIL -> foil++;
                case HOLO -> holo++;
                case POLY -> poly++;
                default -> fail("aura 不应给出负片：" + e);
            }
        }
        // 均匀 1/3 时每档约 100/300（3σ 波动约 ±25）；若误用商店权重(50/35/15)，poly 仅约 45
        assertTrue(foil >= 60 && holo >= 60 && poly >= 60,
                "应为均匀 1/3 分布而非商店权重：foil=" + foil + " holo=" + holo + " poly=" + poly);
    }

    @Test
    void immolateDestroysFiveThenRefillsHand() {
        RunState s = Engine.createRun("red", 0, "IMMOLE");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        int full = s.handSizeRound;
        assertEquals(full, s.hand.size(), "开局手牌应为上限");
        s.consumables.add(new Consumable("spectral", "immolate"));
        long money = s.money;

        Consumables.Result r = Consumables.use(s, 0, List.of());

        assertTrue(r.ok);
        assertEquals(full, s.hand.size(), "销毁 5 张后应补满回上限（对齐原版 drawUpTo）");
        assertEquals(money + 20, s.money, "immolate 应 +$20");
    }

    @Test
    void cryptidCopiesSurviveOnFullHand() {
        RunState s = Engine.createRun("red", 0, "CRYPTID");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        int full = s.handSizeRound;
        assertEquals(full, s.hand.size(), "开局手牌应为上限");
        s.consumables.add(new Consumable("spectral", "cryptid"));
        int tid = s.hand.get(0).id();

        Consumables.Result r = Consumables.use(s, 0, List.of(tid));

        assertTrue(r.ok);
        // 原版允许溢出至上限 +3：2 张复制全部留手（此前按上限硬裁剪，复制直接进弃牌堆）
        assertEquals(full + 2, s.hand.size(), "满手时 cryptid 的 2 张复制应留在手中");
    }

    @Test
    void destroyCardRespectsAllFaceFlag() {
        RunState s = Engine.createRun("red", 0, "ALLFACE");
        s.jokers.add(JokerRegistry.create("pareidolia")); // allFace：所有牌视为人头牌
        JokerInstance canio = JokerRegistry.create("canio");
        s.jokers.add(canio);
        Engine.recomputeFlags(s);
        Card c = s.makeCard(5, 0); // 非人头牌
        s.fullDeck.add(c);

        s.destroyCard(c);

        assertEquals(1.0, (Double) canio.extra.get("x"), 1e-9,
                "pareidolia 在场时销毁非人头牌也应触发 onFaceDestroyed");
    }
}
