package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 城堡（Castle）/ 远古（Ancient）等「每回合换目标」小丑的 onRoundEnd 轮换钩子回归（轮次 R9）。
 *
 * <p>核验的不变量（深审确认 REF 对齐，此处显式锁定防回归）：
 * <ul>
 *   <li>Castle 弃指定花色牌 → 永久 +3 筹码（累积，跨回合不重置）；</li>
 *   <li>onRoundEnd 在盲注胜出时由 {@code Engine.endRound} 调用，重新抽取花色（值域 0~3）；</li>
 *   <li>换花色后，弃「新花色」的牌继续 +3，弃「非新花色」的牌不加分（轮换生效）。</li>
 * </ul>
 */
class CastleRotationTest {

    private static int chips(JokerInstance j) {
        Object v = j.extra.get("chips");
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static int suitOf(JokerInstance j) {
        Object v = j.extra.get("suit");
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    @Test
    void castleAccumulatesAndRotatesSuitAtRoundEnd() {
        RunState s = Engine.createRun("red", 0, "CASTLE01");
        JokerInstance castle = new JokerInstance(BasicJoker.CASTLE);
        s.jokers.add(castle);

        // 初始花色默认 0（gi 默认）：弃 2 张黑桃(suit 0) → 永久 +6 筹码
        Card spadeA = s.makeCard(14, 0);
        Card spadeK = s.makeCard(13, 0);
        BasicJoker.CASTLE.onDiscard(s, List.of(spadeA, spadeK), castle);
        assertEquals(6, chips(castle), "弃 2 张指定花色牌应 +6 筹码");

        // 弃非指定花色（红桃 suit 1）→ 不加分
        Card heartQ = s.makeCard(12, 1);
        BasicJoker.CASTLE.onDiscard(s, List.of(heartQ), castle);
        assertEquals(6, chips(castle), "弃非指定花色牌不应加分");

        // onRoundEnd：重新抽花色（盲注胜出时由 endRound 触发），筹码应保留（永久，不重置）
        BasicJoker.CASTLE.onRoundEnd(s, castle);
        assertEquals(6, chips(castle), "换花色后累积筹码应保留（永久）");
        int newSuit = suitOf(castle);
        assertTrue(newSuit >= 0 && newSuit <= 3, "新花色应在 0~3 范围内，实际=" + newSuit);

        // 轮换生效：弃「新花色」的牌 → +3；弃其他花色 → 不加
        Card matchNew = s.makeCard(2, newSuit);
        BasicJoker.CASTLE.onDiscard(s, List.of(matchNew), castle);
        assertEquals(9, chips(castle), "弃新花色牌应 +3（轮换已生效）");

        int otherSuit = (newSuit + 1) % 4;
        Card nonMatch = s.makeCard(3, otherSuit);
        BasicJoker.CASTLE.onDiscard(s, List.of(nonMatch), castle);
        assertEquals(9, chips(castle), "弃非新花色牌不应加分");
    }
}
