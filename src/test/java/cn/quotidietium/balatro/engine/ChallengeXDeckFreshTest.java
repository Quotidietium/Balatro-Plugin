package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R215：20 挑战 × 15 牌组**全叉叉** × 新种子——300 个组合各 1 全新种子
 *（CXD-*）的速胜全循环 smoke（≤2 盲注）：不崩 + 金钱下界 + 状态合法。
 * 全维联合大 smoke：挑战 mods × 牌组构建变体（erratic 随机/checkered 半红黑/
 * plasma 平均/黑+1 槽-1 出牌等）的全部交互在新种子上过一遍。
 * 新种子第十七维（挑战×牌组矩阵）。
 */
class ChallengeXDeckFreshTest {

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
    void allChallengeDeckCombosOnFreshSeedsBehave() {
        String[] decks = {"red", "blue", "yellow", "green", "black", "magic", "nebula",
                "ghost", "abandoned", "checkered", "zodiac", "painted", "anaglyph", "plasma", "erratic"};
        assertTrue(decks.length == 15);
        int progressed = 0;
        for (Data.Challenge ch : Data.CHALLENGES) {
            for (int d = 0; d < decks.length; d++) {
                RunState s = Engine.createRun(decks[d], d % 3, "CXD-" + ch.key() + "-" + decks[d], ch.key());
                for (int blind = 0; blind < 2; blind++) {
                    if (s.phase == Phase.END) break;
                    if (s.phase == Phase.BLIND_SELECT) {
                        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                    }
                    if (s.phase != Phase.ROUND) break;
                    s.roundScore = s.blindTarget;
                    int guard = 0;
                    while (s.phase == Phase.ROUND && guard++ < 25) {
                        if (playAny(s)) break;
                        if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                            Engine.discard(s, List.of(s.hand.get(0).id()));
                        } else break;
                    }
                    if (s.phase == Phase.SHOP) {
                        progressed++;
                        Engine.nextRound(s);
                    }
                }
                assertTrue(s.money >= -20, "金钱 ≥ -20（" + ch.key() + "@" + decks[d] + "）：" + s.money);
                assertTrue(s.phase == Phase.BLIND_SELECT || s.phase == Phase.ROUND
                        || s.phase == Phase.SHOP || s.phase == Phase.END,
                        "状态合法（" + ch.key() + "@" + decks[d] + "）：" + s.phase);
            }
        }
        assertTrue(progressed > 150, "多数组合应至少赢下一盲（实际 " + progressed + "/300）");
    }
}
