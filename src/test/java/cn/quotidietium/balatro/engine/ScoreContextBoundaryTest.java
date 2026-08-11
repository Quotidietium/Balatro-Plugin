package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ScoreContext 方法边界验证（轮次 46）。
 *
 * <p>验证 prob/rngInt/isSuit/isFace/handIs 在边界值下的正确行为。
 */
class ScoreContextBoundaryTest {

    private ScoreContext ctxWithFlags(Map<String, Object> flags) {
        RunState s = new RunState("SCBOUND");
        s.flags = flags != null ? flags : new HashMap<>();
        return new ScoreContext(s, Data.HandType.HIGH, 0, 0, List.of(), List.of(), new ArrayList<>());
    }

    @Test
    void probDoubleProbFlagDoubles() {
        int trueCount = 0;
        for (int i = 0; i < 1000; i++) {
            RunState s = new RunState("SCD" + i);
            s.flags = new HashMap<>();
            s.flags.put("doubleProb", true);
            ScoreContext ctx = new ScoreContext(s, Data.HandType.HIGH, 0, 0, List.of(), List.of(), new ArrayList<>());
            if (ctx.prob(0.25)) trueCount++;
        }
        assertTrue(trueCount > 400 && trueCount < 600,
                "doubleProb 下 0.25→0.5 应约 500/1000，实际 " + trueCount);
    }

    @Test
    void probWithoutDoubleProb() {
        int trueCount = 0;
        for (int i = 0; i < 1000; i++) {
            RunState s = new RunState("SCN" + i);
            s.flags = new HashMap<>();
            ScoreContext ctx = new ScoreContext(s, Data.HandType.HIGH, 0, 0, List.of(), List.of(), new ArrayList<>());
            if (ctx.prob(0.25)) trueCount++;
        }
        assertTrue(trueCount > 180 && trueCount < 320,
                "无 doubleProb 下 0.25 应约 250/1000，实际 " + trueCount);
    }

    @Test
    void probCappedAt1() {
        RunState s = new RunState("SCCAP");
        s.flags = new HashMap<>();
        s.flags.put("doubleProb", true);
        ScoreContext ctx = new ScoreContext(s, Data.HandType.HIGH, 0, 0, List.of(), List.of(), new ArrayList<>());
        // doubleProb + p=0.6 → min(1, 1.2)=1.0 → chance(1.0) 总是 true
        assertTrue(ctx.prob(0.6), "doubleProb 下 0.6→min(1,1.2)=1.0 应总是 true");
    }

    @Test
    void probZeroAlwaysFalse() {
        RunState s = new RunState("SCZERO");
        s.flags = new HashMap<>();
        ScoreContext ctx = new ScoreContext(s, Data.HandType.HIGH, 0, 0, List.of(), List.of(), new ArrayList<>());
        assertFalse(ctx.prob(0.0), "p=0 总是 false");
    }

    @Test
    void rngIntInRange() {
        for (int i = 0; i < 1000; i++) {
            ScoreContext ctx = ctxWithFlags(null);
            int v = ctx.rngInt(2, 14);
            assertTrue(v >= 2 && v <= 14, "rngInt(2,14) 应在 [2,14] 范围内: " + v);
        }
    }

    @Test
    void rngIntSingleValue() {
        ScoreContext ctx = ctxWithFlags(null);
        assertEquals(5, ctx.rngInt(5, 5), "rngInt(5,5) 应恒为 5");
    }

    @Test
    void isSuitRespectsSmearedFlag() {
        RunState s = new RunState("SCSMEAR");
        s.flags = new HashMap<>();
        Card heart = new Card(1, 10, 1); // 红桃 10
        // 无 smeared：红桃(1) 不匹配方块(3)
        assertFalse(s.isSuit(heart, 3), "无 smeared 红桃不匹配方块");
        // smeared 通过 flags（computeFlags 设）
        s.flags.put("smeared", true);
        assertTrue(s.isSuit(heart, 3), "smeared 红桃匹配方块（红系）");
        assertTrue(s.isSuit(heart, 1), "smeared 红桃匹配红桃");
    }

    @Test
    void isFaceAllFaceFlag() {
        RunState s = new RunState("SCFACE");
        s.flags = new HashMap<>();
        s.flags.put("allFace", true);
        Card five = new Card(1, 5, 0); // 黑桃 5（非人头）
        assertTrue(s.isFace(five), "allFace 时非人头牌视为人头");

        s.flags.put("allFace", false);
        assertFalse(s.isFace(five), "无 allFace 时 5 不是人头");

        Card king = new Card(2, 13, 0);
        assertTrue(s.isFace(king), "K 总是人头");
    }

    @Test
    void isFaceStoneCard() {
        // 石头牌 rank=0，不是人头
        RunState s = new RunState("SCSTONE");
        s.flags = new HashMap<>();
        Card stone = new Card(1, 0, -1);
        stone.setEnh(Data.Enhancement.STONE);
        assertFalse(s.isFace(stone), "石头牌不是人头");
    }

    @Test
    void handIsMatchesKey() {
        RunState s = new RunState("SCHAND");
        s.flags = new HashMap<>();
        ScoreContext ctx = new ScoreContext(s, Data.HandType.PAIR, 0, 0, List.of(), List.of(), new ArrayList<>());
        assertTrue(ctx.handIs("pair"));
        assertFalse(ctx.handIs("flush"));
    }

    @Test
    void probConsumesProbStream() {
        // prob 和 rngInt 都用 stream("prob")——多次调用推进同一条流
        ScoreContext ctx = ctxWithFlags(null);
        double v1 = ctx.state.stream("prob").next();
        // prob 内部也消耗 stream("prob")
        boolean b = ctx.prob(0.5);
        double v2 = ctx.state.stream("prob").next();
        assertNotEquals(v1, v2, "prob 调用后 stream 应推进");
    }
}
