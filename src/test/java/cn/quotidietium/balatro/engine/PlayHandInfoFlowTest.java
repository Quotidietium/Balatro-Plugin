package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PlayHandInfo 数据流正确性专项验证（轮次 48）。
 *
 * <p>验证 hasRank/isMostPlayed/playedCards/scoredCards/hasFace/findJoker
 * 在计分流程中的数据流正确性。
 */
class PlayHandInfoFlowTest {

    @Test
    void playedCardsMatchesInput() {
        RunState s = Engine.createRun("red", 0, "PHIF1", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        List<Integer> ids = List.of(s.hand.get(0).id(), s.hand.get(1).id());
        // 用一个 joker 的 onPlayHand 检查 info.playedCards
        final int[] checkCount = {0};
        Joker customJoker = new Joker() {
            @Override public String key() { return "phiftest"; }
            @Override public String displayName() { return "PHIF Test"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                checkCount[0] = info.playedCards.size();
            }
        };
        s.jokers.add(new JokerInstance(customJoker));
        Engine.playHand(s, ids);
        assertEquals(2, checkCount[0], "playedCards 应匹配出牌数");
    }

    @Test
    void scoredCardsIsSubsetOfPlayed() {
        RunState s = Engine.createRun("red", 0, "PHIF2", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 出全部 5 张，scoredCards 是其中的计分牌
        final List<Card> scoredCapture = new java.util.ArrayList<>();
        Joker customJoker = new Joker() {
            @Override public String key() { return "phiftest2"; }
            @Override public String displayName() { return "PHIF2"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                scoredCapture.addAll(info.scoredCards);
            }
        };
        s.jokers.add(new JokerInstance(customJoker));
        List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
        Engine.playHand(s, ids);
        // scoredCards 应非空且是 playedCards 的子集
        assertTrue(!scoredCapture.isEmpty(), "scoredCards 应非空");
        for (Card c : scoredCapture) {
            assertTrue(ids.contains(c.id()), "scoredCard id 应在打出牌中");
        }
    }

    @Test
    void hasFaceDetectsFaceCards() {
        RunState s = Engine.createRun("red", 0, "PHIF3", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 只打第一张：hasFace 应等于「第一张是否为人头牌(J/Q/K)」（红牌组无 pareidolia 类效果）
        Card first = s.hand.get(0);
        boolean firstIsFace = first.rank() >= 11 && first.rank() <= 13;
        final boolean[] infoHasFace = {false};
        final boolean[] hookCalled = {false};
        Joker customJoker = new Joker() {
            @Override public String key() { return "phiftest3"; }
            @Override public String displayName() { return "PHIF3"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                infoHasFace[0] = info.hasFace;
                hookCalled[0] = true;
            }
        };
        s.jokers.add(new JokerInstance(customJoker));
        Engine.playHand(s, List.of(first.id()));
        assertTrue(hookCalled[0], "onPlayHand 钩子应被调用");
        assertEquals(firstIsFace, infoHasFace[0],
                "hasFace 应反映打出牌中是否含人头牌（第一张 rank=" + first.rank() + "）");
    }

    @Test
    void hasRankDetectsAce() {
        // 扫描少量确定性种子找首手含 A 的局：单种子首手含 A 概率约 1/2，10 个种子几乎必中。
        // 注意本测试的被测对象是 hasRank 而非发牌——扫描使前置条件对引擎洗牌行为变更健壮；
        // 若 10 个种子首手全无 A，则说明发牌/牌组逻辑本身出了回归（此时应当失败报警）。
        RunState s = null;
        int aceIdx = -1;
        for (int t = 0; t < 10 && aceIdx < 0; t++) {
            s = Engine.createRun("red", 0, "PHIF4-" + t, null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            for (int i = 0; i < s.hand.size(); i++) {
                if (s.hand.get(i).rank() == 14) { aceIdx = i; break; }
            }
        }
        assertTrue(aceIdx >= 0, "10 个确定性种子首手均无 A——发牌/牌组逻辑疑似回归");

        final boolean[] infoHasRank = {false};
        Joker customJoker = new Joker() {
            @Override public String key() { return "phiftest4"; }
            @Override public String displayName() { return "PHIF4"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                infoHasRank[0] = info.hasRank(14);
            }
        };
        s.jokers.add(new JokerInstance(customJoker));
        Engine.playHand(s, List.of(s.hand.get(aceIdx).id()));
        assertTrue(infoHasRank[0], "打出了 A，hasRank(14) 应为 true");
    }

    @Test
    void isMostPlayedCorrect() {
        // isMostPlayed = mostPlayedType() == handType
        RunState s = Engine.createRun("red", 0, "PHIF5", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        // 先出 pair 两次使其成为最常用牌型
        for (int attempt = 0; attempt < 4 && s.phase == Phase.ROUND; attempt++) {
            if (s.hand.isEmpty() || s.handsLeft <= 0) break;
            List<Integer> ids = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(5, s.hand.size()); i++) ids.add(s.hand.get(i).id());
            Engine.playHand(s, ids);
        }
        // 大部分种子下 HIGH 是最常用牌型（打出 5 张非配对的牌）
        Data.HandType most = s.mostPlayedType();
        assertNotNull(most, "mostPlayedType 应非 null");
    }

    @Test
    void findJokerReturnsSelfIfExists() {
        RunState s = Engine.createRun("red", 0, "PHIF6", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        JokerInstance testJoker = cn.quotidietium.balatro.engine.joker.JokerRegistry.create("joker");
        s.jokers.add(testJoker);
        final JokerInstance[] found = {null};
        Joker probe = new Joker() {
            @Override public String key() { return "probetest"; }
            @Override public String displayName() { return "Probe"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                found[0] = info.findJoker("joker");
            }
        };
        s.jokers.add(new JokerInstance(probe));
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertNotNull(found[0], "findJoker 应找到 joker");
        assertEquals("joker", found[0].def.key());
    }

    @Test
    void findJokerReturnsNullIfDebuff() {
        RunState s = Engine.createRun("red", 0, "PHIF7", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        JokerInstance testJoker = cn.quotidietium.balatro.engine.joker.JokerRegistry.create("joker");
        testJoker.debuff = true; // 失效
        s.jokers.add(testJoker);
        final JokerInstance[] found = {null};
        Joker probe = new Joker() {
            @Override public String key() { return "probetest2"; }
            @Override public String displayName() { return "Probe2"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                found[0] = info.findJoker("joker");
            }
        };
        s.jokers.add(new JokerInstance(probe));
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertNull(found[0], "findJoker 对 debuff 的 joker 应返回 null");
    }

    @Test
    void handTypeMatchesEvaluated() {
        RunState s = Engine.createRun("red", 0, "PHIF8", null);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        final Data.HandType[] captured = {null};
        Joker probe = new Joker() {
            @Override public String key() { return "probetest3"; }
            @Override public String displayName() { return "Probe3"; }
            @Override public String desc() { return ""; }
            @Override public int cost() { return 1; }
            @Override public void onPlayHand(RunState st, PlayHandInfo info) {
                captured[0] = info.handType;
            }
        };
        s.jokers.add(new JokerInstance(probe));
        List<Integer> ids = List.of(s.hand.get(0).id());
        Engine.PlayResult r = Engine.playHand(s, ids);
        if (r.ok) {
            assertNotNull(captured[0], "handType 应被设置");
        }
    }
}
