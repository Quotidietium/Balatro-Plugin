package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R206：28 Boss 效果分支 × **新种子族**逐分支 smoke——BossGoldenTest/BossEndToEnd
 * 用固定种子；本轮以全新种子族（BFS-*）对每个 Boss 强制指定 bossQueue 后开打：
 * 断言效果生效（可观测副作用）+ 回合可推进或合法失败 + 不崩 + 卡守恒。
 * 新种子探索第八维（Boss 逐分支）。
 */
class BossFreshSmokeTest {

    private static boolean playAny(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int sz = s.hand.size();
        if (sz >= 5) {
            for (int st = 0; st + 5 <= sz; st++) {
                List<Integer> ids = new ArrayList<>();
                for (int i = st; i < st + 5; i++) ids.add(s.hand.get(i).id());
                if (Engine.playHand(s, ids).ok) return true;
            }
        }
        for (int n = 1; n <= Math.min(5, sz); n++) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
            if (Engine.playHand(s, ids).ok) return true;
        }
        return false;
    }

    @Test
    void allBossesOnFreshSeedsBehave() {
        int idx = 0;
        for (Data.Boss boss : Data.Boss.values()) {
            RunState s = Engine.createRun("red", idx % 2, "BFS-" + (idx++) + "-" + boss.key, null);
            // 快进到 Boss 盲注：赢小/大盲后 next 两次（真实路径保留 boss 流——chooseBoss 已在
            // startAnte 抽好本 ante Boss；直接改 queue 会绕过选择器，但效果分支按 queue[0] 读，
            // 用 R126 调度下本 ante 的原 Boss 替换为目标 Boss（消耗为 0——仅改队列头）。
            for (int i = 0; i < 2; i++) {
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                s.roundScore = s.blindTarget;
                int guard = 0;
                while (s.phase == Phase.ROUND && guard++ < 20) {
                    if (playAny(s)) break;
                    if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                        Engine.discard(s, List.of(s.hand.get(0).id()));
                    } else break;
                }
                if (s.phase == Phase.SHOP) Engine.nextRound(s);
            }
            assertTrue(s.phase == Phase.BLIND_SELECT, "应到 Boss 选择（" + boss.key + "）");
            // 替换本 ante Boss 为被测者（引擎效果读 bossQueue[0]；needle 等目标分随 effectBk）
            s.bossQueue.set(0, boss.key);
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            assertTrue(s.phase == Phase.ROUND, "Boss 回合应开始（" + boss.key + "）");

            // 效果生效抽查（可观测副作用）
            switch (boss.key) {
                case "water" -> assertTrue(s.discardsLeft == 0, "水：无弃牌");
                case "needle" -> assertTrue(s.handsLeft == 1, "缝衣针：1 出牌");
                case "manacle" -> assertTrue(s.handSizeRound == 7 && s.hand.size() == 7,
                        "镣铐：红牌组 8-1=7 且手牌恰 7（实际 " + s.handSizeRound + "/" + s.hand.size() + "）");
                case "club", "goad", "head", "window" -> {
                    boolean any = false;
                    for (Card c : s.hand) if (c.debuff()) any = true;
                    // 手牌中可能无对应花色——不强制，但流消耗下抽满后必有 debuff 或空
                    assertTrue(any || s.hand.size() < s.handSizeRound, "花色 Boss 抽查（" + boss.key + "）");
                }
                case "leaf" -> {
                    for (Card c : s.hand) assertTrue(c.debuff(), "翠绿之叶：全失效（" + boss.key + "）");
                }
                case "psychic" -> {
                    // 通灵者：非 5 张出牌被拒（用现有手牌 1 张试，被拒不耗 handsLeft）
                    if (!s.hand.isEmpty()) {
                        var r1 = Engine.playHand(s, List.of(s.hand.get(0).id()));
                        assertTrue(!r1.ok, "通灵者：1 张应被拒（" + boss.key + "）");
                    }
                }
                default -> { }
            }

            // 回合推进（赢或合法败）+ 守恒
            s.roundScore = s.blindTarget;
            int guard = 0;
            while (s.phase == Phase.ROUND && guard++ < 25) {
                if (playAny(s)) break;
                if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else break;
            }
            Map<Integer, Integer> piles = new HashMap<>();
            for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
            for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
            for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
            Map<Integer, Integer> deck = new HashMap<>();
            for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
            int marbles = 0;
            for (var j : s.jokers) if (j.def.key().equals("marble")) marbles++;
            int diff = 0;
            for (var e : deck.entrySet()) diff += e.getValue() - piles.getOrDefault(e.getKey(), 0);
            for (var e : piles.entrySet()) diff += e.getValue() - deck.getOrDefault(e.getKey(), 0);
            assertTrue(diff == marbles, "守恒差=大理石（" + boss.key + "）：" + diff);
            assertTrue(s.phase != Phase.ROUND || s.handsLeft >= 0, "状态合法（" + boss.key + "）");
        }
    }
}
