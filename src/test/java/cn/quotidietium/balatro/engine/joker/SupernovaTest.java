package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.ScoreContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Supernova 规则验证：+Mult 应等于「本局打出该牌型的次数（含当前这次）」。
 * REF JS 在增量前读取导致少 1，已修正为 count+1。
 */
class SupernovaTest {

    private ScoreContext ctxFor(RunState s, Data.HandType type, long chips, long mult) {
        return new ScoreContext(s, type, chips, mult, List.of(), List.of(), new ArrayList<>());
    }

    @Test
    void includesCurrentPlay() {
        RunState s = Engine.createRun("red", 0, "x");
        s.phase = Phase.ROUND;
        // 本局此前已打出 5 次对子（第 6 次应给 +6，而非 +5）
        s.handPlayedCount.merge(Data.HandType.PAIR, 5, Integer::sum);
        ScoreContext ctx = ctxFor(s, Data.HandType.PAIR, 10, 2);
        BasicJoker.SUPERNOVA.onScore(ctx);
        assertEquals(2 + 6, (long) ctx.mult, "第 6 次打出对子应 +6（含本次）");
    }

    @Test
    void firstPlayGivesOne() {
        RunState s = Engine.createRun("red", 0, "x");
        s.phase = Phase.ROUND;
        ScoreContext ctx = ctxFor(s, Data.HandType.HIGH, 5, 1);
        BasicJoker.SUPERNOVA.onScore(ctx);
        assertEquals(1 + 1, (long) ctx.mult, "首次打出应 +1（含本次）");
    }
}
