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
 * Card Sharp 规则修正验证：×3 倍率应在「同一回合内再次打出该牌型」时触发（回合级），
 * 而非 REF JS 误用的「整局已打出次数 >1」（run 级）。
 */
class CardSharpTest {

    private ScoreContext ctxFor(RunState s, Data.HandType type, long chips, long mult) {
        return new ScoreContext(s, type, chips, mult, List.of(), List.of(), new ArrayList<>());
    }

    @Test
    void triggersWhenPlayedEarlierThisRound() {
        RunState s = Engine.createRun("red", 0, "x");
        s.phase = Phase.ROUND;
        s.playedTypesThisRound.add(Data.HandType.PAIR); // 本回合已出过对子
        ScoreContext ctx = ctxFor(s, Data.HandType.PAIR, 10, 2);
        BasicJoker.CARDSHARP.onScore(ctx);
        assertEquals(6, (long) ctx.mult, "再次打出对子应 ×3");
    }

    @Test
    void noTriggerOnFirstPlayOfRound() {
        RunState s = Engine.createRun("red", 0, "x");
        s.phase = Phase.ROUND;
        // 本回合尚未出过任何牌型
        ScoreContext ctx = ctxFor(s, Data.HandType.PAIR, 10, 2);
        BasicJoker.CARDSHARP.onScore(ctx);
        assertEquals(2, (long) ctx.mult, "本回合首次打出不应 ×3");
    }

    @Test
    void runLevelCountDoesNotMatter() {
        // 即使整局打过很多次，但本回合首次打出也不触发（区别于 run 级 bug）
        RunState s = Engine.createRun("red", 0, "x");
        s.phase = Phase.ROUND;
        s.handPlayedCount.merge(Data.HandType.PAIR, 5, Integer::sum); // 整局已出 5 次对子
        ScoreContext ctx = ctxFor(s, Data.HandType.PAIR, 10, 2);
        BasicJoker.CARDSHARP.onScore(ctx);
        assertEquals(2, (long) ctx.mult, "本回合首次打出不应 ×3，即使整局打过多次");
    }
}
