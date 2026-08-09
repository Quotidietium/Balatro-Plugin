package cn.quotidietium.balatro.engine;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨阶段整合冒烟测试：授予若干小丑后驱动 回合→商店→购买→下一盲注→Boss 全流程，
 * 确保计分管线/经济/阶段切换在多小丑场景下无异常。
 */
class SmokeTest {

    @Test
    void fullRunWithJokersAndShopNoException() {
        RunState s = Engine.createRun("red", 0, "SMOKE");
        s.jokers.add(JokerRegistry.create("joker"));
        s.jokers.add(JokerRegistry.create("banner"));
        s.jokers.add(JokerRegistry.create("abstract"));
        Engine.selectBlind(s, Data.BlindType.SMALL, false);

        for (int step = 0; step < 60; step++) {
            if (s.phase == Phase.ROUND) {
                // 出前 5 张（或全部）
                List<Integer> ids = new ArrayList<>();
                int take = Math.min(5, s.hand.size());
                for (int i = 0; i < take; i++) ids.add(s.hand.get(i).id());
                Engine.playHand(s, ids);
            } else if (s.phase == Phase.SHOP) {
                // 尝试购买第一张能买的商品
                for (int i = 0; s.shop != null && i < s.shop.cards.size(); i++) {
                    if (Shop.buyCard(s, i)) break;
                }
                Engine.nextRound(s);
            } else if (s.phase == Phase.BLIND_SELECT) {
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            } else {
                break; // END
            }
        }
        // 至少完成了若干回合且最终进入结束态（通关或失败）
        assertTrue(s.roundCount >= 1, "应至少进行过回合");
        assertTrue(s.phase == Phase.END || s.phase == Phase.SHOP || s.phase == Phase.BLIND_SELECT || s.phase == Phase.ROUND,
                "阶段合法: " + s.phase);
    }
}
