package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R213：24 标签 × 20 挑战交叉 × 新种子——每张标签在轮换挑战的新种子局
 *（TXC-*）中 gainTag 走全效果路径（R207 同款开包脱离 + pending 计数 + double
 * 翻倍复验）：断言不崩 + 守恒（差=大理石+包选牌）+ 金钱下界。
 * bannedTags 为池过滤器（跳过抽取时跳过，R210 同构语义）——不拦截直接 gainTag。
 * 新种子第十五维（标签×挑战交叉）。
 */
class TagXChallengeFreshTest {

    @Test
    void allTagsAcrossChallengesApplyCleanly() {
        var challenges = Data.CHALLENGES;
        int idx = 0;
        for (Data.Tag tag : Data.TAGS) {
            Data.Challenge ch = challenges.get(idx % challenges.size());
            RunState s = Engine.createRun("red", idx % 2, "TXC-" + (idx++) + "-" + tag.key() + "-" + ch.key(),
                    ch.key());
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            long moneyBefore = s.money;
            Engine.gainTag(s, tag.key());
            assertTrue(s.tags.contains(tag.key()), "标签入账（" + tag.key() + "@" + ch.key() + "）");

            int pending = 0;
            int guard = 0;
            while (s.phase == Phase.PACK && guard++ < 12) {
                boolean took = false;
                for (int i = 0; i < s.pack.cards.size(); i++) {
                    boolean isPlaying = "playing".equals(s.pack.cards.get(i).kind);
                    if (cn.quotidietium.balatro.engine.shop.Packs.pick(s, i)) {
                        if (isPlaying) pending++;
                        took = true;
                        break;
                    }
                }
                if (!took) cn.quotidietium.balatro.engine.shop.Packs.skip(s);
            }
            assertTrue(s.phase != Phase.PACK, "开包标签应可脱离（" + tag.key() + "@" + ch.key() + "）");

            if (tag.key().equals("double")) {
                int tagsBefore = s.tags.size();
                Engine.gainTag(s, "handy");
                assertTrue(s.tags.size() >= tagsBefore + 1, "翻倍后入账（double→handy@" + ch.key() + "）");
            }

            Map<Integer, Integer> piles = new HashMap<>();
            for (Card c : s.hand) piles.merge(c.id(), 1, Integer::sum);
            for (Card c : s.drawPile) piles.merge(c.id(), 1, Integer::sum);
            for (Card c : s.discardPile) piles.merge(c.id(), 1, Integer::sum);
            Map<Integer, Integer> deck = new HashMap<>();
            for (Card c : s.fullDeck) deck.merge(c.id(), 1, Integer::sum);
            int marbles = 0;
            for (var j : s.jokers) if (j.def.key().equals("marble")) marbles++;
            int diff = 0;
            for (var e : deck.entrySet()) diff += e.getValue() - piles.getOrDefault(e.getKey(), 0);
            for (var e : piles.entrySet()) diff += e.getValue() - deck.getOrDefault(e.getKey(), 0);
            assertTrue(diff == marbles + pending,
                    "守恒差=大理石+包选牌（" + tag.key() + "@" + ch.key() + "）：" + diff);
            assertTrue(s.money >= Math.min(0, moneyBefore),
                    "金钱不因标签破 0（" + tag.key() + "@" + ch.key() + "）：" + s.money);
        }
    }
}
