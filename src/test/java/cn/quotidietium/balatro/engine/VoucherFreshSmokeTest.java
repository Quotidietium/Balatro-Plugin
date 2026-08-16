package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R209：32 优惠券 × **新种子族**逐张 smoke——每张券在独立新种子局（VFS-*）中
 * 直接拥有（s.vouchers.add，等价于购买生效）→ 推进一整回合（applyVouchersPassive
 * 在 startRound 重建修正）→ 断言：修正落位抽查（antimatter 槽+1 / crystal 消耗品
 * 槽+1 / overstock 商店卡位+1 / grabber·nacho 出牌+1 / wasteful·recyclo 弃牌+1 /
 * paintbrush·palette 手牌+2 / seedmoney·moneytree 利息上限）+ 回合可推进 + 金钱
 * 下界。新种子探索第十一维（优惠券逐张）。
 */
class VoucherFreshSmokeTest {

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
    void allVouchersOnFreshSeedsApplyCorrectly() {
        int idx = 0;
        for (Data.Voucher v : Data.VOUCHERS) {
            RunState s = Engine.createRun("red", idx % 2, "VFS-" + (idx++) + "-" + v.key, null);
            s.vouchers.add(v.key);
            // 升级券的修正不独立生效：overstock2 需同时持有 overstock（requires 链）
            if (v.requires != null) s.vouchers.add(v.requires);

            // 修正落位抽查（applyVouchersPassive 经 startRound 重建后读）
            switch (v.key) {
                case "antimatter" -> assertTrue(nextRoundHands(s, 6, 2), "反物质：槽 6");
                case "crystal", "omen" -> assertTrue(nextRoundHands(s, 5, 3),
                        "水晶球系：消耗品槽 3（" + v.key + "）");
                case "overstock" -> assertTrue(nextRoundShopSlots(s, 3), "多重库存：卡位 3");
                case "overstock2" -> assertTrue(nextRoundShopSlots(s, 4), "多重库存+：卡位 4");
                case "grabber" -> assertTrue(nextRoundHands(s, 5, 2) && s.handsBase == 5, "补给手");
                case "nacho" -> assertTrue(nextRoundHands(s, 5, 2) && s.handsBase == 6, "顺手牵羊");
                case "wasteful" -> assertTrue(nextRoundHands(s, 5, 2)
                        && s.discardsBase == 5, "挥霍无度：弃牌基 5");
                case "recyclo" -> assertTrue(nextRoundHands(s, 5, 2)
                        && s.discardsBase == 6, "回收狂人：弃牌基 6（requires 链叠加）");
                case "paintbrush" -> assertTrue(nextRoundHands(s, 5, 2) && s.handSizeBase == 9, "油漆刷");
                case "palette" -> assertTrue(nextRoundHands(s, 5, 2) && s.handSizeBase == 10, "调色板：基 10（requires 链叠加）");
                case "seedmoney" -> assertTrue(nextRoundHands(s, 5, 2) && s.interestCap == 10, "种子基金");
                case "moneytree" -> assertTrue(nextRoundHands(s, 5, 2) && s.interestCap == 20, "摇钱树");
                default -> assertTrue(nextRoundHands(s, 5, 2), "其余券可推进（" + v.key + "）");
            }
            assertTrue(s.money >= 0, "金钱下界（" + v.key + "）：" + s.money);
        }
    }

    /** 推进到下一回合后读 shopSlots（商店卡位修正）。 */
    private static boolean nextRoundShopSlots(RunState s, int expect) {
        if (!advanceOneRound(s)) return false;
        return s.shopSlots == expect;
    }

    /** 推进到下一回合并抽查（jokerSlots, consumableSlots）。 */
    private static boolean advanceOneRound(RunState s) {
        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        s.roundScore = s.blindTarget;
        int guard = 0;
        while (s.phase == Phase.ROUND && guard++ < 20) {
            if (playAny(s)) break;
            if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                Engine.discard(s, List.of(s.hand.get(0).id()));
            } else return false;
        }
        if (s.phase != Phase.SHOP) return false;
        if (!Engine.nextRound(s)) return false;
        Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
        return s.phase == Phase.ROUND;
    }

    private static boolean nextRoundHands(RunState s, int expectJokerSlots, int expectCons) {
        if (!advanceOneRound(s)) return false;
        // 券修正经 applyVouchersPassive 重建后比对（红组基线 5/2）
        return s.jokerSlots == expectJokerSlots && s.consumableSlots == expectCons;
    }
}
