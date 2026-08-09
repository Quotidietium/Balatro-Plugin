package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.PlayHandInfo;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.ScoreContext;
import java.util.List;
import java.util.Map;

/**
 * 0.1.0 基础小丑（15 个，移植自 {@code jokers.js} 的普通档）。
 * 用枚举的常量特化方法（constant-specific implementation）覆写各自钩子，覆盖：
 * onScore(addMult/addChips/handIs/rngInt/playedCards/discardsLeft)、onScoreCard(isSuit)、heldCards 等。
 *
 * <p>其余 135+ 小丑随 0.4.0 补齐。
 */
public enum BasicJoker implements Joker {
    JOKER("joker", "小丑", "+4 倍率", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(4);
        }
    },
    GREEDY("greedy", "贪婪小丑", "每张计分的方块牌 +3 倍率", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 3)) ctx.addMult(3);
        }
    },
    LUSTY("lusty", "好色小丑", "每张计分的红桃牌 +3 倍率", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 1)) ctx.addMult(3);
        }
    },
    WRATHFUL("wrathful", "愤怒小丑", "每张计分的黑桃牌 +3 倍率", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 0)) ctx.addMult(3);
        }
    },
    GLUTTONOUS("gluttonous", "暴食小丑", "每张计分的梅花牌 +3 倍率", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 2)) ctx.addMult(3);
        }
    },
    JOLLY("jolly", "快乐小丑", "若牌型为对子：+8 倍率", 3) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.handIs("pair")) ctx.addMult(8);
        }
    },
    ZANY("zany", "滑稽小丑", "若牌型为三条：+12 倍率", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.handIs("three")) ctx.addMult(12);
        }
    },
    SLY("sly", "狡猾小丑", "若牌型为对子：+50 筹码", 3) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.handIs("pair")) ctx.addChips(50);
        }
    },
    WILY("wily", "诡计小丑", "若牌型为三条：+100 筹码", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.handIs("three")) ctx.addChips(100);
        }
    },
    HALF("half", "半个小丑", "若出牌不超过 3 张：+20 倍率", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.playedCards.size() <= 3) ctx.addMult(20);
        }
    },
    BANNER("banner", "旗帜", "每张剩余的弃牌次数 +40 筹码", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(40L * ctx.state.discardsLeft);
        }
    },
    SUMMIT("summit", "神秘峰顶", "若弃牌次数为 0：+15 倍率", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.state.discardsLeft == 0) ctx.addMult(15);
        }
    },
    MISPRINT("misprint", "错印", "随机 +0 ~ +23 倍率", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(ctx.rngInt(0, 23));
        }
    },
    RAISEDFIST("raisedfist", "举拳", "手中最小点数牌的点数 ×2 加入倍率", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            Integer min = null;
            for (Card c : ctx.heldCards) {
                if (c.isStone() || c.debuff()) continue;
                if (min == null || c.rank() < min) min = c.rank();
            }
            if (min != null) ctx.addMult(min * 2);
        }
    },
    CRAFTY("crafty", "灵巧小丑", "若牌型为同花：+80 筹码", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.handIs("flush")) ctx.addChips(80);
        }
    },
    FIBONACCI("fibonacci", "斐波那契", "每张计分的 A/2/3/5/8 +8 倍率", 8) {
        private final List<Integer> ranks = List.of(14, 2, 3, 5, 8);
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ranks.contains(card.rank())) ctx.addMult(8);
        }
    },
    SCARYFACE("scaryface", "吓人面孔", "每张计分的人头牌 +30 筹码", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isFace(card)) ctx.addChips(30);
        }
    },
    ABSTRACT("abstract", "抽象小丑", "每张小丑牌 +3 倍率", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(3L * ctx.state.jokers.size());
        }
    },
    DELAYED("delayed", "延迟满足", "回合结束时每张剩余弃牌次数 +$2（本回合未弃牌才生效）", 4) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            return state.usedDiscardThisRound ? 0 : 2L * state.discardsLeft;
        }
    },
    GROSSMICHEL("grossmichel", "格罗米歇尔", "+15 倍率；每回合结束有 1/6 概率自毁", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(15);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            if (state.stream("grossmichel").chance(1.0 / 6)) state.destroyJoker(self, "格罗米歇尔碎掉了！");
            return 0;
        }
    },
    EVENSTEVEN("evensteven", "偶数史蒂夫", "每张计分的偶数牌 +4 倍率", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (card.rank() <= 10 && card.rank() % 2 == 0) ctx.addMult(4);
        }
    },
    ODDTODD("oddtodd", "奇数托德", "每张计分的 A/3/5/7/9 +30 筹码", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            int r = card.rank();
            if (r == 14 || (r <= 9 && r % 2 == 1)) ctx.addChips(30);
        }
    },
    SCHOLAR("scholar", "学者", "每张计分的 A：+20 筹码、+4 倍率", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (card.rank() == 14) { ctx.addChips(20); ctx.addMult(4); }
        }
    },
    BUSINESS("business", "名片", "每张计分的人头牌有 1/2 概率 +$2", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isFace(card) && ctx.prob(0.5)) ctx.dollars(2);
        }
    },
    SUPERNOVA("supernova", "超新星", "本回合每出过一次该牌型 +1 倍率", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(ctx.state.handPlayedCount.getOrDefault(ctx.handType, 0));
        }
    },
    RIDEBUS("ridebus", "搭便车", "连续打出无人头牌的手牌：倍率 +1（累积）；含人头牌则重置", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 0));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("ridebus");
            if (j == null) return;
            if (info.hasFace) { j.extra.put("mult", 0); return; }
            int m = gi(j.extra, "mult", 0) + 1;
            j.extra.put("mult", m);
            state.msg("搭便车：倍率累积至 +" + m);
        }
    },
    ICECREAM("icecream", "冰淇淋", "+100 筹码；每次出牌 -5 筹码", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(gi(ctx.joker.extra, "chips", 100));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("icecream");
            if (j == null) return;
            int c = gi(j.extra, "chips", 100) - 5;
            j.extra.put("chips", c);
            if (c <= 0) state.destroyJoker(j, "冰淇淋融化了！");
        }
    },
    SPLASH("splash", "水花", "所有打出的牌都参与计分", 3) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("splash", true);
        }
    },
    BLUE_JOKER("blue", "蓝色小丑", "牌堆中每剩余一张牌 +2 筹码", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(2L * ctx.state.drawPile.size());
        }
    },
    RUNNER("runner", "跑者", "每次打出顺子：永久 +15 筹码", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(gi(ctx.joker.extra, "chips", 0));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.handType != Data.HandType.STRAIGHT) return;
            JokerInstance j = info.findJoker("runner");
            if (j == null) return;
            int c = gi(j.extra, "chips", 0) + 15;
            j.extra.put("chips", c);
            state.msg("跑者：筹码累积至 +" + c);
        }
    },
    GREEN_JOKER("green", "绿色小丑", "每次出牌 +1 倍率；每次弃牌 -1 倍率（累积，最低为 0）", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 0));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("green");
            if (j != null) j.extra.put("mult", gi(j.extra, "mult", 0) + 1);
        }
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            self.extra.put("mult", Math.max(0, gi(self.extra, "mult", 0) - 1));
        }
    },
    TODO_JOKER("todo", "待办清单", "打出指定牌型 +$4（牌型每回合结束时更换）", 4) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("todo");
            if (j == null) return;
            Object h = j.extra.get("hand");
            Data.HandType target = h instanceof Data.HandType ? (Data.HandType) h : Data.HandType.PAIR;
            if (info.handType == target) state.gainMoney(4);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            self.extra.put("hand", state.stream("todo").pick(List.of(Data.HandType.values())));
            return 0;
        }
    },
    CAVENDISH("cavendish", "卡文迪什", "×3 倍率；每回合结束有 1/1000 概率自毁", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(3);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            if (state.stream("cavendish").chance(1.0 / 1000)) state.destroyJoker(self, "卡文迪什碎掉了！");
            return 0;
        }
    },
    SQUARE("square", "方形小丑", "每次恰好打出 4 张牌：永久 +4 筹码", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(gi(ctx.joker.extra, "chips", 0));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.playedCards.size() != 4) return;
            JokerInstance j = info.findJoker("square");
            if (j == null) return;
            int c = gi(j.extra, "chips", 0) + 4;
            j.extra.put("chips", c);
            state.msg("方形小丑：筹码累积至 +" + c);
        }
    },
    FACELESS("faceless", "无面小丑", "若一次弃掉 3 张以上人头牌：+$5", 4) {
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            int faces = 0;
            for (Card c : cards) if (state.isFace(c)) faces++;
            if (faces >= 3) state.gainMoney(5);
        }
    },
    GOLDEN_JOKER("golden", "黄金小丑", "回合结束时 +$4", 6) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            return 4;
        }
    },
    BULL("bull", "公牛", "每持有 $1：+2 筹码", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(2L * Math.max(0, ctx.state.money));
        }
    },
    POPCORN("popcorn", "爆米花", "+20 倍率；每回合结束 -4 倍率", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 20));
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            int m = gi(self.extra, "mult", 20) - 4;
            self.extra.put("mult", m);
            if (m <= 0) state.destroyJoker(self, "爆米花吃完了！");
            return 0;
        }
    },
    WALKIE("walkie", "对讲机", "每张计分的 10 或 4：+10 筹码、+4 倍率", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (card.rank() == 10 || card.rank() == 4) { ctx.addChips(10); ctx.addMult(4); }
        }
    },
    SMILEY("smiley", "笑脸", "每张计分的人头牌 +4 倍率", 4) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isFace(card)) ctx.addMult(4);
        }
    },
    JUGGLER("juggler", "杂耍者", "手牌上限 +1", 4) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("handSize", 1);
        }
    },
    DRUNKARD("drunkard", "酒鬼", "弃牌次数 +1", 4) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("discards", 1);
        }
    },
    CHAOS("chaos", "混沌小丑", "每次商店提供 1 次免费重掷", 4) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("freeRerolls", 1);
        }
    },
    TICKET("ticket", "黄金门票", "每张计分的黄金牌 +$4", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (card.enh() == Data.Enhancement.GOLD) ctx.dollars(4);
        }
    },
    SWASHBUCKLER("swashbuckler", "剑客", "其他小丑的售价总和加入倍率", 4) {
        @Override
        public void onScore(ScoreContext ctx) {
            int sum = 0;
            for (JokerInstance j : ctx.state.jokers) {
                if (j != ctx.joker) sum += ctx.state.sellValue(j);
            }
            ctx.addMult(sum);
        }
    },
    CHAD("chad", "悬吊乍得", "重新触发第一张计分牌 2 次", 5) {
        @Override
        public int retrigger(Card card, ScoreContext ctx) {
            return ctx.scoreIndex == 0 ? 2 : 0;
        }
    },
    MOON("moon", "射月", "手中每张 Q +13 倍率", 5) {
        @Override
        public void onHeld(ScoreContext ctx, Card card) {
            if (card.rank() == 12) ctx.addMult(13);
        }
    },
    STUNTMAN("stuntman", "特技演员", "+250 筹码；手牌上限 -2", 7) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("handSize", -2);
        }
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(250);
        }
    },
    SEEINGDOUBLE("seeingdouble", "重影", "若出牌含梅花与另一种花色：×2 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            boolean club = false, other = false;
            for (Card c : ctx.playedCards) {
                if (c.isStone()) continue;
                if (ctx.isSuit(c, 2)) club = true;
                else other = true;
            }
            if (club && other) ctx.xMult(2);
        }
    },
    STENCIL("stencil", "模板小丑", "每个空小丑槽 ×1 倍率", 8) {
        @Override
        public void onScore(ScoreContext ctx) {
            int empty = ctx.state.jokerSlots - ctx.state.jokers.size();
            if (empty > 0) ctx.xMult(empty);
        }
    },
    FOURFINGERS("fourfingers", "四指", "顺子与同花只需 4 张牌即可组成", 7) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("fourFingers", true);
        }
    },
    MIME("mime", "哑剧", "手中牌的「持有」效果重新触发一次", 5) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("mimeRetrigger", true);
        }
    },
    DAGGER("dagger", "仪式匕首", "选择盲注时销毁右侧小丑，永久获得其售价 ×2 的倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 0));
        }
        @Override
        public void onBlindSelect(RunState state, JokerInstance self, Data.BlindType blindType) {
            int idx = state.jokers.indexOf(self);
            if (idx < 0 || idx >= state.jokers.size() - 1) return;
            JokerInstance victim = state.jokers.get(idx + 1);
            if (victim.eternal) return;
            int add = 2 * state.sellValue(victim);
            self.extra.put("mult", gi(self.extra, "mult", 0) + add);
            state.destroyJoker(victim, "仪式匕首吞掉了 " + victim.def.displayName());
        }
    },
    LOYALTY("loyalty", "忠诚卡", "每打出第 6 手牌：×4 倍率", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            int c = gi(ctx.joker.extra, "count", 0) + 1;
            ctx.joker.extra.put("count", c);
            if (c >= 6) { ctx.joker.extra.put("count", 0); ctx.xMult(4); }
        }
    },
    DUSK("dusk", "黄昏", "回合最后一次出牌：重新触发所有计分牌", 5) {
        @Override
        public int retrigger(Card card, ScoreContext ctx) {
            return ctx.state.handsLeft == 0 ? 1 : 0;
        }
    },
    HACK("hack", "黑客", "重新触发每张计分的 2/3/4/5", 6) {
        @Override
        public int retrigger(Card card, ScoreContext ctx) {
            return (card.rank() >= 2 && card.rank() <= 5) ? 1 : 0;
        }
    },
    PAREIDOLIA("pareidolia", "空想性错觉", "所有牌都视为人头牌", 5) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("allFace", true);
        }
    },
    STEEL_JOKER("steel", "钢铁小丑", "牌组中每张钢铁牌 ×0.2 倍率", 7) {
        @Override
        public void onScore(ScoreContext ctx) {
            int n = 0;
            for (Card c : ctx.state.fullDeck) if (c.enh() == Data.Enhancement.STEEL) n++;
            if (n > 0) ctx.xMult(1 + 0.2 * n);
        }
    },
    SPACE("space", "太空小丑", "每次出牌有 1/4 概率升级所出牌型", 5) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.findJoker("space") != null && state.stream("space").chance(0.25)) {
                state.levelUpHand(info.handType, 1);
                state.msg("太空小丑：「" + info.handType.name + "」升 1 级");
            }
        }
    },
    BURGLAR("burglar", "窃贼", "选择盲注时：出牌次数 +3、弃牌次数清零", 6) {
        @Override
        public void onBlindSelect(RunState state, JokerInstance self, Data.BlindType blindType) {
            state.handsLeft += 3;
            state.discardsLeft = 0;
            state.msg("窃贼：出牌次数 +3，弃牌次数清零");
        }
    };

    private final String key;
    private final String name;
    private final String desc;
    private final int cost;

    BasicJoker(String key, String name, String desc, int cost) {
        this.key = key;
        this.name = name;
        this.desc = desc;
        this.cost = cost;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String displayName() {
        return name;
    }

    @Override
    public String desc() {
        return desc;
    }

    @Override
    public int cost() {
        return cost;
    }

    /** 从小丑 extra 中读整数（缺失用默认值）。 */
    private static int gi(Map<String, Object> extra, String key, int def) {
        Object v = extra.get(key);
        return v instanceof Number ? ((Number) v).intValue() : def;
    }
}
