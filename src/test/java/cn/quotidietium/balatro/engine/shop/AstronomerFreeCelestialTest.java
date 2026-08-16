package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * R142：天文学家小丑（astronomer）的完整生效面——星球牌免费（flags 路径，R130 已通）
 * 与**天体包免费**（R142 修复：原实现误查 vouchers 集合，券清单里根本没有该小丑 key，
 * 半个效果自 R130 落地即死亡——desc「商店与天体包中的星球牌免费」与实现不符）。
 *
 * <p>多种子探测：商店包从 12 种非幻灵包随机取 2（天体 2/12），循环开店直到出现
 * 天体包并断言其价格为 0（持有天文学家）/大于 0（无天文学家的阴性对照）。
 */
class AstronomerFreeCelestialTest {

    @Test
    void astronomerMakesCelestialPacksFree() {
        boolean found = false;
        for (int seed = 0; seed < 120 && !found; seed++) {
            RunState s = Engine.createRun("red", 0, "ASTRO" + seed, null);
            assertTrue(s.gainJoker("astronomer", null), "天文学家应可入列");
            Shop.openShop(s);
            for (var pi : s.shop.packs) {
                if (pi.pack.type == Data.PackType.CELESTIAL) {
                    assertTrue(pi.price == 0,
                            "R142 修复点：天文学家持有下天体包必须免费（seed=" + seed + "）");
                    found = true;
                }
            }
        }
        assertTrue(found, "120 个种子内应至少出现一个天体包");
    }

    @Test
    void celestialPacksStillPricedWithoutAstronomer() {
        boolean found = false;
        for (int seed = 0; seed < 120 && !found; seed++) {
            RunState s = Engine.createRun("red", 0, "NOASTRO" + seed, null);
            Shop.openShop(s);
            for (var pi : s.shop.packs) {
                if (pi.pack.type == Data.PackType.CELESTIAL) {
                    assertTrue(pi.price > 0, "无天文学家时天体包照常计价（seed=" + seed + "）");
                    found = true;
                }
            }
        }
        assertTrue(found, "120 个种子内应至少出现一个天体包");
    }
}
