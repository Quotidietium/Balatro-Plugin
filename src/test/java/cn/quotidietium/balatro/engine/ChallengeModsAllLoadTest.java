package cn.quotidietium.balatro.engine;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全 20 挑战 mods 加载完整性回归（轮次 R66）。
 *
 * <p>风险：若 {@code Data.CHALLENGES} 某 key 与 {@code challengemods.txt} 键不符（拼写/大小写），
 * {@link ChallengeMods#applyTo} 会按「未知挑战」静默 return —— 该挑战无任何 mod 生效（功能不符），
 * 且现有测试仅 spot-check omelette，覆盖不到。
 *
 * <p>本测试对全 20 挑战逐一断言：① ChallengeMods.exists(key)（键可加载）；
 * ② createRun(challenge) 不崩溃且 challenge 字段已设置；
 * ③ 至少一个 mod 生效（与无挑战基线相比，mods 签名不同）——防静默无 mod。
 */
class ChallengeModsAllLoadTest {

    @Test
    void allChallengesLoadAndApplyMods() {
        List<String> noMod = new ArrayList<>();
        for (Data.Challenge c : Data.CHALLENGES) {
            String key = c.key();
            // ① 键必须可加载（防 Data.CHALLENGES ↔ challengemods.txt 键不符）
            assertTrue(ChallengeMods.exists(key), "挑战 " + key + " 应在 challengemods.txt 中可加载");

            // ② createRun 不崩溃，challenge 字段设置
            RunState st = Engine.createRun("red", 0, "CML" + key, key);
            assertEquals(key, st.challenge, "challenge 字段应为 " + key);

            // ③ 至少一个 mod 生效：与无挑战基线比 mods 签名
            if (!hasAnyMod(st)) {
                noMod.add(key);
            }
        }
        assertTrue(noMod.isEmpty(),
                "以下挑战未生效任何 mod（可能键不符或 mods 全空）：" + noMod);
        assertFalse(Data.CHALLENGES.isEmpty());
    }

    /**
     * 该 run 是否有任一挑战 mod 生效（与全是默认值的 Mods 相比）。
     * 覆盖全部 Mods 字段：任一非默认即视为 mod 已施加。
     */
    private static boolean hasAnyMod(RunState s) {
        Mods m = s.mods;
        // 布尔 mod
        if (m.noInterest || m.redStake || m.greenStake || m.blackStake || m.purpleStake
                || m.orangeStake || m.goldStake || m.plasma || m.spectralInShop
                || m.doubleInterest || m.freeReroll || m.allEternal || m.facesToStone
                || m.checkered || m.allStone || m.numbersToFaces || m.glassDouble
                || m.inflation || m.doubleBoss || m.must5 || m.noJokers
                || m.smallBigRewardHalf
                // R102 对齐真版的 4 个新挑战 mod
                || m.noBlindReward || m.noHandPay || m.faceDouble || m.xrayFacedown
                // R123 对齐真版的 13 个新挑战 mod
                || m.chipsCapByMoney || m.playedDebuff || m.redSealDeck || m.glassDeck
                || m.typecastTrigger || m.luxuryTax || m.inflationPerBuy || m.discardCost
                || m.smallBigNoReward || m.rankMin > 0
                || !m.bannedTarots.isEmpty() || !m.bannedSpectrals.isEmpty()
                || !m.bannedPacks.isEmpty() || !m.bannedTags.isEmpty()
                || !m.bannedBosses.isEmpty() || !m.bannedVouchers.isEmpty()
                || !m.bannedJokers.isEmpty()) return true;
        // 数值 mod（默认 0）
        if (m.handsSet != 0 || m.handSize != 0 || m.blindMult != 0 || m.jokerTax != 0
                || m.rewardMult != 0 || m.shopDiscount != 0 || m.minRewardMoney != 0
                || m.discardsSet != 0 || m.jokerSlotsSet != 0) return true; // R123
        // jokers/money mod（blastoff/omelette：开局即改 jokers 数或 money）
        // red 基线 money=4；blastoff money=0；omelette 5 蛋；typecast/monolith 改牌组（非 mods 字段，但 buildFullDeck 读 mods.checkered/allStone）
        if (!s.jokers.isEmpty()) return true;       // omelette(5蛋)/blastoff(rocket)
        if (s.money != 4) return true;              // blastoff($0)、yellow 不适用（非挑战）
        // typecast/monolith/medusa/bram：mods 布尔已在上面覆盖
        return false;
    }
}
