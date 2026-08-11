package cn.quotidietium.balatro.engine;

import cn.quotidietium.balatro.engine.joker.BasicJoker;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 静态数据表完整性回归（轮次 R67）。
 *
 * <p>锁定全部静态数据表的大小，防止意外删减（删一个 Boss/Joker/消耗品/券等）。
 * {@code DataGoldenTest} 仅验字段值（golden data.txt 比对存在的条目），删条目不会失败；
 * 本测试补「数量」维度。各计数均经与 REF data.js 逐项比对确认（R9/R11/R66/R67）。
 */
class DataCompletenessTest {

    @Test
    void allStaticTableSizesMatchRef() {
        // 牌组 / 赌注 / 牌型 / 盲注类型
        assertEquals(15, Data.DECKS.size(), "牌组");
        assertEquals(8, Data.STAKES.size(), "赌注");
        assertEquals(13, Data.HandType.values().length, "牌型");
        assertEquals(3, Data.BlindType.values().length, "盲注类型(small/big/boss)");

        // Boss / 券 / 标签 / 挑战 / 补充包
        assertEquals(28, Data.Boss.values().length, "Boss 盲注");
        assertEquals(32, Data.VOUCHERS.size(), "优惠券(16 对)");
        assertEquals(24, Data.TAGS.size(), "标签");
        assertEquals(20, Data.CHALLENGES.size(), "挑战");
        assertEquals(13, Data.PACKS.size(), "补充包");

        // 消耗品（塔罗/星球/幻灵）
        assertEquals(22, Data.Tarot.values().length, "塔罗牌");
        assertEquals(12, Data.Planet.values().length, "星球牌(12 牌型，ROYAL 无专属星球)");
        assertEquals(18, Data.Spectral.values().length, "幻灵牌");

        // 增强 / 版本 / 蜡封
        assertEquals(8, Data.Enhancement.values().length, "增强");
        assertEquals(4, Data.Edition.values().length, "版本(foil/holo/poly/negative)");
        assertEquals(4, Data.Seal.values().length, "蜡封(gold/red/blue/purple)");

        // 小丑（注册表全量 = 150）
        assertEquals(150, JokerRegistry.allJokersOrdered().size(), "小丑");
    }

    @Test
    void jokerRegistryCoversAllBasicJokerConstants() {
        // BasicJoker 枚举常量数应 == 注册表数（全部注册，无遗漏）
        int basicCount = BasicJoker.values().length;
        assertEquals(basicCount, JokerRegistry.allJokersOrdered().size(),
                "注册表应覆盖全部 BasicJoker 枚举常量");
    }
}
