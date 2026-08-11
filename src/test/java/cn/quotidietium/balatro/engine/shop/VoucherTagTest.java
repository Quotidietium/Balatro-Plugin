package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * voucher 标签生效回归（对齐真版 "Adds a Voucher to the next Shop. Can be stacked"
 * — https://balatrowiki.org/w/Voucher_Tag）。
 *
 * <p>0.3.9 前：extraVoucher 置位后 Shop 生成处无消费，标签完全无效。
 * 0.3.9：ShopData.voucher 改为 List，genShop 按 extraVoucher 计数追加额外券。
 */
class VoucherTagTest {

    @Test
    void noVoucherTagGivesExactlyOneBaseVoucherOrNone() {
        // 无标签时：若有可用券则 1 张，否则 0 张（取决于已拥有券数）
        RunState s = Engine.createRun("red", 0, "VTEST1");
        Shop.openShop(s);
        assertTrue(s.shop.vouchers.size() <= 1, "无标签时至多 1 张基础券");
    }

    @Test
    void oneVoucherTagAddsExtraVoucher() {
        // 模拟跳过获得 voucher 标签（直接置位 nextShop）
        RunState s = Engine.createRun("red", 0, "VTEST2");
        s.nextShop.put("extraVoucher", 1);
        Shop.openShop(s);
        // 基础 1 + 额外 1 = 2（前提是有足够可用券；开局无已拥有券，avail 充足）
        assertEquals(2, s.shop.vouchers.size(), "1 个 voucher 标签应追加 1 张额外券（共 2 张）");
        // 两张券应不同（不重复）
        assertTrue(s.shop.vouchers.get(0).voucher != s.shop.vouchers.get(1).voucher,
                "两张券不应相同");
    }

    @Test
    void stackedVoucherTagsAddMultiple() {
        RunState s = Engine.createRun("red", 0, "VTEST3");
        s.nextShop.put("extraVoucher", 3); // 3 个 voucher 标签叠加
        Shop.openShop(s);
        // 基础 1 + 额外 3 = 4（Data.VOUCHERS 有 32 个，avail 充足）
        assertEquals(4, s.shop.vouchers.size(), "3 个 voucher 标签应追加 3 张额外券（共 4 张）");
    }

    @Test
    void extraVoucherClearedAfterShop() {
        // genShop 消费后 extraVoucher 应清除（不影响下一店）
        RunState s = Engine.createRun("red", 0, "VTEST4");
        s.nextShop.put("extraVoucher", 2);
        Shop.openShop(s);
        assertEquals(null, s.nextShop.get("extraVoucher"), "genShop 后 extraVoucher 应清除");
    }
}
