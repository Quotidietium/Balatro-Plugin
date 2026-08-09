package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏引擎，移植自 {@code REF/balatro/js/engine.js}（纯逻辑，零 Bukkit 依赖）。
 *
 * <p>0.1.0 范围：标准 52 张牌组、白注（可带简单牌组/赌注效果）、无挑战；
 * 牌型判定 + chips×mult 计分 + 抽/出/弃 + small/big/boss 盲注 + 8 底注通关。
 *
 * <p><b>0.1.0 简化</b>（后续版本补齐）：
 * <ul>
 *   <li>Boss 的"干扰型"效果（钩子/眼睛/通灵者等）未生效（0.3.0）；Boss 的目标分修正（高墙×4/紫罗兰之瓶×3）已生效。</li>
 *   <li>商店/补充包：胜出盲注后直接进入下一盲注选择（0.2.0 接商店）。</li>
 *   <li>跳过盲注的标签（0.3.0）。</li>
 *   <li>消耗品（塔罗/星球/幻灵）及其相关蜡封效果（0.2.0）。</li>
 * </ul>
 */
public final class Engine {

    private static final List<Data.Boss> BOSSES = List.of(Data.Boss.values());

    private Engine() {
    }

    // ================= 创建一局 =================

    public static RunState createRun(String deckKey, int stakeIdx, String seed) {
        if (deckKey == null || deckKey.isEmpty()) deckKey = "red";
        if (seed == null || seed.isEmpty()) seed = Rng.randomSeedString();
        RunState s = new RunState(seed);
        s.deckKey = deckKey;
        s.stakeIdx = stakeIdx;
        s.money = 4;
        s.ante = 1;

        // 赌注效果（累加）
        if (stakeIdx >= 1) s.mods.redStake = true;
        if (stakeIdx >= 2) s.mods.greenStake = true;
        if (stakeIdx >= 3) s.mods.blackStake = true;
        if (stakeIdx >= 5) s.mods.purpleStake = true;
        if (stakeIdx >= 6) s.mods.orangeStake = true;
        if (stakeIdx >= 7) s.mods.goldStake = true;

        // 牌组效果（仅状态类；magic/ghost/zodiac 涉及优惠券/消耗品，留待 0.5.0）
        if ("yellow".equals(deckKey)) s.money += 10;
        if ("green".equals(deckKey)) s.mods.noInterest = true;
        if ("plasma".equals(deckKey)) s.mods.plasma = true;

        buildFullDeck(s);
        applyVouchersPassive(s);
        startAnte(s);
        return s;
    }

    private static void buildFullDeck(RunState s) {
        // 0.1.0：标准 52 张（花色 0..3 × 点数 2..14）。牌组构成变体（erratic/checkered/...）= 0.5.0。
        for (int suit = 0; suit < 4; suit++) {
            for (int rank = 2; rank <= 14; rank++) {
                s.fullDeck.add(s.makeCard(rank, suit));
            }
        }
    }

    // ================= 底注 / 盲注 =================

    private static void startAnte(RunState s) {
        s.playedThisAnte.clear();
        chooseBoss(s);
        s.phase = Phase.BLIND_SELECT;
        s.blindType = null;
        s.nextBlind = "small";
    }

    private static void chooseBoss(RunState s) {
        Rng.Stream st = s.stream("boss");
        Data.Boss picked = st.pick(BOSSES);
        if (s.bossKey != null) {
            for (int tries = 0; tries < 5 && picked.key.equals(s.bossKey); tries++) {
                picked = st.pick(BOSSES);
            }
        }
        s.bossKey = picked.key;
        s.bossQueue.clear();
        s.bossQueue.add(picked.key);
    }

    /** 当前 Boss 定义（仅命名/展示）。 */
    public static Data.Boss bossDef(RunState s) {
        if (s.bossQueue.isEmpty()) return BOSSES.get(0);
        return Data.Boss.byKey(s.bossQueue.get(0));
    }

    /** 盲注目标分。 */
    public static long blindTarget(RunState s, Data.BlindType type) {
        double base = Data.blindBase(s.ante);
        if (s.mods.greenStake) base *= Math.pow(1.15, s.ante - 1);
        if (s.mods.purpleStake) base *= Math.pow(1.3, s.ante - 1);
        double mult = type.mult;
        if (type == Data.BlindType.BOSS) {
            String bk = s.bossQueue.isEmpty() ? null : s.bossQueue.get(0);
            if ("wall".equals(bk)) mult = 4;
            else if ("vessel".equals(bk)) mult = 3;
        }
        if ("plasma".equals(s.deckKey)) mult *= 2;
        if (s.mods.blindMult != 0) mult *= s.mods.blindMult;
        if (s.mods.jokerTax != 0) mult *= (1 + s.mods.jokerTax * s.jokers.size());
        return Math.round(base * mult);
    }

    /** 选择盲注（type 必须等于 nextBlind）；skip 路径 0.1.0 不支持标签（0.3.0）。 */
    public static boolean selectBlind(RunState s, Data.BlindType type, boolean skip) {
        if (s.phase != Phase.BLIND_SELECT) return false;
        if (!type.key.equals(s.nextBlind)) return false;
        if (skip) {
            if (type == Data.BlindType.BOSS) return false;
            // TODO 0.3.0：获得标签 + 小丑 onSkip
            s.nextBlind = type == Data.BlindType.SMALL ? "big" : "boss";
            return true;
        }
        s.blindType = type;
        startRound(s);
        return true;
    }

    // ================= 回合 =================

    private static void applyVouchersPassive(RunState s) {
        s.jokerSlots = 5 + ("black".equals(s.deckKey) ? 1 : 0) - ("painted".equals(s.deckKey) ? 1 : 0);
        s.consumableSlots = 2 + ("nebula".equals(s.deckKey) ? -1 : 0);
        s.shopSlots = 2;
        s.handSizeBase = 8 + ("painted".equals(s.deckKey) ? 2 : 0);
        s.handsBase = 4 + ("blue".equals(s.deckKey) ? 1 : 0) - ("black".equals(s.deckKey) ? 1 : 0);
        s.discardsBase = 3 + ("red".equals(s.deckKey) ? 1 : 0) - (s.stakeIdx >= 4 ? 1 : 0);
        if (s.mods.handsSet != 0) s.handsBase = s.mods.handsSet;
        if (s.mods.handSize != 0) s.handSizeBase += s.mods.handSize;
        s.interestCap = 5;
        // 优惠券加成（0.1.0 无优惠券）
    }

    private static void computeFlags(RunState s) {
        Map<String, Object> f = new HashMap<>();
        for (JokerInstance j : s.jokers) {
            if (j.debuff) continue;
            Map<String, Object> fl = j.def.flags() != null ? j.def.flags() : j.def.flagsFn(s, j);
            if (fl != null) {
                for (Map.Entry<String, Object> e : fl.entrySet()) {
                    Object v = e.getValue();
                    if (v instanceof Number) {
                        int cur = f.get(e.getKey()) instanceof Number ? ((Number) f.get(e.getKey())).intValue() : 0;
                        f.put(e.getKey(), cur + ((Number) v).intValue());
                    } else {
                        f.put(e.getKey(), v);
                    }
                }
            }
        }
        s.flags = f;
    }

    private static void startRound(RunState s) {
        applyVouchersPassive(s);
        computeFlags(s);
        Map<String, Object> f = s.flags;

        s.roundCount++;
        s.phase = Phase.ROUND;
        s.roundScore = 0;
        s.handsPlayedThisRound = 0;
        s.discardsUsedThisRound = 0;
        s.usedDiscardThisRound = false;
        s.playedTypesThisRound.clear();
        s.bossDisabled = false;
        s.bossTriggeredThisHand = false;

        s.blindTarget = blindTarget(s, s.blindType);
        s.handSizeRound = Math.max(1, s.handSizeBase + intFlag(f, "handSize"));
        s.handsLeft = Math.max(0, s.handsBase + intFlag(f, "hands"));
        s.discardsLeft = Math.max(0, s.discardsBase + intFlag(f, "discards"));

        // Boss 干扰效果、juggle 标签 → 0.3.0

        // 洗牌并抽牌
        s.drawPile = new ArrayList<>(s.fullDeck);
        s.stream("shuffle" + s.roundCount).shuffle(s.drawPile);
        s.hand.clear();
        s.discardPile.clear();
        for (int i = 0; i < s.handSizeRound; i++) drawOne(s, false);

        // 小丑回合开始钩子
        List<JokerInstance> snap = new ArrayList<>(s.jokers);
        for (JokerInstance j : snap) {
            if (!j.debuff && s.jokers.contains(j)) {
                j.def.onBlindSelect(s, j, s.blindType);
                j.def.onBlindStart(s, j);
                j.def.onRoundStart(s, j);
            }
        }
        s.msg("回合开始：目标 " + s.blindTarget + " 分");
    }

    private static Card drawOne(RunState s, boolean forceFacedown) {
        if (s.drawPile.isEmpty()) return null;
        Card c = s.drawPile.remove(s.drawPile.size() - 1);
        c.setFacedown(forceFacedown);
        // Boss 抽牌效果（wheel/mark/pillar/花色/人头/leaf）→ 0.3.0
        s.hand.add(c);
        return c;
    }

    private static void drawUpTo(RunState s) {
        int n = s.handSizeRound - s.hand.size();
        // serpent（贪蛇）→ 0.3.0
        for (int i = 0; i < n; i++) {
            Card c = drawOne(s, false);
            if (c == null) break;
            // fish（鱼）→ 0.3.0
        }
    }

    public static HandEval.Result evaluateHand(RunState s, List<Card> cards) {
        return HandEval.evaluate(s, cards);
    }

    // ================= 出牌计分 =================

    public static PlayResult playHand(RunState s, List<Integer> cardIds) {
        if (s.phase != Phase.ROUND) return PlayResult.err("当前不在回合中");
        if (s.handsLeft <= 0) return PlayResult.err("没有剩余出牌次数");
        if (cardIds == null || cardIds.size() < 1 || cardIds.size() > 5) return PlayResult.err("请选择 1-5 张牌");
        Set<Integer> uniq = new HashSet<>(cardIds);
        if (uniq.size() != cardIds.size()) return PlayResult.err("不能重复选择同一张牌");

        List<Card> cards = new ArrayList<>();
        for (int id : cardIds) {
            Card c = findInHand(s, id);
            if (c == null) return PlayResult.err("无效的手牌");
            cards.add(c);
        }
        // 保持手牌顺序（从左到右结算）
        cards.sort(Comparator.comparingInt(c -> s.hand.indexOf(c)));

        // Boss 出牌限制（psychic/bell/eye/mouth）→ 0.3.0

        HandEval.Result evalRes = HandEval.evaluate(s, cards);
        Data.HandType type = evalRes.type;

        s.bossTriggeredThisHand = false;

        // ---------- 计分 ----------
        List<String> events = new ArrayList<>();
        int lvl = s.handLevel(type);
        Data.HandType hd = type;
        double chips = hd.chips + (long) (lvl - 1) * hd.lchips;
        double mult = hd.mult + (long) (lvl - 1) * hd.lmult;

        // flint（燧石）→ 0.3.0

        List<Card> scoringCards = evalRes.scoring;
        List<Card> heldCards = new ArrayList<>();
        for (Card c : s.hand) {
            if (!cardIds.contains(c.id())) heldCards.add(c);
        }
        List<JokerInstance> activeJokers = new ArrayList<>();
        for (JokerInstance j : s.jokers) {
            if (!j.debuff && !j.debuffHand) activeJokers.add(j);
        }

        ScoreContext ctx = new ScoreContext(s, type, chips, mult, cards, heldCards, events);

        // 绯红之心 → 0.3.0

        // 1) 打出牌逐张计分（含重新触发）
        for (int ci = 0; ci < cards.size(); ci++) {
            Card card = cards.get(ci);
            card.setFacedown(false);
            boolean isScoring = scoringCards.contains(card);
            if (!isScoring) continue;
            ctx.scoreIndex = ci;

            int retriggers = 0;
            if (card.seal() == Data.Seal.RED) retriggers += 1;
            for (int ji = 0; ji < activeJokers.size(); ji++) {
                JokerInstance src = resolveCopy(activeJokers, ji);
                if (src != null && !activeJokers.get(ji).debuffHand) {
                    ctx.joker = src;
                    retriggers += src.def.retrigger(card, ctx);
                }
            }
            int times = card.debuff() ? 0 : (1 + retriggers);
            for (int t = 0; t < times; t++) {
                scoreOneCard(s, ctx, card);
                for (int ji = 0; ji < activeJokers.size(); ji++) {
                    JokerInstance src = resolveCopy(activeJokers, ji);
                    if (src != null && !activeJokers.get(ji).debuffHand) {
                        ctx.joker = src;
                        src.def.onScoreCard(ctx, card);
                    }
                }
            }
            // 玻璃牌破碎
            if (card.enh() == Data.Enhancement.GLASS && !card.debuff()) {
                double p = s.mods.glassDouble ? 0.5 : 0.25;
                if (s.stream("glass").chance(p)) {
                    card.addChipBonus(0); // 占位：标记破碎（0.2.0 用 broken 标志移除）
                    events.add("玻璃牌破碎了");
                    for (JokerInstance j : s.jokers) j.def.onGlassBreak(s, j);
                    // 0.1.0 无玻璃牌；破碎移除在下方 removeCardEverywhere（0.2.0）
                }
            }
        }

        // 2) 持有牌效果（钢铁 + onHeld；哑剧重触发）
        int heldRepeat = Boolean.TRUE.equals(s.flags.get("mimeRetrigger")) ? 2 : 1;
        for (Card card : heldCards) {
            if (card.debuff()) continue;
            for (int rep = 0; rep < heldRepeat; rep++) {
                if (card.enh() == Data.Enhancement.STEEL) ctx.xMult(1.5);
                for (int ji = 0; ji < activeJokers.size(); ji++) {
                    JokerInstance src = resolveCopy(activeJokers, ji);
                    if (src != null && !activeJokers.get(ji).debuffHand) {
                        ctx.joker = src;
                        src.def.onHeld(ctx, card);
                    }
                }
            }
        }

        // 3) 独立小丑结算（含蓝图/头脑风暴）
        for (int ji = 0; ji < activeJokers.size(); ji++) {
            JokerInstance j = activeJokers.get(ji);
            if (j.debuffHand) continue;
            applyJokerScore(s, ctx, j, ji, activeJokers);
        }

        // 天文台（observatory 优惠券）→ 0.2.0

        // 等离子牌组：平衡
        if (s.mods.plasma) {
            double avg = (ctx.chips + ctx.mult) / 2.0;
            ctx.chips = Math.round(avg);
            ctx.mult = Math.round(avg);
        }

        long chipsR = Math.round(ctx.chips);
        double multR = Math.round(ctx.mult * 100.0) / 100.0;
        long score = Math.round(chipsR * multR);
        s.roundScore += score;

        // ---------- 出牌后处理 ----------
        s.handsLeft--;
        s.handsPlayedThisRound++;
        s.handPlayedCount.merge(type, 1, Integer::sum);
        s.playedTypesThisRound.add(type);
        for (Card c : cards) s.playedThisAnte.add(c.id());

        // Boss 公牛/牙齿/手臂 → 0.3.0

        // 移除打出的牌
        s.hand.removeIf(c -> cardIds.contains(c.id()));
        for (Card c : cards) s.discardPile.add(c);

        // 小丑 onPlayHand
        PlayHandInfo info = new PlayHandInfo(s, type, cards, scoringCards);
        List<JokerInstance> jokersSnap = new ArrayList<>(s.jokers);
        for (JokerInstance j : jokersSnap) {
            if (!j.debuff && !j.debuffHand && s.jokers.contains(j)) {
                j.def.onPlayHand(s, info);
            }
        }

        // 恢复 debuffHand
        for (JokerInstance j : s.jokers) j.debuffHand = false;

        // Boss 钩子（hook）→ 0.3.0

        drawUpTo(s);

        // 胜负判定
        boolean won = s.roundScore >= s.blindTarget;
        if (won) {
            endRound(s, true);
            return PlayResult.ok(score, type, events, true, false);
        }
        if (s.handsLeft <= 0) {
            // 骨头先生免死 → 0.4.0
            loseRun(s);
            return PlayResult.ok(score, type, events, false, true);
        }
        return PlayResult.ok(score, type, events, false, false);
    }

    private static void scoreOneCard(RunState s, ScoreContext ctx, Card card) {
        if (card.enh() == Data.Enhancement.STONE) ctx.addChips(50);
        else ctx.addChips(Data.rankChips(card.rank()));
        ctx.addChips(card.chipBonus());

        if (card.enh() == Data.Enhancement.BONUS) ctx.addChips(30);
        if (card.enh() == Data.Enhancement.MULT) ctx.addMult(4);
        if (card.enh() == Data.Enhancement.GLASS) ctx.xMult(2);
        if (card.enh() == Data.Enhancement.LUCKY) {
            Rng.Stream st = s.stream("lucky");
            double p5 = 1.0 / 5, p15 = 1.0 / 15;
            if (Boolean.TRUE.equals(s.flags.get("doubleProb"))) { p5 *= 2; p15 *= 2; }
            if (st.chance(p5)) { ctx.addMult(20); triggerLuckyCat(s); }
            if (st.chance(p15)) { ctx.dollars(20); triggerLuckyCat(s); }
        }

        if (card.edition() == Data.Edition.FOIL) ctx.addChips(50);
        if (card.edition() == Data.Edition.HOLO) ctx.addMult(10);
        if (card.edition() == Data.Edition.POLY) ctx.xMult(1.5);

        if (card.seal() == Data.Seal.GOLD) ctx.dollars(3);
    }

    private static void triggerLuckyCat(RunState s) {
        List<JokerInstance> snap = new ArrayList<>(s.jokers);
        for (JokerInstance j : snap) {
            if (!j.debuff) j.def.onLucky(s, j);
        }
    }

    /** 蓝图/头脑风暴复制解析：返回实际生效的小丑实例（0.1.0 无复制类，恒返回自身）。 */
    private static JokerInstance resolveCopy(List<JokerInstance> active, int ji) {
        JokerInstance j = active.get(ji);
        if (j.def.blueprint()) {
            JokerInstance right = ji < active.size() - 1 ? active.get(ji + 1) : null;
            if (right == null || right.def.blueprint() || right.def.brainstorm()) return null;
            return right;
        }
        if (j.def.brainstorm()) {
            JokerInstance first = active.get(0);
            if (first == null || first == j || first.def.blueprint() || first.def.brainstorm()) return null;
            return first;
        }
        return j;
    }

    private static void applyJokerScore(RunState s, ScoreContext ctx, JokerInstance joker, int idx, List<JokerInstance> active) {
        JokerInstance src = resolveCopy(active, idx);
        if (src == null) return;
        src.def.onScore(ctx);
        ctx.joker = src;
        if (joker.edition == Data.Edition.FOIL) ctx.addChips(50);
        if (joker.edition == Data.Edition.HOLO) ctx.addMult(10);
        if (joker.edition == Data.Edition.POLY) ctx.xMult(1.5);
    }

    // ================= 弃牌 =================

    public static PlayResult discard(RunState s, List<Integer> cardIds) {
        if (s.phase != Phase.ROUND) return PlayResult.err("当前不在回合中");
        if (s.discardsLeft <= 0) return PlayResult.err("没有剩余弃牌次数");
        if (cardIds == null || cardIds.size() < 1 || cardIds.size() > 5) return PlayResult.err("请选择 1-5 张牌");
        if (new HashSet<>(cardIds).size() != cardIds.size()) return PlayResult.err("不能重复选择同一张牌");

        List<Card> cards = new ArrayList<>();
        for (int id : cardIds) {
            Card c = findInHand(s, id);
            if (c == null) return PlayResult.err("无效的手牌");
            cards.add(c);
        }

        s.discardsLeft--;
        s.discardsUsedThisRound++;
        s.usedDiscardThisRound = true;

        s.hand.removeIf(c -> cardIds.contains(c.id()));
        for (Card c : cards) {
            c.setFacedown(false);
            // 紫色蜡封 → 塔罗牌（0.2.0）
            s.discardPile.add(c);
        }

        List<JokerInstance> snap = new ArrayList<>(s.jokers);
        for (JokerInstance j : snap) {
            if (!j.debuff && s.jokers.contains(j)) {
                j.def.onDiscard(s, cards, j);
            }
        }

        drawUpTo(s);
        return PlayResult.okDiscard();
    }

    // ================= 回合结束 / 推进 =================

    private static void endRound(RunState s, boolean wonRound) {
        if (!wonRound) { loseRun(s); return; }

        long gain = 0;
        List<String> detail = new ArrayList<>();

        // 盲注奖励金
        long reward = s.blindType.reward;
        if (s.mods.redStake && s.blindType == Data.BlindType.SMALL) reward = 0;
        if (s.mods.smallBigRewardHalf && s.blindType != Data.BlindType.BOSS) reward = (long) Math.ceil(reward / 2.0);
        if (s.mods.rewardMult != 0) reward *= s.mods.rewardMult;
        gain += reward;
        if (reward > 0) detail.add("盲注奖励 +$" + reward);

        // 剩余出牌
        long handPay = s.handsLeft;
        if (s.mods.minRewardMoney != 0 && s.money < s.mods.minRewardMoney) handPay = 0;
        if ("green".equals(s.deckKey)) {
            long g = 2L * s.handsLeft + s.discardsLeft;
            gain += g;
            if (g > 0) detail.add("绿色牌组 +$" + g);
        } else if (handPay > 0) {
            gain += handPay;
            detail.add("剩余出牌 +$" + handPay);
        }

        // 利息
        if (!s.mods.noInterest && s.money > 0) {
            int rate = s.mods.doubleInterest ? 2 : 1;
            long interest = Math.min(s.interestCap, (s.money / 5) * rate);
            if (interest > 0) { gain += interest; detail.add("利息 +$" + interest); }
        }

        // 黄金牌（手中）
        for (Card c : s.hand) {
            if (c.enh() == Data.Enhancement.GOLD && !c.debuff()) gain += 3;
        }
        // 蓝色蜡封 → 星球牌（0.2.0）

        // 小丑回合结束钩子
        List<JokerInstance> snap = new ArrayList<>(s.jokers);
        for (JokerInstance j : snap) {
            if (j.debuff || !s.jokers.contains(j)) continue;
            long g = j.def.onRoundEnd(s, j);
            if (g > 0) { gain += g; detail.add(j.def.displayName() + " +$" + g); }
        }

        // 租赁 / 易腐小丑（0.2.0 商店属性，0.1.0 无）

        s.money += gain;

        // 击败 Boss
        if (s.blindType == Data.BlindType.BOSS) {
            List<JokerInstance> bossSnap = new ArrayList<>(s.jokers);
            for (JokerInstance j : bossSnap) {
                if (!j.debuff && s.jokers.contains(j)) j.def.onBossDefeated(s, j);
            }
            // anaglyph 翻倍标签 → 0.5.0
            s.bossQueue.remove(0);
            if (!s.bossQueue.isEmpty()) {
                // 双 Boss 挑战 → 0.5.0
            }
        }

        s.msg("回合结束：" + String.join("；", detail));
        // 0.1.0：无商店，直接进入下一盲注选择
        proceedToNextBlind(s);
    }

    /** 0.1.0 胜出后推进：small→big→boss，boss 后进下一底注或胜利（无商店）。 */
    private static void proceedToNextBlind(RunState s) {
        if (s.blindType == Data.BlindType.SMALL) {
            s.nextBlind = "big";
            s.phase = Phase.BLIND_SELECT;
        } else if (s.blindType == Data.BlindType.BIG) {
            s.nextBlind = "boss";
            s.phase = Phase.BLIND_SELECT;
        } else {
            // 击败 Boss → 下一底注或胜利
            if (s.ante >= 8 && !s.endless) {
                s.phase = Phase.END;
                s.won = true;
                s.endlessPending = true;
                return;
            }
            s.ante++;
            startAnte(s);
        }
    }

    /** 进入无尽模式（通关后可选）。 */
    public static boolean continueEndless(RunState s) {
        if (!s.endlessPending) return false;
        s.endless = true;
        s.endlessPending = false;
        s.phase = Phase.BLIND_SELECT;
        s.ante++;
        startAnte(s);
        return true;
    }

    private static void loseRun(RunState s) {
        s.phase = Phase.END;
        s.lost = true;
        s.msg("本局结束：未能达到目标分数");
    }

    // ================= 辅助 =================

    private static Card findInHand(RunState s, int id) {
        for (Card c : s.hand) if (c.id() == id) return c;
        return null;
    }

    private static int intFlag(Map<String, Object> f, String key) {
        Object v = f.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    // ================= 出牌结果 =================

    public static final class PlayResult {
        public final boolean ok;
        public final String err;
        public final long score;
        public final Data.HandType type;
        public final List<String> events;
        public final boolean won;
        public final boolean lost;

        private PlayResult(boolean ok, String err, long score, Data.HandType type, List<String> events, boolean won, boolean lost) {
            this.ok = ok;
            this.err = err;
            this.score = score;
            this.type = type;
            this.events = events;
            this.won = won;
            this.lost = lost;
        }

        public static PlayResult err(String msg) {
            return new PlayResult(false, msg, 0, null, null, false, false);
        }

        public static PlayResult ok(long score, Data.HandType type, List<String> events, boolean won, boolean lost) {
            return new PlayResult(true, null, score, type, events, won, lost);
        }

        public static PlayResult okDiscard() {
            return new PlayResult(true, null, 0, null, new ArrayList<>(), false, false);
        }
    }
}
