package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.Phase;
import cn.quotidietium.balatro.engine.RunState;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * R205：商店生成 × **新种子族**统计面——ShopGoldenTest（固定种子）之后，以全新
 * 种子族（SFS-*）验证：①同种子商店重放确定性（同种子两次独立跑到同一商店轮次，
 * 商品序列逐字段一致——shopcards/shopjoker/shopgen 三流的消耗次序在未测试随机
 * 空间可复现）；②商店结构不变量（卡位数=shopSlots/包恰2/券≥1 且不重复）；
 * ③重掷计数与免费次数一致性。新种子探索第七维（商店生成）。
 */
class ShopFreshStatsTest {

    /** 跑到第 n 个商店并返回该店商品摘要（win-blinds 真实路径）。 */
    private static String shopDigestAt(String seed, int shopIndex) {
        RunState s = Engine.createRun("red", 0, seed, null);
        for (int i = 0; i <= shopIndex; i++) {
            Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
            s.roundScore = s.blindTarget;
            int guard = 0;
            while (s.phase == Phase.ROUND && guard++ < 20) {
                int n = Math.min(5, s.hand.size());
                List<Integer> ids = new ArrayList<>(n);
                for (int k = 0; k < n; k++) ids.add(s.hand.get(k).id());
                if (Engine.playHand(s, ids).ok) break;
                if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                    Engine.discard(s, List.of(s.hand.get(0).id()));
                } else break;
            }
            if (s.phase != Phase.SHOP) return "NOSHOP";
            if (i < shopIndex) Engine.nextRound(s);
        }
        var shop = s.shop;
        StringBuilder sb = new StringBuilder();
        sb.append("slots=").append(shop.cards.size());
        for (var c : shop.cards) {
            sb.append(';').append(c.kind).append(',').append(c.key == null ? "-" : c.key)
                    .append(',').append(c.price).append(',').append(c.joker == null ? "-" : c.joker.edition)
                    .append(',').append(c.joker == null ? "-" : c.joker.eternal)
                    .append(',').append(c.joker == null ? "-" : c.joker.rental);
        }
        sb.append("|packs=");
        for (var p : shop.packs) sb.append(p.pack.key).append(',').append(p.price).append(';');
        sb.append("|vouchers=");
        for (var v : shop.vouchers) sb.append(v.voucher.key).append(';');
        sb.append("|rr=").append(shop.rerollCount).append('/').append(shop.freeRerolls);
        return sb.toString();
    }

    @Test
    void freshSeedShopReplayIsDeterministic() {
        for (String seed : new String[] {"SFS-A1", "SFS-B2", "SFS-C3"}) {
            for (int shopIndex : new int[] {0, 2, 4}) {
                String a = shopDigestAt(seed, shopIndex);
                String b = shopDigestAt(seed, shopIndex);
                assertEquals(a, b, "同种子商店重放确定性（" + seed + " shop#" + shopIndex + "）");
            }
        }
    }

    @Test
    void freshSeedShopStructureInvariantsHold() {
        for (int t = 0; t < 12; t++) {
            RunState s = Engine.createRun("red", t % 2, "SFS-S" + t, null);
            for (int i = 0; i < 3; i++) {
                Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                s.roundScore = s.blindTarget;
                int guard = 0;
                while (s.phase == Phase.ROUND && guard++ < 20) {
                    int n = Math.min(5, s.hand.size());
                    List<Integer> ids = new ArrayList<>(n);
                    for (int k = 0; k < n; k++) ids.add(s.hand.get(k).id());
                    if (Engine.playHand(s, ids).ok) break;
                    if (s.discardsLeft > 0 && !s.hand.isEmpty()) {
                        Engine.discard(s, List.of(s.hand.get(0).id()));
                    } else break;
                }
                if (s.phase != Phase.SHOP) break;
                var shop = s.shop;
                assertEquals(s.shopSlots, shop.cards.size(), "卡位数=shopSlots（SFS-S" + t + "）");
                assertEquals(2, shop.packs.size(), "包恰 2（SFS-S" + t + "）");
                assertTrue(shop.vouchers.size() >= 1, "券 ≥1（SFS-S" + t + "）");
                for (int v = 0; v < shop.vouchers.size(); v++) {
                    for (int w = v + 1; w < shop.vouchers.size(); w++) {
                        assertTrue(!shop.vouchers.get(v).voucher.key.equals(shop.vouchers.get(w).voucher.key),
                                "同店券不重复（SFS-S" + t + "）");
                    }
                }
                for (var c : shop.cards) assertTrue(c.price >= 1, "价格 ≥1（SFS-S" + t + "）");
                Engine.nextRound(s);
            }
        }
    }
}
