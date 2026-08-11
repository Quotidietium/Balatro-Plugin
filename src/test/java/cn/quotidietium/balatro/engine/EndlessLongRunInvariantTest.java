package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 无尽模式长程不变量回归（轮次 R97）。
 *
 * <p>现有 endless 测试（FullMatrixSimulationTest 等）仅验证「能进入无尽 + 标记正确」，未覆盖
 * <b>跨多 ante 的长程不变量</b>——这正是无尽挂机长期运行的稳定性核心。本测试驱动通关 ante 8 后
 * 连续推进 20~30 个无尽 ante，断言：
 * <ul>
 *   <li>盲注目标分始终为正且不超过 {@link Long#MAX_VALUE}——{@code blindBase} 在 ante≥17 钳制为
 *       Long.MAX，{@code Math.round(base*mult)} 对 ≥Long.MAX 的正 double 饱和为 Long.MAX（Inf→Long.MAX），
 *       不可能环绕成负导致「必败软锁」；本测试在<b>行为层面</b>锁定该钳制（含进入钳制区的证据）；</li>
 *   <li>{@code s.blindTarget} 字段与纯函数 {@link Engine#blindTarget} 始终一致（胜负判定用的是字段）；</li>
 *   <li>ante 单调递增不倒退、阶段机正确推进（无卡死）；</li>
 *   <li>金钱始终 {@code >= 0}（gainMoney 饱和累加，不环绕成负）。</li>
 * </ul>
 *
 * <p><b>方法论</b>：诚实的计分出牌无法在无尽高 ante 达到天文数字目标分，故本测试聚焦 ante 推进/钳制
 * 不变量而非计分真实性——进入 ROUND 后预置 {@code roundScore = blindTarget}，第一次合法出牌即触发引擎
 * <b>完整</b>的胜利结算路径（score→satAdd→endRound→openShop→nextRound）。出牌/弃牌仍走引擎全部校验
 * （psychic 必须 5 张、bell 必须含强制牌、eye/mouth 牌型限制等），非法出牌探测不消耗出牌次数
 * （playHand 全部校验前置），因此滑窗穷举是安全且确定性的。
 */
class EndlessLongRunInvariantTest {

    /**
     * 打出任意一手合法牌。失败探测不消耗 handsLeft（playHand 校验前置、失败提前返回），可安全穷举。
     * 5 张滑窗同时覆盖 psychic（必须恰好 5 张）与 bell（强制牌在任何位置都被某个滑窗包含）。
     */
    private boolean playAnyValid(RunState s) {
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

    /**
     * 确定性地清空当前盲注（含前后阶段转换）：BLIND_SELECT→选盲注→ROUND→预置分后出牌获胜→SHOP→nextRound。
     * 同时在 ROUND 内断言字段一致性与目标分恒正。返回 false 表示无法推进（极端 Boss 限制 + 弃牌耗尽）。
     */
    private boolean clearCurrentBlind(RunState s) {
        if (s.phase == Phase.BLIND_SELECT) {
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        }
        if (s.phase != Phase.ROUND) return false;
        // 字段一致性：胜负判定读 s.blindTarget 字段，必须与纯函数计算一致且恒正
        assertEquals(Engine.blindTarget(s, s.blindType), s.blindTarget,
                "blindTarget 字段与纯函数不一致 ante=" + s.ante + " blind=" + s.blindType);
        assertTrue(s.blindTarget > 0,
                "回合内目标分必须为正（防必败软锁）ante=" + s.ante + " target=" + s.blindTarget);
        s.roundScore = s.blindTarget; // 第一次合法出牌（哪怕 0 分）即满足 won: roundScore >= blindTarget
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
        return s.phase != Phase.ROUND; // END（通关/败北）等终态由调用方判定
    }

    /** 快进到无尽待定：逐盲注确定性清空直至 ante 8 通关（endlessPending）。 */
    private void fastForwardToEndless(RunState s) {
        int guard = 0;
        while (!s.endlessPending && guard++ < 40) {
            assertTrue(clearCurrentBlind(s),
                    "通关路径盲注应可推进（无软锁）ante=" + s.ante + " next=" + s.nextBlind);
            if (s.phase == Phase.END && !s.endlessPending) break;
        }
        assertTrue(s.endlessPending, "应通关 ante 8 进入无尽待定 guard=" + guard);
    }

    @Test
    void endlessMultiAnteTargetsStayPositiveAndBounded() {
        RunState s = Engine.createRun("red", 0, "ENDLESSLR", null);
        fastForwardToEndless(s);
        assertTrue(Engine.continueEndless(s), "应能进入无尽模式");
        int startAnte = s.ante; // 9
        boolean sawClamp = false;
        for (int i = 0; i < 60; i++) { // 20 个无尽 ante × 3 盲注
            assertEquals(Phase.BLIND_SELECT, s.phase, "无尽每盲注应从 BLIND_SELECT 开始 i=" + i);
            long target = Engine.blindTarget(s, Data.BlindType.byKey(s.nextBlind));
            assertTrue(target > 0, "无尽目标分恒正（不环绕成负）ante=" + s.ante + " target=" + target);
            if (target == Long.MAX_VALUE) sawClamp = true;
            int anteBefore = s.ante;
            assertTrue(clearCurrentBlind(s), "无尽盲注应可推进 i=" + i + " ante=" + s.ante);
            assertTrue(s.ante >= anteBefore, "ante 不倒退 before=" + anteBefore + " after=" + s.ante);
            assertTrue(s.money >= 0, "金钱不环绕成负 ante=" + s.ante + " money=" + s.money);
        }
        assertEquals(startAnte + 20, s.ante, "60 盲注 = 20 ante（无跳过关卡，ante 精确推进）");
        assertTrue(sawClamp, "ante≥17 应进入 blindBase 钳制区（target==Long.MAX 至少一次，证明覆盖到钳制路径）");
    }

    @Test
    void endlessTargetNeverWrapsToNegativeAtGoldStake() {
        // 金注（7）：greenStake×1.15^(ante-1) 与 purpleStake×1.3^(ante-1) 双指数叠乘，目标分增长最快，
        // 是钳制/环绕风险最高的路径。推进 30 个无尽 ante（~ante 39，深入钳制区）。
        RunState s = Engine.createRun("red", 7, "ENDLESSSTK7", null);
        fastForwardToEndless(s);
        assertTrue(Engine.continueEndless(s), "金注应能进入无尽模式");
        boolean sawClamp = false;
        for (int i = 0; i < 90; i++) { // 30 个无尽 ante × 3 盲注
            if (s.phase != Phase.BLIND_SELECT) break;
            long target = Engine.blindTarget(s, Data.BlindType.byKey(s.nextBlind));
            assertTrue(target > 0,
                    "金注无尽目标分必须为正（双指数增长经 Math.round 饱和，不环绕成负）ante=" + s.ante + " target=" + target);
            if (target == Long.MAX_VALUE) sawClamp = true;
            assertTrue(clearCurrentBlind(s), "金注无尽盲注应可推进 i=" + i + " ante=" + s.ante);
            assertTrue(s.money >= 0, "金注无尽金钱不为负 ante=" + s.ante + " money=" + s.money);
        }
        assertTrue(sawClamp, "金注深无尽应进入钳制区（target==Long.MAX 至少一次）");
    }

    @Test
    void endlessReachesEndlessAcrossSeedsWithoutSoftLock() {
        // 不同种子 → 不同 Boss 顺序（psychic/bell/needle/water/house/leaf/wall/vessel 等），
        // 验证确定性推进在任何 Boss 序列下都不软锁、都能抵达无尽并继续推进。
        String[] seeds = {"ENDA", "ENDB", "ENDC"};
        for (String seed : seeds) {
            RunState s = Engine.createRun("red", 0, seed, null);
            fastForwardToEndless(s); // 内部断言 endlessPending
            assertTrue(Engine.continueEndless(s), seed + " 应能进入无尽");
            for (int i = 0; i < 15; i++) { // 5 个无尽 ante，抽样验证目标分恒正
                if (s.phase != Phase.BLIND_SELECT) break;
                long target = Engine.blindTarget(s, Data.BlindType.byKey(s.nextBlind));
                assertTrue(target > 0, seed + " 无尽目标分恒正 ante=" + s.ante);
                assertTrue(clearCurrentBlind(s), seed + " 无尽盲注应可推进 i=" + i);
            }
        }
    }
}
