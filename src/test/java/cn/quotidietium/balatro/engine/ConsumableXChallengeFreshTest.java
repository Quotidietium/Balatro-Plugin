package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R212：52 消耗品 × 20 挑战**禁入交叉** × 新种子——每张消耗品在轮换挑战的新种子局
 *（CXC-*）中注入使用（与 R204 同款自适应目标数 + 前置喂给）：断言效果路径不崩 +
 * use ok + 金钱下界 + 牌状态合法。挑战的 bannedTarots/bannedSpectrals 是池过滤器
 *（商店/随机发放不产出，R210 同构语义）——直接注入使用不检查禁入，故此交叉面
 * 验证的是「挑战 mods 之下效果路径的正确性」。新种子第十四维。
 */
class ConsumableXChallengeFreshTest {

    @Test
    void allConsumablesAcrossChallengesApplyCleanly() {
        List<String[]> all = new ArrayList<>();
        for (Data.Tarot t : Data.TAROTS) all.add(new String[]{"tarot", t.key});
        for (Data.Planet p : Data.PLANETS) all.add(new String[]{"planet", p.key});
        for (Data.Spectral sp : Data.SPECTRALS) all.add(new String[]{"spectral", sp.key});
        assertTrue(all.size() == 52);

        var challenges = Data.CHALLENGES;
        int idx = 0;
        for (String[] c : all) {
            Data.Challenge ch = challenges.get(idx % challenges.size());
            RunState s = Engine.createRun("red", idx % 2, "CXC-" + (idx++) + "-" + c[1] + "-" + ch.key(),
                    ch.key());
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            // 前置喂给（同 R204）
            if (c[1].equals("fool")) {
                s.consumables.clear();
                s.consumables.add(new Consumable("planet", "mercury"));
                cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, List.of());
            }
            if (c[1].equals("wheel") || c[1].equals("ectoplasm") || c[1].equals("wraith")
                    || c[1].equals("hex") || c[1].equals("ankh")) {
                s.jokers.clear();
                s.gainJoker("joker", null);
            }
            // judgement/soul 获得小丑需空槽——omelette 开局 5 蛋占满槽，先腾一格
            if (c[1].equals("judgement") || c[1].equals("soul")) {
                while (s.jokerSpace() <= 0 && !s.jokers.isEmpty()) s.jokers.remove(0);
            }
            s.consumables.clear();
            s.consumables.add(new Consumable(c[0], c[1]));
            List<Integer> targets = new ArrayList<>();
            for (int i = 0; i < Math.min(2, s.hand.size()); i++) targets.add(s.hand.get(i).id());
            var r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, targets);
            if (!r.ok && targets.size() > 1) {
                r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, targets.subList(0, 1));
            }
            if (!r.ok && !targets.isEmpty()) {
                r = cn.quotidietium.balatro.engine.consumable.Consumables.use(s, 0, List.of());
            }
            assertTrue(r.ok, c[0] + ":" + c[1] + "@" + ch.key() + " 应使用成功：" + r.err);
            assertTrue(s.money >= -20, "金钱 ≥ -20（" + c[1] + "@" + ch.key() + "）：" + s.money);
            for (Card h : s.hand) {
                assertTrue(h.rank() == 0 || (h.rank() >= 2 && h.rank() <= 14),
                        "rank 合法（" + c[1] + "@" + ch.key() + "）：" + h.rank());
            }
        }
    }
}
