package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 用户种子输入校验（客户端输入不可信）：长度 1~32、仅字母/数字/_/-。
 */
class RngSeedValidationTest {

    @Test
    void validSeedsAccepted() {
        assertTrue(Rng.isValidSeed("ABC123"));
        assertTrue(Rng.isValidSeed("a"));
        assertTrue(Rng.isValidSeed("seed_with-mix_01"));
        assertTrue(Rng.isValidSeed("A".repeat(32)));
        // 自动生成的种子必须合法
        assertTrue(Rng.isValidSeed(Rng.randomSeedString()));
    }

    @Test
    void invalidSeedsRejected() {
        assertFalse(Rng.isValidSeed(null));
        assertFalse(Rng.isValidSeed(""));
        assertFalse(Rng.isValidSeed("A".repeat(33)), "超长拒绝");
        assertFalse(Rng.isValidSeed("a|b"), "分隔符拒绝（破坏统计文件格式）");
        assertFalse(Rng.isValidSeed("a b"), "空格拒绝");
        assertFalse(Rng.isValidSeed("种子"), "非 ASCII 拒绝");
        assertFalse(Rng.isValidSeed("a\nb"), "控制字符拒绝");
        assertFalse(Rng.isValidSeed("§c hacked"), "颜色码注入拒绝");
        assertFalse(Rng.isValidSeed("<script>"), "特殊字符拒绝");
    }
}
