package cn.quotidietium.balatro.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 13 挑战真版对齐回归（R123，第 53 处修复族；规则源 balatrowiki.org Challenge Decks）。
 * 启动态 + 关键行为断言；goldens（challenge/data/challengemods）已同步锁定 startup 快照。
 */
class RealChallengeSpecTest {

    private static RunState start(String key, String seed) {
        RunState s = Engine.createRun("red", 0, seed, key);
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        return s;
    }

    @Test
    void rich() {
        RunState s = Engine.createRun("red", 0, "RC1", "rich");
        assertEquals(100, s.money, "$100 开局");
        assertTrue(s.vouchers.contains("seedmoney") && s.vouchers.contains("moneytree"), "双券持有");
        assertTrue(s.mods.chipsCapByMoney);
    }

    @Test
    void knife() {
        RunState s = Engine.createRun("red", 0, "RC2", "knife");
        assertEquals(1, s.jokers.size());
        assertEquals("dagger", s.jokers.get(0).def.key());
        assertTrue(s.jokers.get(0).eternal, "仪式匕首永恒（左位=开局首位）");
    }

    @Test
    void luxury() {
        RunState s = Engine.createRun("red", 0, "RC3", "luxury");
        assertEquals(10, 8 + s.mods.handSize, "基础手牌上限 10（8+2）");
        s.money = 25;
        Engine.selectBlind(s, Data.BlindType.SMALL, false);
        assertEquals(5, s.handSizeRound, "$25 → 上限 10-5");
    }

    @Test
    void nonperish() {
        RunState s = Engine.createRun("red", 0, "RC4", "nonperish");
        assertTrue(s.mods.allEternal);
        assertEquals(11, s.mods.bannedJokers.size(), "11 个衰减/功能型小丑禁入");
        assertTrue(s.mods.bannedBosses.contains("leaf"));
    }

    @Test
    void medusa() {
        RunState s = Engine.createRun("red", 0, "RC5", "medusa");
        assertTrue(s.mods.facesToStone);
        assertEquals(1, s.jokers.size());
        assertEquals("marble", s.jokers.get(0).def.key());
        assertTrue(s.jokers.get(0).eternal);
    }

    @Test
    void doubleChallenge() {
        RunState s = start("double", "RC6");
        for (Card c : s.fullDeck) assertEquals(Data.Seal.RED, c.seal(), "全牌组红蜡封");
        assertTrue(s.mods.playedDebuff);
        // 行为：打一张 → 该牌入弃牌堆时已 debuff
        Card played = s.hand.get(0);
        Engine.playHand(s, List.of(played.id()));
        assertTrue(played.debuff(), "计分后的牌失效");
    }

    @Test
    void typecast() {
        RunState s = Engine.createRun("red", 0, "RC7", "typecast");
        s.jokers.add(cn.quotidietium.balatro.engine.joker.JokerRegistry.create("joker"));
        assertTrue(s.mods.typecastTrigger && s.mods.bannedBosses.contains("leaf"));
        s.ante = 4;
        s.nextBlind = "boss";
        assertTrue(Engine.selectBlind(s, Data.BlindType.BOSS, false));
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.jokers.stream().allMatch(j -> j.eternal), "击败底注 4 Boss 后全员永恒");
        assertEquals(0, s.jokerSlots, "小丑槽归零");
    }

    @Test
    void inflationChallenge() {
        RunState s = start("inflation", "RC8");
        assertEquals(1, s.jokers.size());
        assertEquals("creditcard", s.jokers.get(0).def.key(), "开局信用卡（可 -$20 负债购物）");
        assertTrue(s.mods.inflationPerBuy);
        // 行为：赢盲进商店，购买一次 → 计数 +1
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertTrue(s.phase == Phase.SHOP);
        s.money = 999;
        for (int i = 0; i < s.shop.cards.size(); i++) {
            if (Shop_buy(s, i)) break;
        }
        assertEquals(1, s.inflation, "每次购买 +$1");
    }

    private static boolean Shop_buy(RunState s, int i) {
        return cn.quotidietium.balatro.engine.shop.Shop.buyCard(s, i);
    }

    @Test
    void bram() {
        RunState s = Engine.createRun("red", 0, "RC9", "bram");
        assertTrue(s.mods.noJokers);
        assertEquals(1, s.jokers.size());
        assertEquals("vampire", s.jokers.get(0).def.key());
        assertTrue(s.jokers.get(0).eternal, "永恒吸血鬼");
        assertEquals(2, s.consumables.size(), "皇帝+女皇");
        assertTrue(s.vouchers.contains("magictrick") && s.vouchers.contains("illusion"));
    }

    @Test
    void fragile() {
        RunState s = Engine.createRun("red", 0, "RC10", "fragile");
        assertEquals(2, s.jokers.size());
        assertTrue(s.jokers.get(0).eternal && s.jokers.get(0).edition == Data.Edition.NEGATIVE
                && s.jokers.get(1).eternal && s.jokers.get(1).edition == Data.Edition.NEGATIVE,
                "两张永恒负片「全是 6！」");
        assertEquals("oops", s.jokers.get(0).def.key());
        for (Card c : s.fullDeck) assertEquals(Data.Enhancement.GLASS, c.enh(), "全玻璃牌组");
        assertEquals(4, s.mods.bannedJokers.size());
        assertEquals(7, s.mods.bannedTarots.size());
        assertEquals(3, s.mods.bannedSpectrals.size());
        assertEquals(2, s.mods.bannedVouchers.size());
        assertEquals(3, s.mods.bannedPacks.size());
        assertEquals(1, s.mods.bannedTags.size());
    }

    @Test
    void monolith() {
        RunState s = Engine.createRun("red", 0, "RC11", "monolith");
        assertEquals(2, s.jokers.size());
        assertEquals("obelisk", s.jokers.get(0).def.key());
        assertTrue(s.jokers.get(0).eternal);
        assertEquals("marble", s.jokers.get(1).def.key());
        assertTrue(s.jokers.get(1).eternal && s.jokers.get(1).edition == Data.Edition.NEGATIVE);
        assertEquals(52, s.fullDeck.size(), "标准牌组（非全石头）");
        int stones = 0;
        for (Card c : s.fullDeck) if (c.isStone()) stones++;
        assertEquals(0, stones);
    }

    @Test
    void blastoff() {
        RunState s = start("blastoff", "RC12");
        assertEquals(2, s.handsLeft, "2 出牌");
        assertEquals(2, s.discardsLeft, "2 弃牌");
        assertEquals(2, s.jokerSpace(), "4 槽 - 2 永恒小丑");
        assertTrue(s.jokers.get(0).eternal && s.jokers.get(1).eternal, "星座/火箭永恒");
        assertTrue(s.vouchers.contains("planetm") && s.vouchers.contains("planett"));
        assertTrue(s.mods.bannedVouchers.contains("grabber") && s.mods.bannedJokers.contains("burglar"));
    }

    @Test
    void fivecard() {
        RunState s = start("fivecard", "RC13");
        assertEquals(6, s.discardsLeft);
        assertEquals(5, s.handSizeRound);
        assertEquals(5, s.jokerSpace(), "7 槽 - 2 开局小丑");
        assertFalse(s.jokers.get(0).eternal, "老千非永恒");
        assertEquals(3, s.mods.bannedJokers.size());
    }

    @Test
    void golden() {
        RunState s = start("golden", "RC14");
        assertEquals(10, s.money, "$10 开局");
        assertEquals(1, s.handsLeft);
        assertEquals(6, s.discardsLeft);
        assertEquals("creditcard", s.jokers.get(0).def.key());
        // 行为：弃牌花 $1（信用卡允许负）
        long before = s.money;
        Engine.discard(s, List.of(s.hand.get(0).id()));
        assertEquals(before - 1, s.money, "弃牌计费 $1");
    }

    @Test
    void cruelty() {
        RunState s = start("cruelty", "RC15");
        assertEquals(3, s.jokerSpace(), "仅 3 小丑槽");
        assertTrue(s.mods.smallBigNoReward);
        long before = s.money;
        s.handsLeft = 0; // 隔离剩余出牌金（真版残酷保留出牌金）
        s.roundScore = s.blindTarget;
        Engine.playHand(s, List.of(s.hand.get(0).id()));
        assertEquals(before, s.money, "小盲无奖励金（剩余出牌金已隔离）");
    }

    @Test
    void jokerless() {
        RunState s = Engine.createRun("red", 0, "RC16", "jokerless");
        Engine.selectBlind(s, Data.BlindType.SMALL, false); // startRound 应用槽位覆盖
        assertEquals(0, s.jokerSpace(), "0 小丑槽");
        assertTrue(s.mods.noJokers);
        assertEquals(1, s.mods.bannedTarots.size());
        assertEquals(2, s.mods.bannedSpectrals.size());
        assertEquals(8, s.mods.bannedTags.size());
        assertEquals(3, s.mods.bannedBosses.size());
        assertEquals(1, s.mods.bannedVouchers.size());
        assertEquals(3, s.mods.bannedPacks.size());
    }
}
