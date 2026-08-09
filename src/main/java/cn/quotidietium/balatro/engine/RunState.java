package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 一局游戏的可变状态，对应 balatro {@code createRun} 返回的 state 对象。
 *
 * <p>字段为 {@code public}（对齐 JS 里开放的可变 state：引擎直接改写，渲染/会话层只读）。
 * 仅 {@link #streamSource} 与卡牌 id 序列私有。
 */
public final class RunState {

    // ---- 配置 ----
    public String seed;
    public String deckKey;
    public int stakeIdx;
    public String challenge; // 0.1.0 恒为 null
    public final Mods mods = new Mods();

    // ---- 进度 ----
    public long money;
    public int ante;
    public String bossKey;              // 当前底注的 Boss key（仅命名/复现，0.1.0 不生效）
    public List<String> bossQueue = new ArrayList<>();
    public Data.BlindType blindType;    // 当前进行/刚完成的盲注
    public String nextBlind;            // small/big/boss
    public Phase phase;
    public boolean endless;
    public boolean won;
    public boolean lost;
    public boolean endlessPending;

    // ---- 槽位/上限（base） ----
    public int jokerSlots = 5;
    public int consumableSlots = 2;
    public int handSizeBase = 8;
    public int handsBase = 4;
    public int discardsBase = 3;
    public int interestCap = 5;
    public int shopSlots = 2;

    // ---- 持有物 ----
    public final List<JokerInstance> jokers = new ArrayList<>();
    public final List<Object> consumables = new ArrayList<>(); // 0.2.0 类型化
    public final List<String> vouchers = new ArrayList<>();
    public final List<String> tags = new ArrayList<>();

    // ---- 牌堆 ----
    public List<Card> fullDeck = new ArrayList<>();
    public List<Card> drawPile = new ArrayList<>();
    public List<Card> hand = new ArrayList<>();
    public List<Card> discardPile = new ArrayList<>();

    // ---- 牌型升级/统计 ----
    public final Map<Data.HandType, Integer> handLevels = new LinkedHashMap<>();
    public final Map<Data.HandType, Integer> handPlayedCount = new LinkedHashMap<>();
    public final Map<String, Boolean> usedPlanets = new HashMap<>(); // 卫星小丑用（0.2.0 起记录）

    // ---- 回合运行时 ----
    public int handsLeft;
    public int discardsLeft;
    public long roundScore;
    public long blindTarget;
    public int handSizeRound;
    public int handsPlayedThisRound;
    public int discardsUsedThisRound;
    public boolean usedDiscardThisRound;
    public final List<Data.HandType> playedTypesThisRound = new ArrayList<>();
    public final Set<Integer> playedThisAnte = new HashSet<>(); // pillar：本底注打过的牌 id
    public Map<String, Object> flags = new HashMap<>();
    public boolean bossDisabled;
    public boolean bossTriggeredThisHand;
    public int roundCount = 0;
    public boolean grosDead; // 格罗米歇尔已碎（决定卡文迪什是否可生成）
    public cn.quotidietium.balatro.engine.shop.Shop.ShopData shop; // 当前商店（0.2.0）
    public final Map<String, Object> nextShop = new HashMap<>();  // 标签等对下个商店的修饰

    // ---- 运行时杂项 ----
    private final StreamSource streamSource;
    private int cardIdSeq = 1;
    public final List<String> messages = new ArrayList<>();

    RunState(String seed) {
        this.seed = seed;
        this.streamSource = new StreamSource(seed);
        for (Data.HandType h : Data.HandType.values()) {
            handLevels.put(h, 1);
        }
    }

    // ---- 随机流 ----
    public Rng.Stream stream(String name) {
        return streamSource.stream(name);
    }

    // ---- 卡牌 id ----
    int nextCardId() {
        return cardIdSeq++;
    }

    public Card makeCard(int rank, int suit) {
        return new Card(nextCardId(), rank, suit);
    }

    // ---- 金钱/消息 ----
    public void gainMoney(long n) {
        money += n;
    }

    public void msg(String text) {
        messages.add(text);
        if (messages.size() > 200) {
            messages.remove(0);
        }
    }

    /** 取最近的消息（用于 UI 简报）。 */
    public List<String> drainMessages() {
        List<String> copy = new ArrayList<>(messages);
        messages.clear();
        return copy;
    }

    // ---- 牌型辅助（供小丑钩子与计分共用，对应 isFaceCard/isSuitFor） ----
    public boolean isFace(Card c) {
        if (flags != null && Boolean.TRUE.equals(flags.get("allFace"))) return true;
        return c.rank() >= 11 && c.rank() <= 13;
    }

    public boolean isSuit(Card c, int s) {
        if (c.enh() == Data.Enhancement.STONE) return false;
        if (c.enh() == Data.Enhancement.WILD) return true;
        if (flags != null && Boolean.TRUE.equals(flags.get("smeared"))) {
            if (s == 1 || s == 3) return c.suit() == 1 || c.suit() == 3;
            return c.suit() == 0 || c.suit() == 2;
        }
        return c.suit() == s;
    }

    /** 本局最常打出的牌型（公牛 Boss 用），无则 null。 */
    public Data.HandType mostPlayedType() {
        Data.HandType best = null;
        int bestN = -1;
        for (Map.Entry<Data.HandType, Integer> e : handPlayedCount.entrySet()) {
            if (e.getValue() > bestN) {
                bestN = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public int handLevel(Data.HandType t) {
        return handLevels.getOrDefault(t, 1);
    }

    /** 销毁一张小丑（自毁类小丑在 onRoundEnd/onPlayHand 中调用）。 */
    public void destroyJoker(JokerInstance j, String reason) {
        jokers.remove(j);
        if (reason != null) msg(reason);
    }

    /** 小丑售价（基础售价/2 + 售价加成）。 */
    public int sellValue(JokerInstance j) {
        return j.def.cost() / 2 + j.sellBonus;
    }

    /** 升级牌型等级。 */
    public void levelUpHand(Data.HandType type, int n) {
        handLevels.merge(type, n, Integer::sum);
    }

    /** 复制一张牌（新 id，复制增强/版本/蜡封/永久筹码）。 */
    public Card cloneCard(Card c) {
        Card n = new Card(nextCardId(), c.rank(), c.suit());
        n.setEnh(c.enh());
        n.setEdition(c.edition());
        n.setSeal(c.seal());
        n.addChipBonus(c.chipBonus());
        return n;
    }

    /** 卡牌可读名。 */
    public String cardName(Card c) {
        if (c.isStone()) return "石头牌";
        return Data.Suit.byIndex(c.suit()).name + Data.rankName(c.rank());
    }

    /** 从牌组/牌堆/手牌/弃牌堆移除一张牌。 */
    public void removeCardFromDeck(Card c) {
        fullDeck.remove(c);
        drawPile.remove(c);
        hand.remove(c);
        discardPile.remove(c);
    }

    /** 获得随机消耗品（0.2.0 实现；0.1.0 占位无操作）。 */
    public void gainConsumable(String kind) {
        // TODO 0.2.0：按 kind 从 Tarot/Planet/Spectral 池取并加入消耗品区
    }

    /** 消除当前 Boss 效果（0.1.0 Boss 效果未生效，置标志）。 */
    public void disableBoss() {
        bossDisabled = true;
    }

    /** 获得跳过标签（0.1.0 仅入列表，效果 0.3.0）。 */
    public void gainTag(String key) {
        tags.add(key);
    }

    /** 评估一组手牌的牌型（供小丑钩子调用，如烧焦小丑）。 */
    public HandEval.Result evaluateHand(List<Card> cards) {
        return HandEval.evaluate(this, cards);
    }

    /** 剩余小丑槽。 */
    public int jokerSpace() {
        return jokerSlots - jokers.size();
    }

    /** 获得一张指定小丑（0.2.0 商店/效果共用）。 */
    public boolean gainJoker(String key, Data.Edition edition) {
        if (jokerSpace() <= 0) return false;
        JokerInstance j = cn.quotidietium.balatro.engine.joker.JokerRegistry.create(key);
        if (j == null) return false;
        if (edition != null) j.edition = edition;
        jokers.add(j);
        msg("获得小丑：" + cn.quotidietium.balatro.engine.joker.JokerRegistry.nameOf(key));
        return true;
    }

    /** 随机获得一张指定稀有度的小丑。 */
    public void gainRandomJoker(int rarity) {
        java.util.List<Joker> pool = new java.util.ArrayList<>();
        for (Joker j : cn.quotidietium.balatro.engine.joker.JokerRegistry.allJokers()) {
            if (cn.quotidietium.balatro.engine.joker.JokerRegistry.rarityOf(j.key()) == rarity) pool.add(j);
        }
        if (pool.isEmpty()) return;
        Joker pick = stream("jokergrant").pick(pool);
        gainJoker(pick.key(), null);
    }

    /** 把一张牌加入牌组（触发 onCardAdded）。 */
    public void addCardToDeck(Card c) {
        fullDeck.add(c);
        for (JokerInstance j : new java.util.ArrayList<>(jokers)) j.def.onCardAdded(this, c, j);
    }

    /** 生成一张随机游戏牌（rpc 流）。 */
    public Card randomPlayingCard() {
        Rng.Stream s = stream("rpc");
        return makeCard(s.range(2, 14), s.range(0, 3));
    }
}
