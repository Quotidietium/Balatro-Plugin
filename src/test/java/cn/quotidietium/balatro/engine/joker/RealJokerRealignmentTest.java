package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 小丑真版对齐回归（R130，第 60 处修复族；三批 wiki 对照 49 项不符之核心抽样）。
 */
class RealJokerRealignmentTest {

    private static RunState round(String seed) {
        RunState s = Engine.createRun("red", 0, seed, null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    private static void setHand(RunState s, int... rs) {
        for (int i = 0; i < rs.length && i < s.hand.size(); i++) {
            s.hand.get(i).setRank(rs[i]);
            s.hand.get(i).setSuit(0);
        }
        Engine.sortHand(s);
    }

    @Test
    void containsFamilyTriggersOnFullHouseAndSflush() {
        // 直接断言 HandEval.contains（真版 contains 口径的数据源）
        RunState s = round("RJC1");
        var five = new java.util.ArrayList<Card>();
        int[][] cs = {{13,0},{13,1},{13,2},{9,0},{9,1}}; // 葫芦
        for (int[] rc : cs) five.add(s.makeCard(rc[0], rc[1]));
        var r1 = cn.quotidietium.balatro.engine.HandEval.evaluate(s, five);
        assertTrue(r1.contains(Data.HandType.PAIR), "葫芦 contains 对子");
        assertTrue(r1.contains(Data.HandType.THREE), "葫芦 contains 三条");
        assertTrue(r1.contains(Data.HandType.TWOPAIR), "葫芦 contains 两对");
        assertFalse(r1.contains(Data.HandType.STRAIGHT), "葫芦不含顺子");
        var sf = new java.util.ArrayList<Card>();
        int[][] sfc = {{14,0},{13,0},{12,0},{11,0},{10,0}}; // 皇家同花顺
        for (int[] rc : sfc) sf.add(s.makeCard(rc[0], rc[1]));
        var r2 = cn.quotidietium.balatro.engine.HandEval.evaluate(s, sf);
        assertTrue(r2.contains(Data.HandType.STRAIGHT), "同花顺 contains 顺子");
        assertTrue(r2.contains(Data.HandType.FLUSH), "同花顺 contains 同花");
        var four = new java.util.ArrayList<Card>();
        int[][] fc = {{8,0},{8,1},{8,2},{8,3},{3,0}}; // 四条
        for (int[] rc : fc) four.add(s.makeCard(rc[0], rc[1]));
        var r3 = cn.quotidietium.balatro.engine.HandEval.evaluate(s, four);
        assertFalse(r3.contains(Data.HandType.TWOPAIR), "四条不含两对（真版明确）");
        assertTrue(r3.contains(Data.HandType.PAIR), "四条含对子");
    }

    @Test
    void stencilAloneFiveSlotsIsX5() {
        // 同种子双局完全同构：mult 仅差模板 ×5 → with = 5 × bare
        RunState s = round("RJS1");
        s.jokers.add(JokerRegistry.create("stencil"));
        setHand(s, 13, 13, 9, 8, 7);
        long with = play(s);
        RunState ctrl = round("RJS1");
        setHand(ctrl, 13, 13, 9, 8, 7);
        long bare = play(ctrl);
        assertEquals(5 * bare, with, "单模板 5 槽 = ×5（自身槽计入）");
    }

    @Test
    void weeStartsAtZeroAndGrowsByEight() {
        RunState s = round("RJW1");
        JokerInstance wee = JokerRegistry.create("wee");
        s.jokers.add(wee);
        setHand(s, 13, 13, 9, 8, 7, 9, 8, 7);
        // 直接以两张 2 出对子（排序会把 2 沉底，须按 id 选取）
        s.hand.get(6).setRank(2);
        s.hand.get(7).setRank(2);
        Engine.playHand(s, List.of(s.hand.get(6).id(), s.hand.get(7).id()));
        Object v0 = wee.extra.get("chips");
        assertEquals(16, v0 instanceof Number n ? n.intValue() : -1,
                "两张【计分的】2 → +16（初始 +0 起步，无基值）");
    }

    @Test
    void dnaCopyIsPermanent() {
        RunState s = round("RJD1");
        s.jokers.add(JokerRegistry.create("dna"));
        setHand(s, 13, 9, 8, 7, 6);
        int deck = s.fullDeck.size();
        play(s); // 首手单张？出 5 张不触发——出 1 张
        // 单张出牌
        RunState s2 = round("RJD2");
        s2.jokers.add(JokerRegistry.create("dna"));
        setHand(s2, 13, 9, 8, 7, 6);
        int d0 = s2.fullDeck.size();
        Engine.playHand(s2, List.of(s2.hand.get(0).id()));
        assertEquals(d0 + 1, s2.fullDeck.size(), "DNA 复制应永久入牌组");
    }

    @Test
    void invisibleTwoRoundsAndCopyDropsNegative() {
        RunState s = round("RJI1");
        JokerInstance src = JokerRegistry.create("rocket");
        src.edition = Data.Edition.NEGATIVE;
        s.jokers.add(src);
        JokerInstance inv = JokerRegistry.create("invisible");
        s.jokers.add(inv);
        inv.def.onRoundEnd(s, inv); // 1 回合：未到期——出售仅损失（无副本）
        s.sellJoker(1);
        assertEquals(1, s.jokers.size(), "未到期出售不出副本");
        // 重来：2 回合到期出售 → 副本出现且去负片
        RunState s2 = round("RJI2");
        JokerInstance src2 = JokerRegistry.create("rocket");
        src2.edition = Data.Edition.NEGATIVE;
        s2.jokers.add(src2);
        JokerInstance inv2 = JokerRegistry.create("invisible");
        s2.jokers.add(inv2);
        inv2.def.onRoundEnd(s2, inv2);
        inv2.def.onRoundEnd(s2, inv2);
        assertTrue(s2.sellJoker(1), "2 回合后出售成功");
        assertEquals(2, s2.jokers.size(), "卖一得二");
        assertTrue(s2.jokers.get(1).edition == null, "副本应去除负片");
        assertEquals(Data.Edition.NEGATIVE, src2.edition, "源不受影响");
    }

    @Test
    void seanceTriggersOnStraightFlushNotOnlyRoyal() {
        RunState s = round("RJSE1");
        s.jokers.add(JokerRegistry.create("seance"));
        setHand(s, 9, 8, 7, 6, 5);
        for (Card c : s.hand) c.setSuit(1);
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id(),
                s.hand.get(2).id(), s.hand.get(3).id(), s.hand.get(4).id()));
        boolean won = s.phase == Phase.SHOP || s.roundScore >= s.blindTarget;
        assertTrue(won || s.roundScore > 0);
        assertEquals(1, s.consumables.size(), "普通同花顺也应触发降灵会");
        assertEquals("spectral", s.consumables.get(0).kind);
    }

    @Test
    void astronomerMakesPlanetsAndCelestialPacksFree() {
        boolean freePlanet = false, freeCelestial = false;
        for (int shop = 0; shop < 40 && !(freePlanet && freeCelestial); shop++) {
            RunState s = round("RJA" + shop);
            s.vouchers.add("astronomer");
            s.roundScore = s.blindTarget;
            Engine.playHand(s, List.of(s.hand.get(0).id()));
            for (var c : s.shop.cards) {
                if ("planet".equals(c.kind)) { assertEquals(0, c.price, "星球牌免费"); freePlanet = true; }
                if ("tarot".equals(c.kind)) assertEquals(3, c.price, "塔罗照常计价（对照）");
            }
            for (var p : s.shop.packs) {
                if (p.pack.type == Data.PackType.CELESTIAL) { assertEquals(0, p.price, "天体包免费"); freeCelestial = true; }
                else assertTrue(p.price >= 1, "非天体包照常计价");
            }
        }
        assertTrue(freePlanet, "40 店内应出现免费星球牌");
        assertTrue(freeCelestial, "40 店内应出现免费天体包");
    }

    @Test
    void ridebusDebuffedFaceDoesNotReset() {
        RunState s = round("RJR1");
        JokerInstance bus = JokerRegistry.create("ridebus");
        s.jokers.add(bus);
        setHand(s, 13, 13, 9, 8, 7);
        s.hand.get(0).setDebuff(true); // 计分 K 被 debuff
        s.hand.get(1).setDebuff(true);
        play(s);
        assertEquals(1, ((Number) bus.extra.get("mult")).intValue(),
                "debuff 的人头牌【不】重置（真版 scoring face 口径）");
    }

    @Test
    void burntOnlyFirstDiscardPerRound() {
        RunState s = round("RJB1");
        s.jokers.add(JokerRegistry.create("burnt"));
        setHand(s, 13, 13, 9, 8, 7); // 确保弃的两张构成对子
        int lvl0 = s.handLevel(Data.HandType.PAIR);
        Engine.discard(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
        assertEquals(lvl0 + 1, s.handLevel(Data.HandType.PAIR), "首次弃牌升级");
        Engine.discard(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
        assertEquals(lvl0 + 1, s.handLevel(Data.HandType.PAIR), "第二次弃牌不再升级（每回合一次）");
    }

    @Test
    void hittheroadResetsEachRound() {
        RunState s = round("RJH1");
        JokerInstance htr = JokerRegistry.create("hittheroad");
        s.jokers.add(htr);
        setHand(s, 11, 11, 9, 8, 7);
        Engine.discard(s, List.of(s.hand.get(0).id(), s.hand.get(1).id()));
        assertEquals(1.0, ((Number) htr.extra.get("x")).doubleValue(), 1e-9, "两张 J → ×1");
        htr.def.onBlindStart(s, htr);
        assertEquals(0.0, ((Number) htr.extra.get("x")).doubleValue(), 1e-9, "回合重置");
    }

    private static long play(RunState s) {
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id(),
                s.hand.get(2).id(), s.hand.get(3).id(), s.hand.get(4).id()));
        assertTrue(r.ok);
        return r.score;
    }
}
