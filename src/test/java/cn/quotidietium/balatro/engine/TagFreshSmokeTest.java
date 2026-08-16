package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R207：24 标签 × **新种子族**逐张 smoke——TagGoldenTest（固定种子 24 例）之后，
 * 以全新种子族（TFS-*）逐张 gainTag 走全效果路径（含 double 翻倍二次应用、
 * 立即开包标签的 PACK 进入与脱离）：断言不崩 + 金钱下界 + 卡守恒（差=大理石）
 * + 标签入账。新种子探索第九维（标签逐张）。
 */
class TagFreshSmokeTest {

    @Test
    void allTagsOnFreshSeedsApplyCleanly() {
        int idx = 0;
        for (Data.Tag tag : Data.TAGS) {
            RunState s = Engine.createRun("red", idx % 2, "TFS-" + (idx++) + "-" + tag.key(), null);
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
            long moneyBefore = s.money;
            Engine.gainTag(s, tag.key());
            assertTrue(s.tags.contains(tag.key()), "标签入账（" + tag.key() + "）");
            // 立即开包标签会把引擎送进 PACK——逐张选完并 skip 脱离；playing 牌入组未发（pending 源①）
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
            assertTrue(s.phase != Phase.PACK, "开包标签应可脱离 PACK（" + tag.key() + "）");
            // double 标签：翻倍下一次非 double 标签（再喂一张验证一次）
            if (tag.key().equals("double")) {
                int tagsBefore = s.tags.size();
                Engine.gainTag(s, "handy");
                assertTrue(s.tags.size() >= tagsBefore + 1, "翻倍后标签入账（double→handy）");
            }
            // 不变量：守恒（差=大理石）+ 金钱下界
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
            assertTrue(diff == marbles + pending, "守恒差=大理石+包选牌（" + tag.key() + "）：" + diff + " vs " + (marbles + pending));
            assertTrue(s.money >= Math.min(0, moneyBefore), "金钱不因标签下跌破 0（" + tag.key() + "）：" + s.money);
        }
    }
}
