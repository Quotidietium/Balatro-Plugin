package cn.quotidietium.balatro.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * UseTargets.parseAtIds 目标快照令牌解析测试（R225）。
 *
 * <p>令牌形态 {@code @id1,id2,...}：全息「确认使用」按钮携带选中牌卡 id（手牌从左到右
 * 序）。锁：合法形态与次序保持、空段/非数字/≤0/重复/超 5 张/超长段全部拒绝——
 * 任何放宽都会削弱命令层的目标侧 TOCTOU 防线。
 */
class UseTargetsTest {

    @Test
    void parsesSingleAndMultipleIdsInOrder() {
        assertEquals(List.of(17), UseTargets.parseAtIds("@17"));
        assertEquals(List.of(17, 23, 42), UseTargets.parseAtIds("@17,23,42"));
        assertEquals(List.of(1), UseTargets.parseAtIds("@1"));
        assertEquals(List.of(1), UseTargets.parseAtIds("@01")); // 前导零容忍（值语义相同）
        assertEquals(List.of(123456789), UseTargets.parseAtIds("@123456789")); // 9 位上限内
    }

    @Test
    void rejectsMalformedTokens() {
        assertNull(UseTargets.parseAtIds(null));
        assertNull(UseTargets.parseAtIds(""));
        assertNull(UseTargets.parseAtIds("@"));          // 无 id
        assertNull(UseTargets.parseAtIds("@,"));         // 空段
        assertNull(UseTargets.parseAtIds("@1,"));        // 尾空段
        assertNull(UseTargets.parseAtIds("@,1"));        // 首空段
        assertNull(UseTargets.parseAtIds("@1,,2"));      // 中间空段
        assertNull(UseTargets.parseAtIds("@a"));         // 非数字
        assertNull(UseTargets.parseAtIds("@1a"));
        assertNull(UseTargets.parseAtIds("@1@2"));
        assertNull(UseTargets.parseAtIds("@-1"));        // 负数
        assertNull(UseTargets.parseAtIds("@+1"));
        assertNull(UseTargets.parseAtIds("@0"));         // 卡 id 从 1 起
        assertNull(UseTargets.parseAtIds("@1,1"));       // 重复 id
        assertNull(UseTargets.parseAtIds("17,23"));      // 缺 @ 前缀
        assertNull(UseTargets.parseAtIds("@1,2,3,4,5,6")); // 超 5 张
        assertNull(UseTargets.parseAtIds("@1234567890"));  // 段超 9 位
    }
}
