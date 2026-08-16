package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R202：无尽深底注 **新种子族**长程不变量——EndlessLongRunInvariantTest（R97/R145）
 * 用固定种子族（ENDA/ENDLESSLR 等）；本轮以全新种子族（ENDEEP-*）在未测试随机
 * 空间上复验同组长程不变量：目标分恒正且经钳制区、字段与纯函数一致、ante 单调、
 * 金钱非负——新种子探索第四维（无尽长程）。
 */
class EndlessFreshSeedTest {

    private static boolean playAnyValid(RunState s) {
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

    private static boolean clearCurrentBlind(RunState s) {
        if (s.phase == Phase.BLIND_SELECT) {
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        }
        if (s.phase != Phase.ROUND) return false;
        assertEquals(Engine.blindTarget(s, s.blindType), s.blindTarget,
                "blindTarget 字段与纯函数一致（seed 新族 ante=" + s.ante + "）");
        s.roundScore = s.blindTarget;
        int guard = 0;
        while (s.phase == Phase.ROUND && guard++ < 40) {
            if (playAnyValid(s)) continue;
            if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                List<Integer> disc = new ArrayList<>();
                for (int i = 0; i < Math.min(5, s.hand.size()); i++) disc.add(s.hand.get(i).id());
                if (Engine.discard(s, disc).ok) continue;
            }
            return false;
        }
        if (s.phase == Phase.SHOP) return Engine.nextRound(s);
        return s.phase != Phase.ROUND;
    }

    @Test
    void freshSeedEndlessRunsHoldLongRunInvariants() {
        for (int k = 0; k < 3; k++) {
            RunState s = Engine.createRun(k % 2 == 0 ? "red" : "green", k, "ENDEEP-" + k, null);
            int guard = 0;
            while (!s.endlessPending && guard++ < 45) {
                assertTrue(clearCurrentBlind(s), "通关路径可推进（ENDEEP-" + k + "）");
                if (s.phase == Phase.END && !s.endlessPending) break;
            }
            assertTrue(s.endlessPending, "应通关 ante8（ENDEEP-" + k + " guard=" + guard + "）");
            assertTrue(Engine.continueEndless(s), "应进入无尽");
            int startAnte = s.ante;
            boolean sawClamp = false;
            for (int i = 0; i < 45; i++) { // 15 个无尽 ante × 3 盲注
                if (s.phase != Phase.BLIND_SELECT) break;
                long target = Engine.blindTarget(s, Data.BlindType.byKey(s.nextBlind));
                assertTrue(target > 0, "无尽目标分恒正（ENDEEP-" + k + " ante=" + s.ante + " t=" + target + "）");
                if (target == Long.MAX_VALUE) sawClamp = true;
                int anteBefore = s.ante;
                assertTrue(clearCurrentBlind(s), "无尽盲注可推进（ENDEEP-" + k + " i=" + i + "）");
                assertTrue(s.ante >= anteBefore, "ante 单调（ENDEEP-" + k + "）");
                assertTrue(s.money >= 0, "金钱非负（ENDEEP-" + k + " ante=" + s.ante + " m=" + s.money + "）");
            }
            assertTrue(s.ante >= startAnte + 10, "应深入无尽多 ante（ENDEEP-" + k + "：" + startAnte + "→" + s.ante + "）");
            assertTrue(sawClamp, "应进入钳制区（ENDEEP-" + k + "）");
        }
    }
}
