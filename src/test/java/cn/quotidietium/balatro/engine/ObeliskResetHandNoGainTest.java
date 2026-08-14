package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Obelisk 重置手不获增量（R133，第 63 处；真版效果文本 "gains X0.2 per consecutive hand
 * played WITHOUT playing your most played"——打出最常用牌型的手不计入连续）。
 */
class ObeliskResetHandNoGainTest {

    @Test
    void resetHandGainsNothing() {
        RunState s = Engine.createRun("red", 0, "OBNG1", null);
        var ob = JokerRegistry.create("obelisk");
        s.jokers.add(ob);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 重置判定发生在【计分前】且基于打出前计数：
        // h1 high：计数为空 → 无重置对象 → 首手恒累积 → x=0.2（真版：首手无"最常用"可打）。
        // h2 pair：pair=1 与 high=1 并列 → 并列安全 → x=0.4。
        // h3 pair：pair=2 唯一最常用 → 计分前重置 → x=0 且该手【不】+0.2（R133 核心断言）。
        // 构造确定性点数：h1 单张=high；h2/h3 显式两张同点=pair（随机手牌顶部两张未必成对）
        s.roundScore = 0;
        Engine.playHand(s, List.of(s.hand.get(0).id())); // high（单张）
        assertEquals(0.2, ((Number) ob.extra.get("x")).doubleValue(), 1e-9, "首手恒累积（计数前置为空）");
        Engine.sortHand(s);
        s.hand.get(0).setRank(9);
        s.hand.get(1).setRank(9);
        s.roundScore = 0;
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id())); // pair=1 与 high=1 并列
        assertEquals(0.4, ((Number) ob.extra.get("x")).doubleValue(), 1e-9, "并列安全 → +0.2");
        Engine.sortHand(s);
        s.hand.get(0).setRank(9);
        s.hand.get(1).setRank(9);
        s.roundScore = 0;
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id())); // pair 计数后=2
        // h3 计分前仍并列（pair=1 vs high=1）→ 安全累积至 0.6；计数后 pair=2 成唯一最常用
        assertEquals(0.6000000000000001, ((Number) ob.extra.get("x")).doubleValue(), 1e-9,
                "h3 预检查仍并列 → 继续累积");
        Engine.sortHand(s);
        s.hand.get(0).setRank(9);
        s.hand.get(1).setRank(9);
        s.roundScore = 0;
        Engine.playHand(s, List.of(s.hand.get(0).id(), s.hand.get(1).id())); // 预检查 pair=2 严格唯一 → 重置
        assertEquals(0.0, ((Number) ob.extra.get("x")).doubleValue(), 1e-9,
                "重置手不获增量（X1 而非 X1.2）");
    }
}