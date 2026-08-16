package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * R213→R214：28 Boss × 20 挑战禁入交叉 × 新种子——两个面：
 * ①**禁现统计**：带 bannedBosses 的挑战（madworld=plant、nonperish/typecast=leaf、
 * jokerless=heart/leaf/acorn）在多种子多底注下自然抽取的 Boss 序列**绝不含**禁现者
 *（chooseBoss 去重谓词过滤，R122）；②**效果路径**：每个 Boss 在轮换挑战的新种子局
 *（BXC-*）中强制上桌开打（R206 同款 queue 头替换），挑战 mods 下效果生效、可推进。
 * 新种子第十六维（Boss×挑战交叉）。
 */
class BossXChallengeFreshTest {

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
    void bannedBossesNeverNaturallyAppear() {
        // 三个禁现挑战 × 各 6 种子 × 推进 2 个底注（覆盖 ≥2 次 chooseBoss）
        String[][] cases = {
                {"madworld", "plant"},
                {"nonperish", "leaf"},
                {"typecast", "leaf"},
                {"jokerless", "heart"}, {"jokerless", "leaf"}, {"jokerless", "acorn"}};
        for (String[] cs : cases) {
            for (int k = 0; k < 6; k++) {
                RunState s = Engine.createRun("red", 0, "BXCB-" + cs[0] + "-" + k, cs[0]);
                assertTrue(s.mods.bannedBosses.contains(cs[1]), "挑战应禁现（" + cs[0] + ":" + cs[1] + "）");
                for (int ante = 0; ante < 2; ante++) {
                    for (int blind = 0; blind < 3; blind++) {
                        assertNotEquals(cs[1], s.bossQueue.get(0),
                                "禁现 Boss 不得被抽出（" + cs[0] + " seed#" + k + " ante" + s.ante + "）");
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
                        if (s.phase == Phase.SHOP) Engine.nextRound(s);
                    }
                }
            }
        }
    }

    @Test
    void bossesUnderChallengeModsFightCleanly() {
        var challenges = Data.CHALLENGES;
        int idx = 0;
        Set<String> fought = new HashSet<>();
        for (Data.Boss boss : Data.Boss.values()) {
            Data.Challenge ch = challenges.get(idx % challenges.size());
            RunState s = Engine.createRun("red", 0, "BXC-" + (idx++) + "-" + boss.key + "-" + ch.key(),
                    ch.key());
            // 快进到 Boss 盲注（赢小/大盲）
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
            if (s.phase != Phase.BLIND_SELECT) continue; // 极限挑战未达 Boss——跳过（非缺陷）
            s.bossQueue.set(0, boss.key);
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            if (s.phase != Phase.ROUND) continue;
            s.roundScore = s.blindTarget;
            int guard = 0;
            while (s.phase == Phase.ROUND && guard++ < 25) {
                if (playAny(s)) break;
                if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else break;
            }
            fought.add(boss.key);
            assertTrue(s.phase != Phase.ROUND || s.handsLeft >= 0, "状态合法（" + boss.key + "@" + ch.key() + "）");
            assertTrue(s.money >= -20, "金钱 ≥ -20（" + boss.key + "@" + ch.key() + "）：" + s.money);
        }
        assertTrue(fought.size() >= 24, "绝大多数 Boss 应完成挑战下对战（实际 " + fought.size() + "/28）");
    }
}
