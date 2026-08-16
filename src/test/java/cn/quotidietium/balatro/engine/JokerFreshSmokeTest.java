package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R208：150 小丑 × **新种子族**全量 smoke——JokerGoldenTest（固定种子 50+ 例）
 * 与各专项测试之后，以全新种子族（JFS-*）对**每个**小丑在独立新种子局中：
 * gainJoker 持有 → 出牌一轮（触发 onScore/onScoreCard/onHeld/onPlayHand 主钩子链）
 * → 弃牌一轮（onDiscard）→ 断言不崩 + 钩子副作用后守恒 + 金钱下界。
 * 新种子探索第十维（小丑全量）。
 */
class JokerFreshSmokeTest {

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
    void all150JokersOnFreshSeedsTriggerCleanly() {
        var all = cn.quotidietium.balatro.engine.joker.JokerRegistry.allJokersOrdered();
        assertTrue(all.size() == 150, "恰 150：" + all.size());
        int idx = 0;
        for (var def : all) {
            RunState s = Engine.createRun("red", idx % 2, "JFS-" + (idx++) + "-" + def.key(), null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            assertTrue(s.gainJoker(def.key(), null), "持有应成功（" + def.key() + "）");

            // 出牌一轮（主钩子链）+ 弃牌一轮（onDiscard 链）
            int guard = 0;
            while (s.phase == Phase.ROUND && guard++ < 8) {
                if (playAny(s)) break;
                if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else break;
            }
            if (s.phase == Phase.ROUND && s.discardsLeft > 0 && !s.hand.isEmpty()) {
                Engine.discard(s, List.of(s.hand.get(0).id()));
            }

            // 不变量：守恒（差=大理石；无包选故无 pending）+ 金钱下界 + 状态合法
            Map<Integer, Integer> piles = new HashMap<>();
            for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
            for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
            for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
            Map<Integer, Integer> deck = new HashMap<>();
            for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
            int diff = 0;
            for (var e : deck.entrySet()) diff += e.getValue() - piles.getOrDefault(e.getKey(), 0);
            for (var e : piles.entrySet()) diff += e.getValue() - deck.getOrDefault(e.getKey(), 0);
            // 本测试在 startRound 之后才 gainJoker——marble 的 onBlindStart 尚未触发
            //（下一回合才 pending）、certificate 的 onRoundStart 同理；DNA 加牌双表同增。
            // 故此闭包内严格守恒（diff==0）成立。
            assertTrue(diff == 0, "本闭包严格守恒（" + def.key() + "）：" + diff);
            assertTrue(s.money >= -20, "金钱 ≥ -20（信用卡兜底）（" + def.key() + "）：" + s.money);
            assertTrue(s.phase == Phase.ROUND || s.phase == Phase.SHOP || s.phase == Phase.END,
                    "状态合法（" + def.key() + "）：" + s.phase);
        }
    }
}
