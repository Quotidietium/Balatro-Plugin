package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import cn.quotidietium.balatro.engine.shop.Packs;
import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R138：多会话交错确定性——静态共享状态（Data 池/稀有度分桶/HandEval 草稿区/消耗品
 * 产出池等全部进程级共享物）下，多会话在同一线程交错推进时，每个会话的演化必须与
 * 「单会话独跑」逐位一致。
 *
 * <p>这是 R137 静态池「零跨会话污染」结论的行为学证明：任何被引入的静态可变状态
 * （如误将可变列表落池、草稿区残留、缓存未按会话隔离）都会使交错运行的分岔点
 * 与独跑不同，本测试即失败。动作脚本是会话状态的纯函数 + 全局 tick（同一会话在
 * 交错/独跑两种模式下收到相同的 tick 序列），故两条路径的动作序列必然同源，
 * 摘要差异只能来自跨会话污染。
 */
class MultiSessionInterleaveTest {

    private static final int TOTAL_TICKS = 660; // 三会话各 ~220 步（覆盖多底注/商店/补充包）

    /** 一个会话的动作步：纯函数于自身状态 + 全局 tick。 */
    private static void step(RunState s, int tick) {
        switch (s.phase) {
            case BLIND_SELECT -> Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            case ROUND -> {
                if (tick % 3 == 0 && s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else if (!s.hand.isEmpty()) {
                    int n = Math.min(4, s.hand.size());
                    List<Integer> ids = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
                    Engine.playHand(s, ids);
                }
                // 有消耗品就用第 1 个（覆盖 use 一次性流 + 产出池跨会话共享）
                if (!s.consumables.isEmpty() && tick % 5 == 0) {
                    cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, null);
                }
            }
            case SHOP -> {
                if (tick % 4 == 1) Shop.reroll(s);
                if (s.shop != null) { // 依价买最便宜的可购卡（尝试失败自动跳过——资金/槽位守卫）
                    for (int i = 0; i < s.shop.cards.size(); i++) {
                        if (Shop.buyCard(s, i)) break;
                    }
                    for (int i = 0; i < s.shop.packs.size(); i++) {
                        if (Shop.buyPack(s, i)) break;
                    }
                }
                Engine.nextRound(s);
            }
            case PACK -> {
                if (s.pack != null) {
                    boolean took = false;
                    for (int i = 0; i < s.pack.cards.size(); i++) {
                        if (Packs.pick(s, i)) { took = true; break; }
                    }
                    if (!took) Packs.skip(s);
                }
            }
            case END -> { /* 终局：无动作 */ }
        }
    }

    /** 每步后的状态摘要（确定性内容：无时间戳/无哈希序）。 */
    private static String digest(RunState s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.phase).append('|').append(s.ante).append('|').append(s.money)
                .append('|').append(s.roundScore).append('|').append(s.roundCount)
                .append('|').append(s.handSizeBase).append('|').append(s.handSizePerm)
                .append('|').append(s.grosDead).append('|').append(s.useSeq).append('|').append(s.packSeq);
        sb.append("|J:");
        for (var j : s.jokers) {
            sb.append(j.def.key()).append(',').append(j.edition).append(',').append(j.extra).append(';');
        }
        sb.append("|L:").append(s.handLevels);
        sb.append("|H:");
        for (Card c : s.hand) sb.append(c.id()).append(':').append(c.rank()).append(c.suit())
                .append(c.enh()).append(c.seal()).append(',');
        // R160 oracle 完备性加固：原摘要缺 vouchers/tags/consumables/牌堆序/进度面——
        // 若静态污染只落在这类字段上，旧摘要会漏检。全部补入（顺序敏感，逐 id 序列）。
        sb.append("|V:").append(s.vouchers);
        sb.append("|T:").append(s.tags);
        sb.append("|C:");
        for (var c : s.consumables) sb.append(c.kind).append(':').append(c.key).append(':').append(c.edition).append(',');
        sb.append("|D:");
        for (Card c : s.drawPile) sb.append(c.id()).append(',');
        sb.append("|X:");
        for (Card c : s.discardPile) sb.append(c.id()).append(',');
        sb.append("|P:").append(new java.util.TreeSet<>(s.playedThisAnte));
        sb.append("|PC:").append(s.handPlayedCount);
        sb.append("|UP:").append(s.usedPlanets);
        sb.append("|B:").append(s.bossQueue).append('/').append(s.blindType);
        sb.append("|NS:").append(s.nextShop);
        sb.append("|M:").append(String.join("␟", s.drainMessages()));
        return sb.toString();
    }

    @Test
    void interleavedSessionsMatchSoloReplay() {
        String[] decks = {"red", "green", "plasma"};
        String[] seeds = {"MSECA", "MSECB", "MSECC"};
        String challenge = Data.CHALLENGES.get(0).key();

        // 交错运行：3 会话轮转（第 3 个带挑战——禁入池/rank 带覆盖不同静态分支）
        RunState[] inter = new RunState[3];
        for (int i = 0; i < 3; i++) {
            inter[i] = Engine.createRun(decks[i], i % 3, seeds[i], i == 2 ? challenge : null);
        }
        List<String>[] interDigests = new List[3];
        for (int i = 0; i < 3; i++) interDigests[i] = new ArrayList<>();
        for (int tick = 0; tick < TOTAL_TICKS; tick++) {
            int i = tick % 3;
            step(inter[i], tick);
            interDigests[i].add(digest(inter[i]));
        }

        // 独跑重放：每会话单独新建，仅在属于它的 tick 上推进（tick 序列与交错运行完全相同）
        for (int i = 0; i < 3; i++) {
            RunState solo = Engine.createRun(decks[i], i % 3, seeds[i], i == 2 ? challenge : null);
            for (int tick = i; tick < TOTAL_TICKS; tick += 3) {
                step(solo, tick);
                String expect = digest(solo);
                String actual = interDigests[i].get(tick / 3);
                assertEquals(expect, actual,
                        "会话 " + i + "（" + decks[i] + "）在 tick " + tick + " 分岔——存在跨会话状态污染");
            }
        }
    }

    /**
     * R179：五会话扩展变体——「多用户高频率」目标的更强隔离证明。
     * 5 会话（red/green/plasma/black/yellow × 赌注 0-4 × 1 个挑战）×1000 tick 轮转，
     * 每会话 ~200 步（覆盖数个底注）；独跑重放逐 tick 比对同一 R160 完备摘要。
     */
    @Test
    void fiveSessionInterleaveExtended() {
        final int N = 5;
        final int TICKS = 1000;
        String[] decks = {"red", "green", "plasma", "black", "yellow"};
        String[] seeds = {"MSX1", "MSX2", "MSX3", "MSX4", "MSX5"};
        String challenge = Data.CHALLENGES.get(3).key(); // 与三会话变体不同挑战

        RunState[] inter = new RunState[N];
        for (int i = 0; i < N; i++) {
            inter[i] = Engine.createRun(decks[i], i, seeds[i], i == 1 ? challenge : null);
        }
        List<List<String>> digests = new ArrayList<>();
        for (int i = 0; i < N; i++) digests.add(new ArrayList<>());
        for (int tick = 0; tick < TICKS; tick++) {
            int i = tick % N;
            step(inter[i], tick);
            digests.get(i).add(digest(inter[i]));
        }
        for (int i = 0; i < N; i++) {
            RunState solo = Engine.createRun(decks[i], i, seeds[i], i == 1 ? challenge : null);
            for (int tick = i, k = 0; tick < TICKS; tick += N, k++) {
                step(solo, tick);
                assertEquals(digest(solo), digests.get(i).get(k),
                        "五会话变体：会话 " + i + "（" + decks[i] + "）在 tick " + tick + " 分岔");
            }
        }
    }
}
