package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * To the Moon（奔月）规则验证：应给「每 $5 额外 +$1 利息」（不受 $5 上限），
 * 而非 REF JS 的「每剩余出牌 +$1」。([Wiki](https://balatrowiki.org/w/To_the_Moon))
 */
class ToTheMoonTest {

    @Test
    void givesExtraInterestPerFiveDollars() {
        RunState s = Engine.createRun("red", 0, "x");
        JokerInstance j = new JokerInstance(BasicJoker.TOTHEMOON);
        s.jokers.add(j);

        s.money = 25;
        assertEquals(5, BasicJoker.TOTHEMOON.onRoundEnd(s, j), "$25 → 额外 +$5");

        s.money = 50;
        assertEquals(10, BasicJoker.TOTHEMOON.onRoundEnd(s, j), "$50 → 额外 +$10（不受基础利息 $5 上限约束）");

        s.money = 12;
        assertEquals(2, BasicJoker.TOTHEMOON.onRoundEnd(s, j), "$12 → 额外 +$2（整除）");

        s.money = 4;
        assertEquals(0, BasicJoker.TOTHEMOON.onRoundEnd(s, j), "$4 → +$0");
    }
}
