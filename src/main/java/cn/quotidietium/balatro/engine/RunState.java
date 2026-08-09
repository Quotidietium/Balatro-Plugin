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
 * 字段包级可见，供 {@link Engine} 直接读写（对齐 JS 里函数直接改 state 的风格）。
 */
public final class RunState {

    // ---- 配置 ----
    String seed;
    String deckKey;
    int stakeIdx;
    String challenge; // 0.1.0 恒为 null
    final Mods mods = new Mods();

    // ---- 进度 ----
    long money;
    int ante;
    String bossKey;              // 当前底注的 Boss key（仅命名/复现，0.1.0 不生效）
    List<String> bossQueue = new ArrayList<>();
    Data.BlindType blindType;    // 当前进行/刚完成的盲注
    String nextBlind;            // small/big/boss
    Phase phase;
    boolean endless;
    boolean won;
    boolean lost;
    boolean endlessPending;

    // ---- 槽位/上限（base） ----
    int jokerSlots = 5;
    int consumableSlots = 2;
    int handSizeBase = 8;
    int handsBase = 4;
    int discardsBase = 3;
    int interestCap = 5;
    int shopSlots = 2;

    // ---- 持有物 ----
    final List<JokerInstance> jokers = new ArrayList<>();
    final List<String> vouchers = new ArrayList<>();
    final List<String> tags = new ArrayList<>();

    // ---- 牌堆 ----
    List<Card> fullDeck = new ArrayList<>();
    List<Card> drawPile = new ArrayList<>();
    List<Card> hand = new ArrayList<>();
    List<Card> discardPile = new ArrayList<>();

    // ---- 牌型升级/统计 ----
    final Map<Data.HandType, Integer> handLevels = new LinkedHashMap<>();
    final Map<Data.HandType, Integer> handPlayedCount = new LinkedHashMap<>();

    // ---- 回合运行时 ----
    int handsLeft;
    int discardsLeft;
    long roundScore;
    long blindTarget;
    int handSizeRound;
    int handsPlayedThisRound;
    int discardsUsedThisRound;
    boolean usedDiscardThisRound;
    final List<Data.HandType> playedTypesThisRound = new ArrayList<>();
    final Set<Integer> playedThisAnte = new HashSet<>(); // pillar：本底注打过的牌 id
    Map<String, Object> flags = new HashMap<>();
    boolean bossDisabled;
    boolean bossTriggeredThisHand;
    int roundCount = 0;

    // ---- 运行时杂项 ----
    private final StreamSource streamSource;
    private int cardIdSeq = 1;
    final List<String> messages = new ArrayList<>();

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

    Card makeCard(int rank, int suit) {
        Card c = new Card(nextCardId(), rank, suit);
        return c;
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
}
