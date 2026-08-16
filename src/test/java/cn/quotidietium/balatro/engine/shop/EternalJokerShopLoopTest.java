package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * R163：黑注（stake 3+）永恒小丑不可出售语义下的商店循环经济封闭性 fuzz。
 *
 * <p>黑注下商店小丑约 30% 带永恒贴纸（R162 统计锁定）；永恒小丑买下后永不可出售。
 * 本 fuzz 驱动「速胜→商店买 0~2 个最便宜可负担小丑（含永恒）→全列表出售尝试→
 * 重掷→next」循环 ×25 试验 ×≤6 轮，逐操作断言：
 * <ul>
 *   <li>可负担最便宜小丑购买必成功且扣款恰=标价、标记已售、金钱不为负；</li>
 *   <li>**永恒出售恒拒**（false + 列表逐字不变 + 金钱零变化）；非永恒出售成功且
 *       回收恰=售前 sellValue（闭包依据：全 150 小丑仅 campfire 有 onAnySell，
 *       且只累自身倍率不动金钱与他人售价）；</li>
 *   <li>重掷精确扣款 5+次数；next 不软锁（每轮均可推进）；</li>
 *   <li>槽满后购买自动被 jokerSpace 拒绝（buyCard false）——由「可负担才尝试」
 *       与成功断言联合覆盖。</li>
 * </ul>
 */
class EternalJokerShopLoopTest {

    private static List<String> snapshot(RunState s) {
        List<String> out = new ArrayList<>();
        for (var j : s.jokers) out.add(j.def.key() + ":" + j.eternal + ":" + j.edition);
        return out;
    }

    @Test
    void eternalJokersCannotBeSoldAndLoopNeverLeaks() {
        Random rnd = new Random(20260819L);
        for (int trial = 0; trial < 25; trial++) {
            RunState s = Engine.createRun("red", 3, "ETERNAL" + trial, null);
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            for (int round = 0; round < 6; round++) {
                // —— 回合速胜进商店 ——
                if (s.phase != Phase.ROUND) {
                    if (s.phase != Phase.BLIND_SELECT || !Engine.nextRound(s)) break;
                    Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                }
                if (s.phase != Phase.ROUND) break;
                s.roundScore = s.blindTarget;
                int guard = 0;
                while (s.phase == Phase.ROUND && guard++ < 20) {
                    int n = Math.min(5, s.hand.size());
                    List<Integer> ids = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
                    if (Engine.playHand(s, ids).ok) break;
                    if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                        Engine.discard(s, List.of(s.hand.get(0).id()));
                    } else break;
                }
                if (s.phase != Phase.SHOP) break;

                // —— 买 0~2 个最便宜可负担小丑 ——
                int buys = rnd.nextInt(3);
                for (int b = 0; b < buys; b++) {
                    int best = -1;
                    long bestPrice = Long.MAX_VALUE;
                    for (int i = 0; i < s.shop.cards.size(); i++) {
                        var c = s.shop.cards.get(i);
                        if (!c.sold && "joker".equals(c.kind) && c.price <= s.money && c.price < bestPrice) {
                            best = i;
                            bestPrice = c.price;
                        }
                    }
                    if (best < 0) break;
                    long m0 = s.money;
                    var victim = s.shop.cards.get(best);
                    assertTrue(Shop.buyCard(s, best), "可负担最便宜项购买应成功（trial=" + trial + "）");
                    assertEquals(m0 - victim.price, s.money, "购买扣款恰=标价");
                    assertTrue(victim.sold);
                    assertTrue(s.money >= 0, "金钱不为负（黑注无信用卡）");
                }

                // —— 出售路径：从尾部遍历避免索引位移；永恒拒/非永恒恰回收 ——
                for (int i = s.jokers.size() - 1; i >= 0; i--) {
                    var j = s.jokers.get(i);
                    long m1 = s.money;
                    List<String> before = snapshot(s);
                    int expect = j.eternal ? -1 : s.sellValue(j);
                    boolean sold = s.sellJoker(i);
                    if (j.eternal) {
                        assertFalse(sold, "永恒小丑出售应恒拒");
                        assertEquals(before, snapshot(s), "拒绝后列表逐字不变");
                        assertEquals(m1, s.money, "拒绝后金钱零变化");
                    } else {
                        assertTrue(sold, "非永恒小丑出售应成功");
                        assertEquals(m1 + expect, s.money, "回收恰=售前 sellValue");
                    }
                }

                // —— 重掷精确扣款（买穷时可跳过）+ next 不软锁 ——
                long m2 = s.money;
                long expect = 5 + s.shop.rerollCount;
                if (m2 >= expect) {
                    assertEquals(expect, Shop.reroll(s));
                    assertEquals(m2 - expect, s.money);
                }
                assertTrue(Engine.nextRound(s), "next 不应软锁（trial=" + trial + " round=" + round + "）");
            }
            assertTrue(s.ante >= 1);
        }
    }
}
