package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R204：消耗品 52 张 × **新种子族**逐张 smoke——ConsumableGoldenTest（固定种子）
 * 与 ConsumableChaosFuzzTest（CHAO* 族）之后，以全新种子族（CSF-*）逐张走
 * 效果路径：每张在两个不同新种子局中、以合法目标（取手牌前 N 张）使用，
 * 断言不崩 + use 返回 ok + 卡守恒（R187 精化式：差集=大理石数）+ 金钱下界。
 * 新种子探索第六维（消耗品逐张）。
 */
class ConsumableFreshSmokeTest {

    private static void assertConserved(RunState s, String where) {
        Map<Integer, Integer> piles = new HashMap<>();
        for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
        for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
        Map<Integer, Integer> deck = new HashMap<>();
        for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
        int marbles = 0;
        for (var j : s.jokers) if (j.def.key().equals("marble") && !j.debuff) marbles++;
        int diff = 0;
        for (var e : deck.entrySet()) diff += e.getValue() - piles.getOrDefault(e.getKey(), 0);
        for (var e : piles.entrySet()) diff += e.getValue() - deck.getOrDefault(e.getKey(), 0);
        assertTrue(diff == marbles, "守恒差=大理石数（" + where + "）diff=" + diff + " marbles=" + marbles);
        assertTrue(deck.keySet().containsAll(piles.keySet()), "三堆 id ⊆ 牌组（" + where + "）");
    }

    @Test
    void all52ConsumablesOnFreshSeedsApplyCleanly() {
        List<String[]> all = new ArrayList<>();
        for (Data.Tarot t : Data.TAROTS) all.add(new String[]{"tarot", t.key});
        for (Data.Planet p : Data.PLANETS) all.add(new String[]{"planet", p.key});
        for (Data.Spectral sp : Data.SPECTRALS) all.add(new String[]{"spectral", sp.key});
        assertTrue(all.size() == 52, "52 张（22+12+18）：" + all.size());

        int idx = 0;
        for (String[] c : all) {
            for (int rep = 0; rep < 2; rep++) {
                RunState s = Engine.createRun("red", rep, "CSF-" + (idx++) + "-" + c[1], null);
                Engine.selectBlind(s, Data.BlindType.SMALL, false);
                // fool 需要先有一张塔罗/星球使用记录（lastTarotPlanet）——先喂一张星球
                if (c[1].equals("fool")) {
                    s.consumables.clear();
                    s.consumables.add(new Consumable("planet", "mercury"));
                    cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, List.of());
                }
                s.consumables.clear();
                s.consumables.add(new Consumable(c[0], c[1]));
                // 合法目标：取手牌前 2 张（需目标的消耗品普遍 ≤2；无目标者忽略）
                List<Integer> targets = new ArrayList<>();
                for (int i = 0; i < Math.min(2, s.hand.size()); i++) targets.add(s.hand.get(i).id());
                // wheel 需要 ≥1 个无版本小丑；wraith/ectoplasm 需要可编辑小丑——先喂一个普通小丑
                if (c[1].equals("wheel") || c[1].equals("ectoplasm") || c[1].equals("wraith")
                        || c[1].equals("hex") || c[1].equals("ankh")) {
                    s.jokers.clear();
                    s.gainJoker("joker", null); // 无版本普通小丑
                }
                // 自适应目标数：先 2 后 1 后 0（exact=1 的牌用 1 张；无目标牌用 0）
                var r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, targets);
                if (!r.ok && targets.size() > 1) {
                    r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, targets.subList(0, 1));
                }
                if (!r.ok && !targets.isEmpty()) {
                    r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, List.of());
                }
                assertTrue(r.ok, c[0] + ":" + c[1] + " 合法目标应使用成功（rep=" + rep + "）：" + r.err);
                // 生成型（女祭司/皇帝）使用后可能新增其他消耗品——只断言有界
                assertTrue(s.consumables.size() <= 6, "消耗品数有界（" + c[1] + "）：" + s.consumables.size());
                assertConserved(s, c[1] + "#rep" + rep);
                assertTrue(s.money >= 0, "金钱下界（" + c[1] + "）：" + s.money);
                // 牌状态合法（R92 口径抽样）
                for (Card h : s.hand) {
                    assertTrue(h.rank() == 0 || (h.rank() >= 2 && h.rank() <= 14),
                            "rank 合法（" + c[1] + "）：" + h.rank());
                }
            }
        }
    }
}
