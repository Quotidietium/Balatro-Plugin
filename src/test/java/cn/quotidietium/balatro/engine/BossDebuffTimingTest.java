package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Boss debuff 的施加/解除时序锁定（R116）。
 *
 * <p>①翠绿之叶：leaf 激活期间弃牌重抽，**新抽的牌**由 drawOne 按 bossLeaf 现值重新施加 debuff；
 *   出售小丑解除 leaf 后，hand 清 debuff 且**后续重抽不再施加**。
 * <p>③绯红之心：每手随机禁一张小丑（debuffHand），**每手结束统一重置**（Engine L699-700）——
 *   连续多手不累积多个失效者；重置发生在 onPlayHand 循环之后（被禁者本手的 onPlayHand 正确跳过）。
 */
class BossDebuffTimingTest {

    private static RunState bossRound(String seed, String bossKey) {
        RunState s = Engine.createRun("red", 0, seed, null);
        s.bossQueue.clear();
        s.bossQueue.add(bossKey);
        Engine.selectBlind(s, Data.BlindType.BOSS, false);
        return s;
    }

    @Test
    void leafReDebuffsNewlyDrawnCardsUntilSold() {
        RunState s = bossRound("LEAF1", "leaf");
        // 开局全 debuff（leaf 的 startRound 全面失效）
        for (Card c : s.hand) assertTrue(c.debuff(), "leaf 开局应全失效");

        // leaf 激活期间弃 3 张 → 重抽的新牌应再次 debuff
        List<Integer> disc = new ArrayList<>();
        for (int i = 0; i < 3 && i < s.hand.size(); i++) disc.add(s.hand.get(i).id());
        Engine.discard(s, disc);
        for (Card c : s.hand) assertTrue(c.debuff(), "leaf 激活期间重抽的牌应再次失效");

        // 出售一张小丑解除 leaf：hand 清 debuff
        s.jokers.add(cn.quotidietium.balatro.engine.joker.JokerRegistry.create("joker"));
        assertTrue(s.sellJoker(0), "出售应成功");
        for (Card c : s.hand) assertFalse(c.debuff(), "解除后手牌应恢复");

        // 解除后再弃牌重抽：新牌不再施加 debuff
        disc.clear();
        for (int i = 0; i < 3 && i < s.hand.size(); i++) disc.add(s.hand.get(i).id());
        Engine.discard(s, disc);
        for (Card c : s.hand) assertFalse(c.debuff(), "解除后重抽的牌不得再失效");
    }

    @Test
    void heartDebuffHandResetsEachHandNoAccumulation() {
        RunState s = bossRound("HEART1", "heart");
        for (int k = 0; k < 3; k++) {
            s.jokers.add(cn.quotidietium.balatro.engine.joker.JokerRegistry.create("joker"));
        }
        // 连续两手（够分不赢盲以留hands）——heart 每手禁 1 张，手末统一重置
        for (int hand = 0; hand < 2 && s.handsLeft > 0; hand++) {
            Engine.playHand(s, List.of(s.hand.get(0).id()));
            for (var j : s.jokers) {
                assertFalse(j.debuffHand, "每手结束后 debuffHand 应全部重置（手 " + hand + "）");
            }
        }
        // boss 本体标志不受重置影响
        assertTrue("heart".equals(s.bossQueue.get(0)), "heart 仍在队列");
    }
}
