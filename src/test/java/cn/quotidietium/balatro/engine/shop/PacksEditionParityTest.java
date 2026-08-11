package cn.quotidietium.balatro.engine.shop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import org.junit.jupiter.api.Test;

/**
 * 补充包 standard 牌版本分布回归（对照 REF engine.js:1433）。
 *
 * <p>原版 {@code s.pick(["foil","holo","poly"])} 为均匀 1/3；Java 曾误用
 * {@code weightedEdition}（50/35/15），分布错误且破坏种子复现。轮次 14 修复。
 *
 * <p>版本仅在 {@code chance(0.2)} 命中时附加，故收集「有版本」的样本统计分布。
 */
class PacksEditionParityTest {

    @Test
    void standardPackEditionIsUniformNotWeighted() {
        int foil = 0, holo = 0, poly = 0;
        int withEdition = 0;
        int runs = 400;
        for (int i = 0; i < runs; i++) {
            RunState s = Engine.createRun("red", 0, "PKED" + i);
            // 找 standard 包；若无则跳过此种子
            Data.Pack stdPack = null;
            for (Data.Pack p : Data.PACKS) {
                if (p.type == Data.PackType.STANDARD) { stdPack = p; break; }
            }
            if (stdPack == null) continue;
            Packs.open(s, stdPack);
            for (var pc : s.pack.cards) {
                if (pc.kind.equals("playing") && pc.card.edition() != null) {
                    withEdition++;
                    switch (pc.card.edition()) {
                        case FOIL -> foil++;
                        case HOLO -> holo++;
                        case POLY -> poly++;
                    }
                }
            }
        }
        // 均匀 1/3：三档接近 withEdition/3。若误用 50/35/15，poly 占比仅 ~15%。
        // withEdition 通常 ~400*0.2*3 ≈ 240（每包 3 张各 0.2 概率）。
        // 宽松断言：每档 ≥ withEdition * 0.20（均匀时约 0.33，加权时 poly≈0.15）。
        assertTrue(withEdition >= 100, "应收集到足够带版本的样本，实际 " + withEdition);
        double threshold = withEdition * 0.20;
        assertTrue(foil >= threshold && holo >= threshold && poly >= threshold,
                "standard 包版本应为均匀 1/3 而非商店权重(50/35/15)："
                        + "foil=" + foil + " holo=" + holo + " poly=" + poly
                        + " (总带版本=" + withEdition + ", 阈值=" + threshold + ")");
    }
}
