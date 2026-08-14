package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 蜡封真版规则回归（R129，第 59 处修复族；Card Modifiers/Seals Wiki）。
 *
 * <p>①红蜡封同样重触发**手中效果**（钢牌红蜡封无哑剧也 ×2）；②紫蜡封在**被动弃牌**
 * （钩子 Boss 强弃）时同样触发给塔罗（"player or automatic discards"）。
 */
class SealRealRulesTest {

    @Test
    void redSealRetriggersSteelInHandWithoutMime() {
        // 同种子双局对照：普通局 vs 手持钢牌+红蜡封（钢 ×1.5/次）
        RunState base = Engine.createRun("red", 0, "RSB1", null);
        Engine.selectBlind(base, Data.BlindType.SMALL, false);
        for (Card c : base.hand) c.setEnh(Data.Enhancement.STEEL);
        long sb = playFirst(base);

        RunState red = Engine.createRun("red", 0, "RSB1", null);
        Engine.selectBlind(red, Data.BlindType.SMALL, false);
        for (Card c : red.hand) c.setEnh(Data.Enhancement.STEEL);
        for (Card c : red.hand) c.setSeal(Data.Seal.RED);
        long sr = playFirst(red);

        // 出 1 张：基础局该牌离手不触发钢；红局**剩余手持钢牌**各多触发一次 ×1.5
        // 差值 = 其余钢牌的一次 ×1.5 份额（精确断言：红局 > 基础局）
        assertTrue(sr > sb, "红蜡封应重触发手中钢铁（" + sr + " > " + sb + "）");
    }

    @Test
    void hookForcedDiscardTriggersPurpleSeal() {
        RunState s = Engine.createRun("red", 0, "HKPS1", null);
        s.bossQueue.clear();
        s.bossQueue.add("hook");
        s.nextBlind = "boss";
        Engine.selectBlind(s, Data.BlindType.BOSS, false);
        // 全手牌紫蜡封：钩子强弃 2 张必触发
        for (Card c : s.hand) c.setSeal(Data.Seal.PURPLE);
        s.roundScore = 0; // 不赢盲，聚焦弃牌
        int consBefore = s.consumables.size();
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.consumables.size() > consBefore,
                "钩子强弃的紫蜡封牌应触发给塔罗（被动弃牌同触发）");
        boolean tarot = s.consumables.stream().anyMatch(c -> "tarot".equals(c.kind));
        assertTrue(tarot, "产出应为塔罗");
    }

    private static long playFirst(RunState s) {
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(r.ok);
        return r.score;
    }
}
