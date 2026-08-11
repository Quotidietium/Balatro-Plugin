package cn.quotidietium.balatro.engine.consumable;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Consumable;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 消耗品使用与效果，移植自 {@code engine.js} 的 useConsumable/applyConsumable（52 效果）。
 * 目标手牌通过 targetIds（卡 id）传入；非回合内使用时 targets 返回 null（仅限可用项）。
 */
public final class Consumables {

    private Consumables() {
    }

    public static final class Result {
        public final boolean ok;
        public final String err;
        private Result(boolean ok, String err) { this.ok = ok; this.err = err; }
        public static Result ok() { return new Result(true, null); }
        public static Result err(String e) { return new Result(false, e); }
    }

    /** 使用消耗品 idx；targetIds 为手牌卡 id（可空）。 */
    public static Result use(RunState s, int idx, List<Integer> targetIds) {
        if (s.phase != Phase.ROUND && s.phase != Phase.SHOP) return Result.err("当前无法使用");
        if (idx < 0 || idx >= s.consumables.size()) return Result.err("无效消耗品");
        Consumable c = s.consumables.get(idx);
        boolean inRound = s.phase == Phase.ROUND;

        Result res = apply(s, c, targetIds, inRound);
        if (!res.ok) return res;

        s.consumables.remove(idx);
        if (c.kind.equals("tarot") || c.kind.equals("planet")) {
            s.lastTarotPlanet = new RunState.TarotPlanet(c.kind, c.key);
        }
        if (c.kind.equals("tarot")) {
            for (JokerInstance j : new ArrayList<>(s.jokers)) if (!j.debuff) j.def.onUseTarot(s, j);
        }
        if (c.kind.equals("planet")) {
            s.usedPlanets.put(c.key, true);
            for (JokerInstance j : new ArrayList<>(s.jokers)) if (!j.debuff) j.def.onUsePlanet(s, j);
        }
        Engine.sortHand(s); // 消耗品可能改写/增删手牌，整理以保持展示顺序（apply 内 stream.pick 已于此之前完成）
        return Result.ok();
    }

    private static List<Card> targets(RunState s, List<Integer> targetIds, boolean inRound, int max, boolean exact) {
        if (!inRound) return null;
        if (targetIds == null) targetIds = List.of();
        if (targetIds.size() > max) return null;
        if (exact && targetIds.size() != max) return null;
        if (new HashSet<>(targetIds).size() != targetIds.size()) return null;
        List<Card> arr = new ArrayList<>();
        for (int id : targetIds) {
            Card card = null;
            for (Card x : s.hand) if (x.id() == id) { card = x; break; }
            if (card == null) return null;
            arr.add(card);
        }
        return arr;
    }

    /** 版本抽取池（wheel/aura 用，对齐原版：1/3 均匀，非商店权重）。 */
    private static final List<String> EDITION_POOL = List.of("foil", "holo", "poly");

    /** 需要以手牌为目标的消耗品 key（非回合阶段无法使用）。 */
    private static final Set<String> NEED_ROUND_TARGET = Set.of(
            "magician", "empress", "hierophant",
            "lovers", "chariot", "justice", "devil", "tower",
            "strength", "hanged", "death",
            "star", "moon", "sun", "world",
            "talisman", "dejavu", "trance", "medium", "aura", "cryptid");

    private static Result apply(RunState s, Consumable c, List<Integer> targetIds, boolean inRound) {
        Rng.Stream st = s.stream("use:" + c.key + ":" + s.roundCount + ":" + (s.useSeq = (s.useSeq) + 1));

        // 需指定手牌目标的消耗品只能在出牌回合使用（提前给出明确提示，避免误导性报错）
        if (!inRound && NEED_ROUND_TARGET.contains(c.key)) {
            return Result.err("该消耗品需要指定手牌目标，请在出牌回合使用");
        }

        if (c.kind.equals("planet")) {
            Data.Planet p = Data.Planet.byKey(c.key);
            s.levelUpHand(p.hand, 1);
            s.msg(p.name + "：「" + p.hand.name + "」升 1 级");
            return Result.ok();
        }

        if (c.kind.equals("tarot")) {
            switch (c.key) {
                case "fool": {
                    RunState.TarotPlanet last = s.lastTarotPlanet;
                    if (last == null || (last.kind.equals("tarot") && last.key.equals("fool")))
                        return Result.err("没有可复制的牌");
                    return apply(s, new Consumable(last.kind, last.key), targetIds, inRound);
                }
                case "magician", "empress", "hierophant": {
                    List<Card> t = targets(s, targetIds, inRound, 2, false);
                    if (t == null || t.isEmpty()) return Result.err("请选择至多 2 张手牌");
                    Data.Enhancement enh = c.key.equals("magician") ? Data.Enhancement.LUCKY
                            : c.key.equals("empress") ? Data.Enhancement.MULT : Data.Enhancement.BONUS;
                    for (Card card : t) { card.setEnh(enh); card.setFacedown(false); }
                    return Result.ok();
                }
                case "priestess": {
                    for (int i = 0; i < 2; i++) {
                        Data.Planet p = st.pick(List.of(Data.Planet.values()));
                        if (!s.addConsumableKey("planet", p.key)) break;
                    }
                    return Result.ok();
                }
                case "emperor": {
                    for (int i = 0; i < 2; i++) {
                        Data.Tarot t = st.pick(List.of(Data.Tarot.values()));
                        if (t.key.equals("emperor")) continue;
                        if (!s.addConsumableKey("tarot", t.key)) break;
                    }
                    return Result.ok();
                }
                case "lovers", "chariot", "justice", "devil", "tower": {
                    List<Card> t = targets(s, targetIds, inRound, 1, true);
                    if (t == null) return Result.err("请选择 1 张手牌");
                    Data.Enhancement enh = switch (c.key) {
                        case "lovers" -> Data.Enhancement.WILD;
                        case "chariot" -> Data.Enhancement.STEEL;
                        case "justice" -> Data.Enhancement.GLASS;
                        case "devil" -> Data.Enhancement.GOLD;
                        default -> Data.Enhancement.STONE;
                    };
                    t.get(0).setEnh(enh);
                    if (enh == Data.Enhancement.STONE) { t.get(0).setRank(0); t.get(0).setSuit(-1); }
                    t.get(0).setFacedown(false);
                    return Result.ok();
                }
                case "hermit": {
                    long g = Math.min(20, Math.max(0, s.money));
                    s.gainMoney(g);
                    return Result.ok();
                }
                case "wheel": {
                    List<JokerInstance> noEdition = new ArrayList<>();
                    for (JokerInstance j : s.jokers) if (j.edition == null) noEdition.add(j);
                    if (noEdition.isEmpty()) return Result.err("没有可附加版本的小丑");
                    double pch = Boolean.TRUE.equals(s.flags.get("doubleProb")) ? 0.5 : 0.25;
                    if (st.chance(pch)) {
                        JokerInstance j = st.pick(noEdition);
                        // 对齐原版：版本为 1/3 均匀抽取（此前误用商店权重 50/35/15）
                        String e = st.pick(EDITION_POOL);
                        j.edition = parseEdition(e);
                        s.msg("命运之轮：" + j.def.displayName() + " 获得 " + editionName(e));
                    } else s.msg("命运之轮：什么都没发生");
                    return Result.ok();
                }
                case "strength": {
                    List<Card> t = targets(s, targetIds, inRound, 2, false);
                    if (t == null || t.isEmpty()) return Result.err("请选择至多 2 张手牌");
                    for (Card card : t) {
                        if (card.rank() >= 2 && card.rank() < 14) card.setRank(card.rank() + 1);
                        card.setFacedown(false);
                    }
                    return Result.ok();
                }
                case "hanged": {
                    List<Card> t = targets(s, targetIds, inRound, 2, false);
                    if (t == null || t.isEmpty()) return Result.err("请选择至多 2 张手牌");
                    for (Card card : t) { s.destroyCard(card); }
                    cn.quotidietium.balatro.engine.Engine.refillHand(s);
                    return Result.ok();
                }
                case "death": {
                    List<Card> t = targets(s, targetIds, inRound, 2, true);
                    if (t == null) return Result.err("请选择恰好 2 张手牌");
                    Card src = t.get(1), dst = t.get(0);
                    dst.setRank(src.rank()); dst.setSuit(src.suit()); dst.setEnh(src.enh());
                    dst.setEdition(src.edition()); dst.setSeal(src.seal()); dst.setFacedown(false);
                    return Result.ok();
                }
                case "temperance": {
                    long sum = 0;
                    for (JokerInstance j : s.jokers) sum += s.sellValue(j);
                    s.gainMoney(Math.min(50, sum));
                    return Result.ok();
                }
                case "star", "moon", "sun", "world": {
                    List<Card> t = targets(s, targetIds, inRound, 3, false);
                    if (t == null || t.isEmpty()) return Result.err("请选择至多 3 张手牌");
                    int suit = switch (c.key) { case "star" -> 3; case "moon" -> 2; case "sun" -> 1; default -> 0; };
                    for (Card card : t) { if (card.enh() != Data.Enhancement.STONE) card.setSuit(suit); card.setFacedown(false); }
                    return Result.ok();
                }
                case "judgement":
                    return s.gainRandomJoker(null) ? Result.ok() : Result.err("小丑槽已满");
                default:
                    return Result.err("未实现的塔罗牌");
            }
        }

        if (c.kind.equals("spectral")) {
            switch (c.key) {
                case "familiar", "grim", "incantation": {
                    if (!inRoundHand(s)) return Result.err("需要在回合中使用");
                    destroyRandomHandCards(s, st, 1);
                    int n; List<Integer> ranks;
                    if (c.key.equals("familiar")) { n = 3; ranks = List.of(11, 12, 13); }
                    else if (c.key.equals("grim")) { n = 2; ranks = List.of(14); }
                    else { n = 4; ranks = List.of(2, 3, 4, 5, 6, 7, 8, 9, 10); }
                    Data.Enhancement[] enhs = Data.Enhancement.values();
                    for (int i = 0; i < n; i++) {
                        Card card = s.randomPlayingCard();
                        card.setRank(st.pick(ranks));
                        card.setEnh(enhs[st.range(0, enhs.length - 1)]);
                        s.fullDeck.add(card);
                        s.hand.add(card);
                    }
                    trimHand(s);
                    return Result.ok();
                }
                case "talisman", "dejavu", "trance", "medium": {
                    List<Card> t = targets(s, targetIds, inRound, 1, true);
                    if (t == null) return Result.err("请选择 1 张手牌");
                    Data.Seal seal = switch (c.key) {
                        case "talisman" -> Data.Seal.GOLD; case "dejavu" -> Data.Seal.RED;
                        case "trance" -> Data.Seal.BLUE; default -> Data.Seal.PURPLE;
                    };
                    t.get(0).setSeal(seal); t.get(0).setFacedown(false);
                    return Result.ok();
                }
                case "aura": {
                    List<Card> t = targets(s, targetIds, inRound, 1, true);
                    if (t == null) return Result.err("请选择 1 张手牌");
                    // 对齐原版：版本为 1/3 均匀抽取（此前误用商店权重 50/35/15）
                    t.get(0).setEdition(parseEdition(st.pick(EDITION_POOL))); t.get(0).setFacedown(false);
                    return Result.ok();
                }
                case "wraith":
                    s.money = 0; s.gainRandomJoker(2); return Result.ok();
                case "sigil": {
                    if (!inRoundHand(s)) return Result.err("需要在回合中使用");
                    int suit = st.range(0, 3);
                    for (Card card : s.hand) if (card.enh() != Data.Enhancement.STONE) card.setSuit(suit);
                    return Result.ok();
                }
                case "ouija": {
                    if (!inRoundHand(s)) return Result.err("需要在回合中使用");
                    int rank = st.range(2, 14);
                    for (Card card : s.hand) if (card.enh() != Data.Enhancement.STONE) card.setRank(rank);
                    s.handSizeBase -= 1;
                    return Result.ok();
                }
                case "hex": {
                    List<JokerInstance> editable = new ArrayList<>();
                    for (JokerInstance j : s.jokers) if (!j.eternal) editable.add(j);
                    if (editable.isEmpty()) return Result.err("没有可用的小丑");
                    JokerInstance keep = st.pick(editable);
                    s.jokers.removeIf(j -> j != keep && !j.eternal);
                    keep.edition = Data.Edition.NEGATIVE;
                    return Result.ok();
                }
                case "ankh": {
                    List<JokerInstance> copyable = new ArrayList<>();
                    for (JokerInstance j : s.jokers) if (!j.eternal) copyable.add(j);
                    if (copyable.isEmpty()) return Result.err("没有可用的小丑");
                    JokerInstance src = st.pick(copyable);
                    s.jokers.removeIf(j -> j != src && !j.eternal);
                    s.gainJoker(src.def.key(), src.edition);
                    return Result.ok();
                }
                case "cryptid": {
                    List<Card> t = targets(s, targetIds, inRound, 1, true);
                    if (t == null) return Result.err("请选择 1 张手牌");
                    for (int i = 0; i < 2; i++) {
                        Card copy = s.cloneCard(t.get(0));
                        s.fullDeck.add(copy); s.hand.add(copy);
                    }
                    trimHand(s);
                    return Result.ok();
                }
                case "immolate": {
                    if (!inRoundHand(s)) return Result.err("需要在回合中使用");
                    destroyRandomHandCards(s, st, 5);
                    s.gainMoney(20);
                    return Result.ok();
                }
                case "soul":
                    return s.gainRandomJoker(3) ? Result.ok() : Result.err("小丑槽已满");
                case "blackhole":
                    for (Data.HandType h : Data.HandType.values()) s.levelUpHand(h, 1);
                    s.msg("黑洞：所有牌型升 1 级");
                    return Result.ok();
                case "ectoplasm": {
                    List<JokerInstance> editable = new ArrayList<>();
                    for (JokerInstance j : s.jokers) if (j.edition == null) editable.add(j);
                    if (editable.isEmpty()) return Result.err("没有可用的小丑");
                    st.pick(editable).edition = Data.Edition.NEGATIVE;
                    s.handSizeBase -= 1;
                    return Result.ok();
                }
                default:
                    return Result.err("未实现的幻灵牌");
            }
        }
        return Result.err("未知消耗品");
    }

    private static boolean inRoundHand(RunState s) {
        return s.phase == Phase.ROUND && !s.hand.isEmpty();
    }

    private static void destroyRandomHandCards(RunState s, Rng.Stream st, int n) {
        Rng.Stream ds = s.stream("destroyhand");
        for (int i = 0; i < n && !s.hand.isEmpty(); i++) {
            Card v = ds.pick(s.hand);
            s.destroyCard(v);
        }
        // 对齐原版：销毁后补满手牌（drawUpTo）。缺失会使手牌停留短缺状态，
        // 且 wheel Boss 回合跳过补牌即跳过 wheel 流消耗，造成后续流分歧。
        cn.quotidietium.balatro.engine.Engine.refillHand(s);
    }

    private static void trimHand(RunState s) {
        // 对齐原版：允许手牌临时溢出至上限 +3（新生成/复制的牌得以保留），
        // 此前按上限硬裁剪，满手时 cryptid 的复制会直接进弃牌堆（效果作废）。
        while (s.hand.size() > s.handSizeRound + 3) {
            Card c = s.hand.remove(s.hand.size() - 1);
            s.discardPile.add(c);
        }
    }

    private static Data.Edition parseEdition(String e) {
        return switch (e) {
            case "foil" -> Data.Edition.FOIL;
            case "holo" -> Data.Edition.HOLO;
            case "poly" -> Data.Edition.POLY;
            case "negative" -> Data.Edition.NEGATIVE;
            default -> null;
        };
    }

    private static String editionName(String e) {
        return switch (e) {
            case "foil" -> "闪膜"; case "holo" -> "镭射"; case "poly" -> "多彩"; case "negative" -> "负片"; default -> e;
        };
    }
}
