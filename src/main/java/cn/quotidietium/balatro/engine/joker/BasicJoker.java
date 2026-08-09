package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.HandEval;
import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
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
    },
    BLACKBOARD("blackboard", "黑板", "若手中没有红桃/方块：×3 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            for (Card c : ctx.heldCards) {
                if (c.enh() == Data.Enhancement.STONE) continue;
                if (c.enh() == Data.Enhancement.WILD) continue;
                if (c.suit() == 1 || c.suit() == 3) return;
            }
            ctx.xMult(3);
        }
    },
    DNA("dna", "DNA", "回合第一次出牌仅 1 张时：复制该牌加入手中", 8) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.findJoker("dna") == null) return;
            if (state.handsPlayedThisRound == 1 && info.playedCards.size() == 1) {
                Card src = info.playedCards.get(0);
                state.hand.add(state.cloneCard(src));
                state.msg("DNA：复制了 " + state.cardName(src));
            }
        }
    },
    CONSTELLATION("constellation", "星座", "每使用一张星球牌：×0.1 倍率（累积）", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
    },
    HIKER("hiker", "徒步者", "每张计分牌永久 +5 筹码", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            card.addChipBonus(5);
        }
    },
    CARDSHARP("cardsharp", "老千", "若本回合已出过该牌型：×3 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.state.handPlayedCount.getOrDefault(ctx.handType, 0) > 1) ctx.xMult(3);
        }
    },
    MADNESS("madness", "癫狂", "选择大小盲注时：销毁一张随机小丑，×0.5 倍率（累积）", 7) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onBlindSelect(RunState state, JokerInstance self, Data.BlindType blindType) {
            if (blindType == Data.BlindType.BOSS) return;
            List<JokerInstance> others = new java.util.ArrayList<>();
            for (JokerInstance x : state.jokers) if (x != self && !x.eternal) others.add(x);
            if (!others.isEmpty()) {
                JokerInstance victim = state.stream("madness").pick(others);
                state.destroyJoker(victim, "癫狂销毁了 " + victim.def.displayName());
                self.extra.put("x", gd(self.extra, "x") + 0.5);
            }
        }
    },
    SEANCE("seance", "降灵会", "若打出皇家同花顺：获得一张随机幻灵牌", 6) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.handType == Data.HandType.ROYAL && info.findJoker("seance") != null) {
                state.gainConsumable("spectral");
            }
        }
    },
    VAMPIRE("vampire", "吸血鬼", "每张计分的增强牌被移除增强：×0.1 倍率（累积）", 7) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("vampire");
            if (j == null) return;
            for (Card c : info.scoredCards) {
                if (c.enh() != null && !c.debuff()) {
                    c.setEnh(null);
                    j.extra.put("x", gd(j.extra, "x") + 0.1);
                    state.msg("吸血鬼：移除了增强，倍率累积");
                }
            }
        }
    },
    SHORTCUT("shortcut", "捷径", "顺子允许间隔 1 点（如 2 4 6 8 10）", 7) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("shortcut", true);
        }
    },
    HOLOGRAM("hologram", "全息影像", "每有一张游戏牌加入牌组：×0.25 倍率（累积）", 7) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
    },
    VAGABOND("vagabond", "流浪者", "若出牌时资金 ≤ $4：获得一张塔罗牌", 8) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.findJoker("vagabond") != null && state.money <= 4) state.gainConsumable("tarot");
        }
    },
    BARON("baron", "男爵", "手中每张 K ×1.5 倍率", 8) {
        @Override
        public void onHeld(ScoreContext ctx, Card card) {
            if (card.rank() == 13) ctx.xMult(1.5);
        }
    },
    CLOUD9("cloud9", "九霄云外", "回合结束时牌组中每张 9 +$1", 7) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            int n = 0;
            for (Card c : state.fullDeck) if (c.rank() == 9) n++;
            return n;
        }
    },
    ROCKET("rocket", "火箭", "回合结束 +$1；每击败一个 Boss 盲注 +$2（累积）", 6) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            return gi(self.extra, "pay", 1);
        }
        @Override
        public void onBossDefeated(RunState state, JokerInstance self) {
            self.extra.put("pay", gi(self.extra, "pay", 1) + 2);
        }
    },
    OBELISK("obelisk", "方尖碑", "连续打出非最常用牌型：×0.2 倍率（累积）；打出最常用牌型则重置", 8) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("obelisk");
            if (j == null) return;
            if (info.isMostPlayed) { j.extra.put("x", 0.0); state.msg("方尖碑：重置"); }
            else j.extra.put("x", gd(j.extra, "x") + 0.2);
        }
    },
    MIDAS("midas", "迈达斯面具", "每张计分的人头牌变为黄金牌", 7) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isFace(card) && !card.debuff()) card.setEnh(Data.Enhancement.GOLD);
        }
    },
    SIXTHSENSE("sixthsense", "第六感", "回合第一次出牌为单张 6 时：销毁它并获得一张幻灵牌", 6) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.findJoker("sixthsense") == null) return;
            if (state.handsPlayedThisRound == 1 && info.playedCards.size() == 1 && info.playedCards.get(0).rank() == 6) {
                state.removeCardFromDeck(info.playedCards.get(0));
                state.gainConsumable("spectral");
                state.msg("第六感：销毁了 6，获得一张幻灵牌");
            }
        }
    },
    PHOTOGRAPH("photograph", "照片", "第一张计分的人头牌 ×2 倍率", 5) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isFace(card) && !ctx.photoUsed) { ctx.photoUsed = true; ctx.xMult(2); }
        }
    },
    GIFTCARD("giftcard", "礼品卡", "回合结束时每张小丑与消耗品售价 +$1", 6) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            for (JokerInstance j : state.jokers) j.sellBonus += 1;
            return 0;
        }
    },
    TURTLE("turtle", "海龟豆", "手牌上限 +3；每回合结束 -1", 6) {
        @Override
        public Map<String, Object> flagsFn(RunState state, JokerInstance self) {
            return Map.of("handSize", gi(self.extra, "size", 3));
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            int s = gi(self.extra, "size", 3) - 1;
            self.extra.put("size", s);
            if (s <= 0) state.destroyJoker(self, "海龟豆吃完了！");
            return 0;
        }
    },
    EROSION("erosion", "侵蚀", "牌组每比 52 张少一张牌：+4 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(4L * Math.max(0, 52 - ctx.state.fullDeck.size()));
        }
    },
    PARKING("parking", "预留车位", "手中每张人头牌有 1/2 概率 +$1", 5) {
        @Override
        public void onHeld(ScoreContext ctx, Card card) {
            if (ctx.isFace(card) && ctx.prob(0.5)) ctx.dollars(1);
        }
    },
    MAILIN("mailin", "邮寄返利", "弃掉指定点数的牌每张 +$5（点数每回合结束更换）", 4) {
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            int target = gi(self.extra, "rank", 14);
            int n = 0;
            for (Card c : cards) if (c.rank() == target) n++;
            if (n > 0) state.gainMoney(5L * n);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            self.extra.put("rank", state.stream("mailin").range(2, 14));
            return 0;
        }
    },
    TOTHEMOON("tothemoon", "奔月", "回合结束时每张剩余出牌次数 +$1", 5) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            return state.handsLeft;
        }
    },
    FORTUNE("fortune", "算命先生", "每使用一张塔罗牌：+1 倍率（累积）", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 0));
        }
        @Override
        public void onUseTarot(RunState state, JokerInstance self) {
            self.extra.put("mult", gi(self.extra, "mult", 0) + 1);
        }
    },
    STONE_JOKER("stone", "石头小丑", "牌组中每张石头牌 +25 筹码", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            int n = 0;
            for (Card c : ctx.state.fullDeck) if (c.enh() == Data.Enhancement.STONE) n++;
            ctx.addChips(25L * n);
        }
    },
    LUCKYCAT("luckycat", "招财猫", "每张幸运牌触发成功：×0.25 倍率（累积）", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onLucky(RunState state, JokerInstance self) {
            self.extra.put("x", gd(self.extra, "x") + 0.25);
        }
    },
    TRADING("trading", "交易卡", "回合第一次弃牌仅 1 张时：销毁它并 +$3", 6) {
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            if (state.discardsUsedThisRound == 1 && cards.size() == 1) {
                state.removeCardFromDeck(cards.get(0));
                state.gainMoney(3);
                state.msg("交易卡：销毁了 " + state.cardName(cards.get(0)) + "，+$3");
            }
        }
    },
    FLASH("flash", "闪存卡", "每次商店重掷：+2 倍率（累积）", 5) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 0));
        }
        @Override
        public void onReroll(RunState state, JokerInstance self) {
            self.extra.put("mult", gi(self.extra, "mult", 0) + 2);
        }
    },
    TROUSERS("trousers", "备用长裤", "每次打出两对：永久 +2 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(gi(ctx.joker.extra, "mult", 0));
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.handType != Data.HandType.TWOPAIR) return;
            JokerInstance j = info.findJoker("trousers");
            if (j == null) return;
            int m = gi(j.extra, "mult", 0) + 2;
            j.extra.put("mult", m);
            state.msg("备用长裤：倍率累积至 +" + m);
        }
    },
    ANCIENT("ancient", "远古小丑", "每张指定花色的计分牌 ×1.5 倍率（花色每回合结束更换）", 8) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            int suit = gi(ctx.joker.extra, "suit", 1);
            if (ctx.isSuit(card, suit)) ctx.xMult(1.5);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            self.extra.put("suit", state.stream("ancient").range(0, 3));
            return 0;
        }
    },
    RAMEN("ramen", "拉面", "×2 倍率；每弃一张牌 -0.01 倍率", 6) {
        private double x(JokerInstance j) {
            Object v = j.extra.get("x");
            return v instanceof Number ? ((Number) v).doubleValue() : 2.0;
        }
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(x(ctx.joker));
        }
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            double nx = x(self) - 0.01 * cards.size();
            self.extra.put("x", nx);
            if (nx <= 1) state.destroyJoker(self, "拉面吃完了！");
        }
    },
    SELTZER("seltzer", "苏打水", "接下来 10 次出牌重新触发所有计分牌，之后自毁", 6) {
        @Override
        public int retrigger(Card card, ScoreContext ctx) {
            return 1;
        }
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            JokerInstance j = info.findJoker("seltzer");
            if (j == null) return;
            int u = gi(j.extra, "uses", 10) - 1;
            j.extra.put("uses", u);
            if (u <= 0) state.destroyJoker(j, "苏打水喝完了！");
        }
    },
    CASTLE("castle", "城堡", "每弃一张指定花色的牌：永久 +3 筹码（花色每回合更换）", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(gi(ctx.joker.extra, "chips", 0));
        }
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            int suit = gi(self.extra, "suit", 0);
            for (Card c : cards) if (state.isSuit(c, suit)) self.extra.put("chips", gi(self.extra, "chips", 0) + 3);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            self.extra.put("suit", state.stream("castle").range(0, 3));
            return 0;
        }
    },
    ACROBAT("acrobat", "杂技演员", "回合最后一次出牌：×3 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            if (ctx.state.handsLeft == 0) ctx.xMult(3);
        }
    },
    SOCK("sock", "袜子与布偶", "重新触发所有计分的人头牌", 6) {
        @Override
        public int retrigger(Card card, ScoreContext ctx) {
            return ctx.isFace(card) ? 1 : 0;
        }
    },
    TROUBADOUR("troubadour", "吟游诗人", "手牌上限 +2；出牌次数 -1", 6) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("handSize", 2, "hands", -1);
        }
    },
    LUCHADOR("luchador", "摔跤手", "出售此牌：消除当前 Boss 盲注效果", 5) {
        @Override
        public void onSell(RunState state, JokerInstance self) {
            if (state.phase == Phase.ROUND && state.blindType == Data.BlindType.BOSS) {
                state.disableBoss();
                state.msg("摔跤手：Boss 盲注效果已消除");
            }
        }
    },
    COLA("cola", "健怡可乐", "出售此牌：获得一个「翻倍标签」", 6) {
        @Override
        public void onSell(RunState state, JokerInstance self) {
            state.gainTag("double");
        }
    },
    CAMPFIRE("campfire", "篝火", "每卖出一张牌：×0.5 倍率（累积）；击败 Boss 盲注后重置", 9) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onAnySell(RunState state, JokerInstance self) {
            self.extra.put("x", gd(self.extra, "x") + 0.5);
        }
        @Override
        public void onBossDefeated(RunState state, JokerInstance self) {
            self.extra.put("x", 0.0);
        }
    },
    SMEARED("smeared", "污渍小丑", "红桃与方块视为同一花色；黑桃与梅花视为同一花色", 7) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("smeared", true);
        }
    },
    THROWBACK("throwback", "复古", "每跳过一个盲注：×0.25 倍率（累积）", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onSkip(RunState state, JokerInstance self) {
            self.extra.put("x", gd(self.extra, "x") + 0.25);
        }
    },
    GEM("gem", "粗宝石", "每张计分的方块牌 +$1", 7) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 3)) ctx.dollars(1);
        }
    },
    BLOODSTONE("bloodstone", "血石", "每张计分的红桃牌有 1/2 概率 ×1.5 倍率", 7) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 1) && ctx.prob(0.5)) ctx.xMult(1.5);
        }
    },
    ARROWHEAD("arrowhead", "箭头", "每张计分的黑桃牌 +50 筹码", 7) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 0)) ctx.addChips(50);
        }
    },
    ONYX("onyx", "玛瑙", "每张计分的梅花牌 +7 倍率", 7) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (ctx.isSuit(card, 2)) ctx.addMult(7);
        }
    },
    GLASS_JOKER("glass", "玻璃小丑", "每张玻璃牌破碎：×0.75 倍率（累积）", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.xMult(1 + gd(ctx.joker.extra, "x"));
        }
        @Override
        public void onGlassBreak(RunState state, JokerInstance self) {
            self.extra.put("x", gd(self.extra, "x") + 0.75);
        }
    },
    SHOWMAN("showman", "演艺家", "商店/补充包中可以出现已拥有的卡牌", 5) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("allowDupes", true);
        }
    },
    FLOWERPOT("flowerpot", "花盆", "若出牌含全部四种花色：×3 倍率", 6) {
        @Override
        public void onScore(ScoreContext ctx) {
            boolean[] seen = new boolean[4];
            for (Card c : ctx.playedCards) {
                if (c.isStone()) continue;
                if (c.enh() == Data.Enhancement.WILD) { seen[0] = seen[1] = seen[2] = seen[3] = true; break; }
                seen[c.suit()] = true;
            }
            if (seen[0] && seen[1] && seen[2] && seen[3]) ctx.xMult(3);
        }
    },
    WEE("wee", "小不点", "+10 筹码；每张计分的 2 使其永久 +8 筹码", 8) {
        private long chips(JokerInstance j) {
            Object v = j.extra.get("chips");
            return v instanceof Number ? ((Number) v).longValue() : 10;
        }
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addChips(chips(ctx.joker));
        }
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            if (card.rank() == 2) ctx.joker.extra.put("chips", chips(ctx.joker) + 8);
        }
    },
    MERRY("merry", "快乐安迪", "弃牌次数 +3；手牌上限 -1", 7) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("discards", 3, "handSize", -1);
        }
    },
    OOPS("oops", "全是 6", "所有概率翻倍（如 1/4 → 1/2）", 4) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("doubleProb", true);
        }
    },
    SATELLITE("satellite", "卫星", "回合结束：本局每用过一种不同的星球牌 +$1", 6) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            int n = 0;
            for (Object v : state.usedPlanets.values()) if (Boolean.TRUE.equals(v)) n++;
            return n;
        }
    },
    LICENSE("license", "驾照", "若牌组中增强牌 ≥16 张：×3 倍率", 7) {
        @Override
        public void onScore(ScoreContext ctx) {
            int n = 0;
            for (Card c : ctx.state.fullDeck) if (c.enh() != null) n++;
            if (n >= 16) ctx.xMult(3);
        }
    },
    CARTOMANCER("cartomancer", "卡牌术士", "击败盲注后获得一张塔罗牌", 6) {
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            state.gainConsumable("tarot");
            return 0;
        }
    },
    ASTRONOMER("astronomer", "天文学家", "商店与天体包中的星球牌免费", 8) {
        @Override
        public Map<String, Object> flags() {
            return Map.of("freePlanets", true);
        }
    },
    BURNT("burnt", "烧焦小丑", "每次弃牌后升级所弃牌构成的牌型", 6) {
        @Override
        public void onDiscard(RunState state, List<Card> cards, JokerInstance self) {
            HandEval.Result res = state.evaluateHand(cards);
            if (res != null) {
                state.levelUpHand(res.type, 1);
                state.msg("烧焦小丑：「" + res.type.name + "」升 1 级");
            }
        }
    },
    BOOTSTRAPS("bootstraps", "自力更生", "每持有 $5：+2 倍率", 7) {
        @Override
        public void onScore(ScoreContext ctx) {
            ctx.addMult(2L * (Math.max(0, ctx.state.money) / 5));
        }
    },
    MATADOR("matador", "斗牛士", "若出牌触发了 Boss 盲注的能力：+$8", 7) {
        @Override
        public void onPlayHand(RunState state, PlayHandInfo info) {
            if (info.findJoker("matador") != null && state.bossTriggeredThisHand) state.gainMoney(8);
        }
    },
    IDOL("idol", "偶像", "每张指定的牌（点数+花色）计分时 ×2 倍率（目标每回合结束更换）", 6) {
        @Override
        public void onScoreCard(ScoreContext ctx, Card card) {
            int rank = gi(ctx.joker.extra, "rank", 14);
            int suit = gi(ctx.joker.extra, "suit", 0);
            if (card.rank() == rank && ctx.isSuit(card, suit)) ctx.xMult(2);
        }
        @Override
        public long onRoundEnd(RunState state, JokerInstance self) {
            self.extra.put("rank", state.stream("idol").range(2, 14));
            self.extra.put("suit", state.stream("idol").range(0, 3));
            return 0;
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

    /** 从小丑 extra 中读 double（缺失为 0）。 */
    private static double gd(Map<String, Object> extra, String key) {
        Object v = extra.get(key);
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }
}
