package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R211：32 优惠券 × 20 挑战**购买路径**交叉 × 新种子——每张券在轮换挑战的新种子局
 *（VXC-*）中经 Shop.buyVoucher 真实购买（赢盲进店→找到该券→购买）：
 * 禁入券（banVouchers）不应出现在店内；未禁入的券购买成功且推进回合约束不崩。
 * 修正落位断言沿用 R209 口径（挑战会覆盖基线——只断言「推进成功+状态合法+金钱下界」，
 * 逐券数值断言在无挑战闭包 R209 已锁，避免基线矩阵爆炸）。新种子第十三维。
 */
class VoucherXChallengeFreshTest {

    private static boolean playAny(RunState s) {
        if (s.handsLeft <= 0 || s.hand.isEmpty()) return false;
        int sz = s.hand.size();
        if (sz >= 5) {
            for (int st = 0; st + 5 <= sz; st++) {
                List<Integer> ids = new ArrayList<>();
                for (int i = st; i < st + 5; i++) ids.add(s.hand.get(i).id());
                if (Engine.playHand(s, ids).ok) return true;
            }
        }
        for (int n = 1; n <= Math.min(5, sz); n++) {
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < n; i++) ids.add(s.hand.get(i).id());
            if (Engine.playHand(s, ids).ok) return true;
        }
        return false;
    }

    @Test
    void vouchersAcrossChallengesBuyPathBehaves() {
        var challenges = Data.CHALLENGES;
        int idx = 0;
        int bought = 0;
        for (Data.Voucher v : Data.VOUCHERS) {
            Data.Challenge ch = challenges.get(idx % challenges.size());
            RunState s = Engine.createRun("red", 0, "VXC-" + (idx++) + "-" + v.key + "-" + ch.key(),
                    ch.key());
            // 赢首盲进店（真实路径）
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            s.roundScore = s.blindTarget;
            int guard = 0;
            while (s.phase == Phase.ROUND && guard++ < 25) {
                if (playAny(s)) break;
                if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else break;
            }
            if (s.phase != Phase.SHOP) continue; // 极限挑战赢不下首盲——本券跳过（非缺陷）

            boolean banned = s.mods.bannedVouchers.contains(v.key);
            boolean everInShop = false;
            // 最多逛 5 家店找目标券（每店随机 1-2 张券，32 券池下需多店）
            for (int shopVisit = 0; shopVisit < 5 && s.phase == Phase.SHOP; shopVisit++) {
                boolean inShop = false;
                int vi = -1;
                for (int i = 0; i < s.shop.vouchers.size(); i++) {
                    if (s.shop.vouchers.get(i).voucher.key.equals(v.key)) { inShop = true; vi = i; break; }
                }
                if (inShop) {
                    everInShop = true;
                    if (banned) {
                        assertTrue(false, "禁入券不应出现在店内（" + v.key + "@" + ch.key() + "）");
                    }
                    if (s.money >= s.shop.vouchers.get(vi).price) {
                        long m0 = s.money;
                        boolean ok = cn.quotidietium.balatro.engine.shop.Shop.buyVoucher(s, vi);
                        assertTrue(ok, "可负担券购买应成功（" + v.key + "@" + ch.key() + "）");
                        assertTrue(s.money < m0, "购买扣款（" + v.key + "）");
                        assertTrue(s.vouchers.contains(v.key), "券入 vouchers（" + v.key + "）");
                        bought++;
                    }
                    break;
                }
                // 未上架：推进到下一店（真实路径）
                if (!Engine.nextRound(s)) break;
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                s.roundScore = s.blindTarget;
                int guard2 = 0;
                while (s.phase == Phase.ROUND && guard2++ < 25) {
                    if (playAny(s)) break;
                    if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                        Engine.discard(s, List.of(s.hand.get(0).id()));
                    } else break;
                }
            }
            assertTrue(s.money >= -20, "金钱 ≥ -20（" + v.key + "@" + ch.key() + "）：" + s.money);
        }
        assertTrue(bought >= 5, "应有不少量券经真实购买路径入账（实际 " + bought + "）");
    }
}
