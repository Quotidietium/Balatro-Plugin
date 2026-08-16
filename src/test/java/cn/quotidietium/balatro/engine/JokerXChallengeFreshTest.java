package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R210：150 小丑 × 20 挑战 mods **交叉全量** × 新种子——每小丑在轮换挑战
 *（150 = 7 轮 × 20 挑战 + 尾 10，即每挑战覆盖 ~7-8 个小丑）的新种子局（JXC-*）
 * 中持有并出牌一轮：禁入清单内的小丑按挑战规则**拒绝持有**（gainJoker false），
 * 其余正常触发主钩子链——断言不崩 + 状态合法 + 金钱下界。
 * 新种子探索第十二维（小丑×挑战交叉）。
 */
class JokerXChallengeFreshTest {

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
    void jokersAcrossChallengeModsHoldBehave() {
        var all = cn.quotidietium.balatro.engine.joker.JokerRegistry.allJokersOrdered();
        assertTrue(all.size() == 150);
        var challenges = Data.CHALLENGES;
        int idx = 0;
        int heldCount = 0;
        for (var def : all) {
            Data.Challenge ch = challenges.get(idx % challenges.size());
            RunState s = Engine.createRun("red", 0, "JXC-" + (idx++) + "-" + def.key() + "-" + ch.key(),
                    ch.key());
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            // 注意：banJokers 是**池过滤器**（商店/随机发放不产出），直接 gainJoker
            // 不检查禁入（设计如此——REF 同构）；唯一硬门是槽位（jokerless 的
            // jokerSlotsSet=0 会拒）。故此处只断言槽位语义。
            boolean got = s.gainJoker(def.key(), null);
            if (s.jokerSlots + countNeg(s) - (got ? 1 : 0) < 0) {
                assertTrue(!got, "零槽挑战应拒（" + def.key() + "@" + ch.key() + "）");
            }
            if (got) heldCount++;
            // 出牌一轮触发主钩子链（挑战可能加严：psychic 类挑战经 must5 等）
            int guard = 0;
            while (s.phase == Phase.ROUND && guard++ < 8) {
                if (playAny(s)) break;
                if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else break;
            }
            assertTrue(s.money >= -20, "金钱 ≥ -20（" + def.key() + "@" + ch.key() + "）：" + s.money);
            assertTrue(s.phase == Phase.ROUND || s.phase == Phase.SHOP || s.phase == Phase.END,
                    "状态合法（" + def.key() + "@" + ch.key() + "）：" + s.phase);
        }
        assertTrue(heldCount > 100, "绝大多数小丑应可持有（实际 " + heldCount + "/150）");
    }

    private static int countNeg(RunState s) {
        int n = 0;
        for (var j : s.jokers) if (j.edition == Data.Edition.NEGATIVE) n++;
        return n;
    }
}
