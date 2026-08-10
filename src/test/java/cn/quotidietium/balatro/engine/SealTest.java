package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 蜡封效果测试：紫色蜡封（弃牌→塔罗）、蓝色蜡封（手中回合结束→星球）。
 * 0.3.0 新实现，对齐 engine.js discard/endRound，黄金套件不覆盖故单独锁定。
 */
class SealTest {

    @Test
    void purpleSealCreatesTarotOnDiscard() {
        RunState s = Engine.createRun("red", 0, "PURP");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        Card c = s.hand.get(0);
        c.setSeal(Data.Seal.PURPLE);
        int before = s.consumables.size();

        Engine.PlayResult r = Engine.discard(s, List.of(c.id()));
        assertTrue(r.ok);
        assertEquals(before + 1, s.consumables.size(), "紫色蜡封应生成一张塔罗");
        assertEquals("tarot", s.consumables.get(s.consumables.size() - 1).kind);
    }

    @Test
    void blueSealCreatesPlanetOnRoundEnd() {
        RunState s = Engine.createRun("red", 0, "BLUE");
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 手牌第 2 张（不会被出）附加蓝色蜡封
        Card held = s.hand.get(1);
        held.setSeal(Data.Seal.BLUE);
        s.blindTarget = 1; // 确保第一手即获胜，触发 endRound
        int before = s.consumables.size();

        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(r.ok && r.won, "应通过本手盲注");
        assertEquals(before + 1, s.consumables.size(), "蓝色蜡封应生成一张星球牌");
        assertEquals("planet", s.consumables.get(s.consumables.size() - 1).kind);
    }
}
