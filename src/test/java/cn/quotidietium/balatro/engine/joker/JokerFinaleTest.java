package cn.quotidietium.balatro.engine.joker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 「回合最后一手」类小丑回归：dusk(黄昏 重触发) / acrobat(杂技演员 ×3)。
 *
 * <p>计分发生在 {@code handsLeft--} 之前，故"最后一手"计分时 {@code handsLeft==1}。
 * 原版网页误写 {@code ==0}（永不触发），已按真实 Balatro 规则修正
 * （见 note/release/逻辑审计.md #8/#9）。
 *
 * <p>基准：单张 A（黑桃）= 高牌 5 筹码 + 11 筹码，×1 倍率 = 16 分。
 */
class JokerFinaleTest {

    /** 构造单张黑桃 A 手牌、指定剩余出牌数的局面（目标分设为不可达，避免提前胜出）。 */
    private static RunState setup(String seed, String jokerKey, int handsLeft) {
        RunState s = Engine.createRun("red", 0, seed);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        s.jokers.add(JokerRegistry.create(jokerKey));
        Card ace = s.makeCard(14, 0);
        s.hand.clear();
        s.hand.add(ace);
        s.handsLeft = handsLeft;
        s.blindTarget = Long.MAX_VALUE;
        return s;
    }

    @Test
    void duskRetriggersOnFinalHand() {
        RunState s = setup("DUSKFINAL", "dusk", 1);
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        // 高牌 5 + A 的 11 筹码计两次（重触发 1 次），×1 倍率
        assertEquals(5 + 22, r.score, "最后一手应重触发计分牌");
    }

    @Test
    void duskNoRetriggerBeforeFinalHand() {
        RunState s = setup("DUSKEARLY", "dusk", 2);
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(16, r.score, "非最后一手不应重触发");
    }

    @Test
    void acrobatTripleMultOnFinalHand() {
        RunState s = setup("ACROBATFINAL", "acrobat", 1);
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(16 * 3, r.score, "最后一手应 ×3 倍率");
    }

    @Test
    void acrobatNoMultBeforeFinalHand() {
        RunState s = setup("ACROBATEARLY", "acrobat", 2);
        Engine.PlayResult r = Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(16, r.score, "非最后一手不应 ×3");
    }
}
