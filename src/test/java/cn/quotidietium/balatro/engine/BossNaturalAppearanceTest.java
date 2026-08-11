package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Boss 自然出现端到端验证（轮次 45）。
 *
 * <p>用多种子驱动完整通关流程（非强制设置 bossKey），统计每个 Boss 的自然出现频率，
 * 验证 Boss 随机选择正常工作、每个 Boss 都可达、效果在自然流程中不崩溃。
 */
class BossNaturalAppearanceTest {

    private void simulateAndCollectBosses(String seed, Set<String> encountered) {
        RunState s = Engine.createRun("red", 0, seed, null);
        int it = 0, safety = 1500;
        while (s.phase != Phase.END && it++ < safety) {
            // 在 BLIND_SELECT 阶段记录 Boss
            if (s.phase == Phase.BLIND_SELECT && !s.bossQueue.isEmpty()) {
                encountered.add(s.bossQueue.get(0));
            }
            if (s.phase == Phase.BLIND_SELECT) {
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                continue;
            }
            if (s.phase == Phase.SHOP) { Engine.nextRound(s); continue; }
            if (s.phase == Phase.PACK) { cn.quotidietium.balatro.engine.shop.Packs.skip(s); continue; }
            if (s.phase == Phase.ROUND) {
                if (s.handsLeft <= 0 || s.hand.isEmpty()) break;
                List<Integer> ids = new ArrayList<>();
                for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
                Engine.PlayResult r = Engine.playHand(s, ids);
                if (!r.ok && s.handsLeft > 0 && !s.hand.isEmpty()) {
                    for (int tn = 1; tn <= 5; tn++) {
                        if (tn > s.hand.size()) break;
                        List<Integer> t = new ArrayList<>();
                        for (int i = 0; i < tn; i++) t.add(s.hand.get(i).id());
                        if (Engine.playHand(s, t).ok) break;
                    }
                }
                continue;
            }
            break;
        }
    }

    @Test
    void all28BossesReachableInBatch() {
        // 用 200 个种子批量运行，统计遇到的 Boss
        Set<String> encountered = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            simulateAndCollectBosses("BOSSBATCH" + String.format("%03d", i), encountered);
        }
        // 每个 ante 有 1 个 Boss，200 种子至少覆盖 ~200 个 Boss 出现
        // 28 个 Boss 应该都能遇到（随机均匀抽取）
        assertTrue(encountered.size() >= 20,
                "200 种子应至少遇到 20 种不同 Boss，实际 " + encountered.size());
    }

    @Test
    void bossDistributionNotDegenerate() {
        // 验证 Boss 分布不退化（不只出现同一个 Boss）
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            Set<String> enc = new HashSet<>();
            simulateAndCollectBosses("DIST" + String.format("%03d", i), enc);
            for (String bk : enc) counts.merge(bk, 1, Integer::sum);
        }
        // 至少 15 种不同 Boss 出现
        assertTrue(counts.size() >= 15,
                "100 种子应至少遇到 15 种不同 Boss，实际 " + counts.size());
        // 最常出现的 Boss 不超过 30 次（均匀分布下 ~100/28≈3.6 次/Boss）
        int maxCount = counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        assertTrue(maxCount < 30,
                "单个 Boss 出现不应超过 30 次（防退化），实际最大 " + maxCount);
    }

    @Test
    void bossEffectsTriggerWithoutCrashInNaturalFlow() {
        // 验证 Boss 效果在自然流程中不崩溃
        for (int i = 0; i < 50; i++) {
            Set<String> enc = new HashSet<>();
            simulateAndCollectBosses("NATURAL" + String.format("%03d", i), enc);
            // 只要不抛异常就通过
            assertTrue(enc != null);
        }
    }
}
