package cn.quotidietium.balatro.bench;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.HandEval;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.shop.Packs;
import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.ArrayList;
import java.util.List;

/**
 * 全部基准场景。红线：所有场景由固定种子驱动、每批工作量恒定——
 * 任何引擎优化只要行为不变（458 测试锁定），前后数字即可直接对比。
 */
public final class Scenarios {

    /** 计分场景的小丑配置：覆盖 onScore/onScoreCard/contains/人头判定/逐小丑加成等钩子路径。 */
    private static final String[] SCORE_LOADOUT = {"joker", "fibonacci", "greedy", "abstract", "scaryface"};

    private Scenarios() {
    }

    public static List<Scenario> all() {
        List<Scenario> list = new ArrayList<>();
        list.add(new RngNext());
        list.add(new StreamLookup());
        list.add(new HandEvalScenario());
        list.add(new PlayHandScenario());
        list.add(new DiscardScenario());
        list.add(new RoundCycleScenario());
        list.add(new ShopGenScenario());
        list.add(new CreateRunScenario());
        list.add(new FullRunScenario());
        list.add(new UseConsumableScenario());
        list.add(new PackOpenScenario());
        return list;
    }

    /** P4 新增：构局成本单独测量（区分 playHand/roundCycle 场景里摊销的 createRun 部分）。 */
    private static final class CreateRunScenario implements Scenario {
        private int k;

        public String name() { return "createRun"; }
        public String description() { return "Engine.createRun 开局构局成本"; }

        public long runBatch() {
            long sink = 0;
            for (int i = 0; i < 6_000; i++) {
                RunState s = Engine.createRun("red", 0, "BENCHCR" + (k++ & 63));
                sink += s.fullDeck.size() + s.hand.size();
            }
            Blackhole.consume(sink);
            return 6_000;
        }
    }

    // ================= 随机流核心 =================

    /** mulberry32 核心 next() 吞吐（每个 op 恰一次 next）。 */
    private static final class RngNext implements Scenario {
        public String name() { return "rngNext"; }
        public String description() { return "Rng.Stream.next() 吞吐"; }

        public long runBatch() {
            Rng.Stream st = Rng.makeStream("BENCHRNG", "core");
            long sink = 0;
            for (int i = 0; i < 4_000_000; i++) {
                sink += (long) (st.next() * 1000);
            }
            Blackhole.consume(sink);
            return 4_000_000;
        }
    }

    /** 命名流取流（StreamSource.stream 的 HashMap 查找）——计分 prob/rngInt 每次都走这里。 */
    private static final class StreamLookup implements Scenario {
        public String name() { return "streamLookup"; }
        public String description() { return "RunState.stream(name) 命名流查找"; }

        public long runBatch() {
            RunState s = Engine.createRun("red", 0, "BENCHSL");
            long sink = 0;
            for (int i = 0; i < 20_000_000; i++) {
                sink += System.identityHashCode(s.stream("prob"));
            }
            Blackhole.consume(sink);
            return 20_000_000;
        }
    }

    // ================= 牌型判定 =================

    /** HandEval.evaluate：512 组固定手牌 × 4 种 flags 轮转（无/四指/捷径/污渍）。 */
    private static final class HandEvalScenario implements Scenario {
        private List<List<Card>> hands;
        private RunState[] states;

        public String name() { return "handEval"; }
        public String description() { return "HandEval.evaluate 牌型判定（实时牌型评估热路径）"; }

        private void init() {
            if (hands != null) return;
            Rng.Stream g = Rng.makeStream("BENCHEVAL", "gen");
            hands = new ArrayList<>(512);
            for (int h = 0; h < 512; h++) {
                int size = 1 + (int) (g.next() * 5); // 1..5
                List<Card> cs = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    Card c = new Card(h * 8 + i, 2 + (int) (g.next() * 13), (int) (g.next() * 4));
                    double r = g.next();
                    if (r < 0.10) c.setEnh(Data.Enhancement.STONE);
                    else if (r < 0.22) c.setEnh(Data.Enhancement.WILD);
                    cs.add(c);
                }
                hands.add(cs);
            }
            states = new RunState[4];
            for (int i = 0; i < 4; i++) states[i] = Engine.createRun("red", 0, "BENCHEVALF" + i);
            states[1].flags.put("fourFingers", true);
            states[2].flags.put("shortcut", true);
            states[3].flags.put("smeared", true);
        }

        public long runBatch() {
            init();
            long sink = 0;
            for (int i = 0; i < 200_000; i++) {
                HandEval.Result r = HandEval.evaluate(states[i & 3], hands.get((i >> 2) & 511));
                sink += r.type.order + r.scoring.size();
            }
            Blackhole.consume(sink);
            return 200_000;
        }
    }

    // ================= 出牌 / 弃牌 =================

    /** 新建带小丑配置的回合状态（计分/弃牌场景共用）。 */
    private static RunState newLoadedRound(int k) {
        RunState s = Engine.createRun("red", 0, "BENCHP" + (k & 63));
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        for (String key : SCORE_LOADOUT) s.gainJoker(key, null);
        return s;
    }

    /** 出牌计分管线（含 5 小丑钩子、重掷流不涉及；回合被消耗完/结束即换新局）。 */
    private static final class PlayHandScenario implements Scenario {
        private RunState s;
        private int k;

        public String name() { return "playHand"; }
        public String description() { return "Engine.playHand 计分管线（5 小丑负载）"; }

        private void reset() {
            s = newLoadedRound(k++);
        }

        public long runBatch() {
            if (s == null) reset();
            long sink = 0;
            for (int i = 0; i < 20_000; i++) {
                if (s.phase != Phase.ROUND || s.hand.size() < 2) reset();
                if (s.handsLeft <= 0) s.handsLeft = 4; // 基准内续命：把 op 聚焦在计分管线本身
                int n = Math.min(5, s.hand.size());
                List<Integer> ids = new ArrayList<>(n);
                for (int j = 0; j < n; j++) ids.add(s.hand.get(j).id());
                Engine.PlayResult r = Engine.playHand(s, ids);
                sink += r.ok ? r.score : -1;
            }
            Blackhole.consume(sink);
            return 20_000;
        }
    }

    /** 弃牌管线（含紫蜡封路径与 onDiscard 钩子分发）。 */
    private static final class DiscardScenario implements Scenario {
        private RunState s;
        private int k;

        public String name() { return "discard"; }
        public String description() { return "Engine.discard 弃牌管线"; }

        private void reset() {
            s = newLoadedRound(k++);
        }

        public long runBatch() {
            if (s == null) reset();
            long sink = 0;
            for (int i = 0; i < 20_000; i++) {
                if (s.phase != Phase.ROUND || s.hand.size() < 2) reset();
                if (s.discardsLeft <= 0) s.discardsLeft = 3;
                int n = Math.min(2, s.hand.size());
                List<Integer> ids = new ArrayList<>(n);
                for (int j = 0; j < n; j++) ids.add(s.hand.get(j).id());
                Engine.PlayResult r = Engine.discard(s, ids);
                sink += r.ok ? 1 : 0;
            }
            Blackhole.consume(sink);
            return 20_000;
        }
    }

    // ================= 回合 / 商店 / 整局 =================

    /** 开局→小盲→自动打到商店（覆盖 createRun/回合循环/计分/结算全链）。 */
    private static final class RoundCycleScenario implements Scenario {
        private int k;

        public String name() { return "roundCycle"; }
        public String description() { return "createRun+整回合打到商店"; }

        public long runBatch() {
            long sink = 0;
            for (int i = 0; i < 400; i++) {
                RunState s = Engine.createRun("red", 0, "BENCHRC" + (k++ & 63));
                Engine.selectBlind(s, Data.BlindType.SMALL, false);
                int guard = 0;
                while (s.phase == Phase.ROUND && guard++ < 200) {
                    playBest(s);
                }
                sink += s.roundScore + (s.phase == Phase.SHOP ? 1 : 0);
            }
            Blackhole.consume(sink);
            return 400;
        }
    }

    /** 商店生成 + 两次重掷（覆盖 genShop/商品构造/价格管线）。 */
    private static final class ShopGenScenario implements Scenario {
        private int k;

        public String name() { return "shopGen"; }
        public String description() { return "商店生成+重掷"; }

        public long runBatch() {
            long sink = 0;
            for (int i = 0; i < 1_500; i++) {
                RunState s = Engine.createRun("red", 0, "BENCHSG" + (k++ & 63));
                s.money = 1000;
                Shop.openShop(s);
                sink += s.shop.cards.size() + s.shop.packs.size();
                sink += Shop.reroll(s);
                sink += Shop.reroll(s);
            }
            Blackhole.consume(sink);
            return 1_500;
        }
    }

    /** 整局模拟（开局→盲注→商店买小丑→…→终局），策略固定 ⇒ 与 E2E 测试同口径。 */
    private static final class FullRunScenario implements Scenario {
        private int k;

        public String name() { return "fullRun"; }
        public String description() { return "整局模拟（含商店买小丑）"; }

        public long runBatch() {
            long sink = 0;
            for (int i = 0; i < 150; i++) {
                sink += runFull("BENCHFR" + (k++ & 127));
            }
            Blackhole.consume(sink);
            return 150;
        }
    }

    // ================= 自动驾驶策略（与 EndToEndSimulationTest 同口径 + 商店买小丑） =================

    /** 完整跑一局，返回校验和（ante/胜负/小丑数）。 */
    static long runFull(String seed) {
        RunState s = Engine.createRun("red", 0, seed);
        int guard = 0;
        while (s.phase != Phase.END && guard++ < 3000) {
            switch (s.phase) {
                case BLIND_SELECT -> Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                case ROUND -> playBest(s);
                case SHOP -> shopTurn(s);
                case PACK -> Packs.skip(s);
                default -> { return -1; }
            }
        }
        return s.ante + (s.won ? 100_000L : 0) + s.jokers.size();
    }

    /** 出牌：枚举手牌子集（≤5 张，手牌超 8 只看前 8）取评估分最高者；失败回退从 5 到 1 张。 */
    private static void playBest(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return;
        List<Card> hand = s.hand;
        int n = Math.min(hand.size(), 8);
        double best = -1;
        List<Card> bestCards = null;
        for (int mask = 1; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) > 5) continue;
            List<Card> sub = new ArrayList<>(Integer.bitCount(mask));
            for (int b = 0; b < n; b++) {
                if ((mask & (1 << b)) != 0) sub.add(hand.get(b));
            }
            HandEval.Result r = HandEval.evaluate(s, sub);
            int lvl = s.handLevel(r.type);
            long chips = r.type.chips + (long) (lvl - 1) * r.type.lchips;
            long mult = r.type.mult + (long) (lvl - 1) * r.type.lmult;
            long cardChips = 0;
            for (Card c : r.scoring) cardChips += Data.rankChips(c.rank());
            double est = (chips + cardChips) * (double) mult;
            if (est > best) {
                best = est;
                bestCards = sub;
            }
        }
        if (bestCards != null) {
            List<Integer> ids = new ArrayList<>(bestCards.size());
            for (Card c : bestCards) ids.add(c.id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.ok) return;
        }
        for (int size = Math.min(5, hand.size()); size >= 1; size--) {
            List<Integer> ids = new ArrayList<>(size);
            for (int j = 0; j < size; j++) ids.add(hand.get(j).id());
            Engine.PlayResult r = Engine.playHand(s, ids);
            if (r.ok) return;
            if (s.handsLeft <= 0 || s.hand.isEmpty()) return;
        }
    }

    /** 商店回合：买得起的最便宜小丑（槽位有空时），然后离开。 */
    private static void shopTurn(RunState s) {
        if (s.jokerSpace() > 0 && s.jokers.size() < 5) {
            int best = -1;
            long bestPrice = Long.MAX_VALUE;
            List<Shop.CardItem> cards = s.shop.cards;
            for (int i = 0; i < cards.size(); i++) {
                Shop.CardItem c = cards.get(i);
                if (c.sold || !"joker".equals(c.kind)) continue;
                if (c.price < bestPrice && Shop.canAfford(s, c.price)) {
                    bestPrice = c.price;
                    best = i;
                }
            }
            if (best >= 0) Shop.buyCard(s, best);
        }
        Engine.nextRound(s);
    }

    // ================= P14 新增场景：消耗品 / 补充包 =================
    // 红线：与既有场景同规范——固定种子驱动、每批工作量恒定，前后版本可直接对比。

    /** 消耗品使用管线：轮换 magician(增强2张)/strength(升2张)/mercury(升级对子)。 */
    private static final class UseConsumableScenario implements Scenario {
        private RunState s;
        private int k;

        public String name() { return "useConsumable"; }
        public String description() { return "Consumables.use 消耗品使用管线"; }

        private void reset() {
            s = Engine.createRun("red", 0, "BENCHUC" + (k++ & 63));
            Engine.selectBlind(s, Data.BlindType.SMALL, false);
        }

        public long runBatch() {
            if (s == null) reset();
            long sink = 0;
            for (int i = 0; i < 20_000; i++) {
                if (s.phase != Phase.ROUND || s.hand.size() < 4) reset();
                switch (i % 3) {
                    case 0 -> {
                        s.addConsumableKey("tarot", "magician");
                        sink += cn.quotidietium.balatro.engine.consumable.Consumables.use(s,
                                s.consumables.size() - 1, List.of(s.hand.get(0).id(), s.hand.get(1).id())).ok ? 1 : 0;
                    }
                    case 1 -> {
                        s.addConsumableKey("tarot", "strength");
                        sink += cn.quotidietium.balatro.engine.consumable.Consumables.use(s,
                                s.consumables.size() - 1, List.of(s.hand.get(2).id(), s.hand.get(3).id())).ok ? 1 : 0;
                    }
                    default -> {
                        s.addConsumableKey("planet", "mercury");
                        sink += cn.quotidietium.balatro.engine.consumable.Consumables.use(s,
                                s.consumables.size() - 1, List.of()).ok ? 1 : 0;
                    }
                }
            }
            Blackhole.consume(sink + s.hand.size());
            return 20_000;
        }
    }

    /** 补充包开启管线：开秘术包→选第 1 张（消耗品槽满则跳过）→自动回程。 */
    private static final class PackOpenScenario implements Scenario {
        private RunState s;
        private int k;
        private Data.Pack arcana;

        public String name() { return "packOpen"; }
        public String description() { return "Packs.open+pick/skip 补充包管线"; }

        private void reset() {
            s = Engine.createRun("red", 0, "BENCHPO" + (k++ & 63));
            if (arcana == null) {
                for (Data.Pack p : Data.PACKS) {
                    if (p.type == Data.PackType.ARCANA) { arcana = p; break; }
                }
            }
        }

        public long runBatch() {
            if (s == null) reset();
            long sink = 0;
            for (int i = 0; i < 20_000; i++) {
                Packs.open(s, arcana);
                sink += Packs.pick(s, 0) ? 1 : 0;
                if (s.phase == Phase.PACK) { Packs.skip(s); sink += 2; }
            }
            Blackhole.consume(sink + s.consumables.size());
            return 20_000;
        }
    }
}
