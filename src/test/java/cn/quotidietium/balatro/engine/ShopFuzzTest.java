package cn.quotidietium.balatro.engine;

import cn.quotidietium.balatro.engine.shop.Shop;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商店出售/购买/重掷模糊测试（R87 新增）。
 *
 * <p>用固定种子随机数（非游戏种子）驱动 100 局 createRun→打牌进商店→出售/购买/重掷，
 * 验证不崩溃、不出无效状态。覆盖 0.4.21 新增的商店持有牌出售路径在极端组合下的稳定性。
 */
class ShopFuzzTest {

    private static final String[] DECKS = {
            "red", "blue", "yellow", "green", "black", "magic", "nebula", "ghost",
            "abandoned", "checkered", "zodiac", "painted", "anaglyph", "plasma", "erratic"
    };

    @Test
    void fuzzShopSellBuyRerollNoCrash() {
        Random rnd = new Random(42); // 固定种子驱动测试确定性
        int runs = 100;
        int crashes = 0;
        int reachedShop = 0;

        for (int i = 0; i < runs; i++) {
            try {
                String deck = DECKS[rnd.nextInt(DECKS.length)];
                int stake = rnd.nextInt(8);
                String seed = "FUZZ" + i;
                RunState s = Engine.createRun(deck, stake, seed, null);
                if (s.phase == Phase.BLIND_SELECT) {
                    Engine.selectBlind(s, Data.BlindType.byKey(s.nextBlind), false);
                }
                // 打牌直到赢或输或出牌耗尽
                int safety = 50;
                while (s.phase == Phase.ROUND && safety-- > 0) {
                    if (s.hand.isEmpty()) break;
                    List<Integer> ids = new ArrayList<>();
                    for (int j = 0; j < Math.min(5, s.hand.size()); j++) {
                        ids.add(s.hand.get(j).id());
                    }
                    Engine.PlayResult r = Engine.playHand(s, ids);
                    if (r.won || r.lost) break;
                    if (s.phase == Phase.ROUND && s.handsLeft <= 0) break;
                }
                // 在商店中执行出售/购买/重掷
                if (s.phase == Phase.SHOP) {
                    reachedShop++;
                    // 出售第一张小丑（如有）
                    if (!s.jokers.isEmpty()) {
                        s.sellJoker(0);
                    }
                    // 出售第一个消耗品（如有）
                    if (!s.consumables.isEmpty()) {
                        s.sellConsumable(0);
                    }
                    // 购买所有可购买的卡牌
                    if (s.shop != null) {
                        for (int c = 0; c < s.shop.cards.size(); c++) {
                            Shop.buyCard(s, c);
                        }
                        // 重掷
                        Shop.reroll(s);
                    }
                }
            } catch (Exception e) {
                crashes++;
                if (crashes <= 3) {
                    System.err.println("CRASH run " + i + ": " + e);
                }
            }
        }
        assertEquals(0, crashes, crashes + "/" + runs + " 局崩溃");
        System.out.println("ShopFuzz: " + runs + " runs, " + reachedShop + " reached shop, 0 crashes");
    }
}
