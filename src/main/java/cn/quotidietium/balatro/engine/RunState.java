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
    public final List<Consumable> consumables = new ArrayList<>();
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
    public Integer bossSuitDebuff; // Boss 花色失效（null 或 0-3）
    public boolean bossFaceDebuff; // Boss 人头牌失效
    public boolean bossLeaf;       // 翠绿之叶：全部失效
    public Integer bellCardId;     // 翠绿铃：强制选中的牌 id（null 无）
    public int roundCount = 0;
    public boolean grosDead; // 格罗米歇尔已碎（决定卡文迪什是否可生成）
    public int inflation;   // 通货膨胀挑战：商店加价累计
    public int useSeq;      // 消耗品使用序号（构造唯一流）
    public TarotPlanet lastTarotPlanet; // 愚人复制用：上一张使用的塔罗/星球
    public boolean doubleTagPending;    // 翻倍标签待复制
    public int statsHandsPlayed, statsDiscardsUnused, statsBlindsSkipped; // 标签/统计用

    /** 上一张使用的塔罗/星球（供愚人复制）。 */
    public static final class TarotPlanet {
        public final String kind, key;
        public TarotPlanet(String kind, String key) { this.kind = kind; this.key = key; }
    }
    public cn.quotidietium.balatro.engine.shop.Shop.ShopData shop; // 当前商店（0.2.0）
    public final Map<String, Object> nextShop = new HashMap<>();  // 标签等对下个商店的修饰
    public cn.quotidietium.balatro.engine.shop.Packs.Session pack; // 当前补充包
    public Phase packReturn;                                       // 开完包返回的阶段
    public int packSeq;                                            // 补充包序号（构造唯一流）

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

    /** 小丑售价（含版本加成，max(1)）。 */
    public int sellValue(JokerInstance j) {
        int cost = j.def.cost();
        if (j.edition == Data.Edition.FOIL) cost += 2;
        else if (j.edition == Data.Edition.HOLO) cost += 3;
        else if (j.edition == Data.Edition.POLY) cost += 5;
        else if (j.edition == Data.Edition.NEGATIVE) cost += 5;
        return Math.max(1, cost / 2 + j.sellBonus);
    }

    /** 出售第 idx 张小丑（永恒不可出售；触发 onSell/onAnySell；解除翠绿之叶）。 */
    public boolean sellJoker(int idx) {
        if (idx < 0 || idx >= jokers.size()) return false;
        JokerInstance j = jokers.get(idx);
        if (j.eternal) { msg("永恒小丑不可出售"); return false; }
        int val = sellValue(j);
        jokers.remove(idx);
        money += val;
        msg("出售 " + j.def.displayName() + " +$" + val);
        j.def.onSell(this, j);
        for (JokerInstance o : new ArrayList<>(jokers)) if (!o.debuff) o.def.onAnySell(this, o);
        if (bossLeaf) {
            bossLeaf = false;
            for (Card c : hand) c.setDebuff(false);
            msg("翠绿之叶：失效解除");
        }
        Engine.recomputeFlags(this);
        return true;
    }

    /** 出售第 idx 个消耗品。 */
    public boolean sellConsumable(int idx) {
        if (idx < 0 || idx >= consumables.size()) return false;
        Consumable c = consumables.remove(idx);
        int val = Math.max(1, 1 + c.sellBonus);
        money += val;
        msg("出售消耗品 +$" + val);
        return true;
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

    /** 销毁一张牌（含 onFaceDestroyed 钩子：人头牌被销毁时触发 Canio 等）。 */
    public void destroyCard(Card c) {
        // 对齐原版 isFaceCard：尊重 allFace 标志（空想性错觉 pareidolia 时所有牌视为人头牌）
        if (isFace(c)) {
            for (JokerInstance j : new ArrayList<>(jokers)) {
                if (!j.debuff) j.def.onFaceDestroyed(this, j);
            }
        }
        removeCardFromDeck(c);
    }

    /** 获得随机消耗品（按 kind 从对应池随机取；对齐 engine.js：加入成功时提示"获得：名称"）。 */
    public void gainConsumable(String kind) {
        switch (kind) {
            case "tarot" -> {
                Data.Tarot t = stream("consumable").pick(List.of(Data.Tarot.values()));
                if (addConsumableKey("tarot", t.key)) msg("获得：" + t.name);
            }
            case "planet" -> {
                Data.Planet p = stream("consumable").pick(List.of(Data.Planet.values()));
                if (addConsumableKey("planet", p.key)) msg("获得：" + p.name);
            }
            default -> {
                Data.Spectral sp = stream("consumable").pick(List.of(Data.Spectral.values()));
                if (addConsumableKey("spectral", sp.key)) msg("获得：" + sp.name);
            }
        }
    }

    /** 把指定消耗品加入消耗品区（槽位满则失败）。 */
    public boolean addConsumableKey(String kind, String key) {
        // 对齐 REF engine.js:202-205：negative 版本消耗品 each +1 槽
        int neg = 0;
        for (Consumable c : consumables) if (c.edition == Data.Edition.NEGATIVE) neg++;
        if (consumables.size() >= consumableSlots + neg) return false;
        consumables.add(new Consumable(kind, key));
        return true;
    }

    /** 消除当前 Boss 效果（对齐 REF engine.js:1987-1993：置标志 + 清除所有 Boss debuff 标记 + hand 已有 debuff）。 */
    public void disableBoss() {
        bossDisabled = true;
        bossSuitDebuff = null;
        bossFaceDebuff = false;
        bossLeaf = false;
        for (Card c : hand) c.setDebuff(false);
    }

    /** 获得跳过标签（应用效果）。 */
    public void gainTag(String key) {
        Engine.gainTag(this, key);
    }

    /** 评估一组手牌的牌型（供小丑钩子调用，如烧焦小丑）。 */
    public HandEval.Result evaluateHand(List<Card> cards) {
        return HandEval.evaluate(this, cards);
    }

    /** 剩余小丑槽（对齐 REF engine.js:1402-1405：negative 版本小丑 each +1 槽）。 */
    public int jokerSpace() {
        int neg = 0;
        for (JokerInstance j : jokers) if (j.edition == Data.Edition.NEGATIVE) neg++;
        return jokerSlots + neg - jokers.size();
    }

    /** 获得一张指定小丑（0.2.0 商店/效果共用）。对齐 engine.js gainJoker：加入后重算 flags。 */
    public boolean gainJoker(String key, Data.Edition edition) {
        if (jokerSpace() <= 0) return false;
        JokerInstance j = cn.quotidietium.balatro.engine.joker.JokerRegistry.create(key);
        if (j == null) return false;
        if (edition != null) j.edition = edition;
        jokers.add(j);
        msg("获得小丑：" + cn.quotidietium.balatro.engine.joker.JokerRegistry.nameOf(key));
        // 原版 gainJoker 即 computeFlags：新小丑的 flags（fourFingers/splash/handSize 等）
        // 须立即对后续计分/回合生效（如回合中用「审判」获得带 flags 的小丑）
        Engine.recomputeFlags(this);
        return true;
    }

    /**
     * 随机获得一张指定稀有度的小丑（对齐 engine.js gainRandomJoker）。
     *
     * <p>rarity=null 时从 普通+罕见+稀有 混合池**均匀**抽取（原版语义，无传奇）；
     * 否则取该稀有度池。流名 {@code randomjoker}、满槽先返回不耗流，均与原版逐字一致
     * （此前误用 jokergrant 流 + 70/25/5 分段掷稀有度，属移植错误，破坏种子复现）。
     */
    public boolean gainRandomJoker(Integer rarity) {
        if (jokerSpace() <= 0) return false;
        Rng.Stream st = stream("randomjoker");
        java.util.List<Joker> pool = new java.util.ArrayList<>();
        for (Joker j : cn.quotidietium.balatro.engine.joker.JokerRegistry.allJokersOrdered()) {
            int r = cn.quotidietium.balatro.engine.joker.JokerRegistry.rarityOf(j.key());
            if (rarity == null ? r < 3 : r == rarity) pool.add(j);
        }
        if (pool.isEmpty()) return false;
        Joker pick = st.pick(pool);
        return gainJoker(pick.key(), null);
    }

    /** 把一张牌加入牌组（触发 onCardAdded）。 */
    public void addCardToDeck(Card c) {
        fullDeck.add(c);
        for (JokerInstance j : new java.util.ArrayList<>(jokers)) {
            if (!j.debuff) j.def.onCardAdded(this, c, j);
        }
    }

    /** 生成一张随机游戏牌（rpc 流）。 */
    public Card randomPlayingCard() {
        Rng.Stream s = stream("rpc");
        return makeCard(s.range(2, 14), s.range(0, 3));
    }
}
