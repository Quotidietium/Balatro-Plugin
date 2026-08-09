package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.ScoreContext;

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
}
