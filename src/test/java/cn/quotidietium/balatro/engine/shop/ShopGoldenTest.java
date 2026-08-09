package cn.quotidietium.balatro.engine.shop;

import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Engine;
import cn.quotidietium.balatro.engine.RunState;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 商店生成黄金用例：读取 {@code golden/shop.txt}（原版 openShop 产出），
 * 用本插件 {@link Shop#openShop} 生成并逐项断言商品/补充包/优惠券/免费重掷一致。
 */
class ShopGoldenTest {

    @Test
    void shopMatchesOriginal() throws IOException {
        List<String> lines = readAll("/golden/shop.txt");
        int i = 0;
        while (i < lines.size()) {
            String header = lines.get(i++);
            // SHOP <seed>
            String seed = header.split(" ")[1];
            RunState st = Engine.createRun("red", 0, seed);
            Shop.openShop(st);
            Shop.ShopData shop = st.shop;

            String line;
            int cardIdx = 0;
            int packIdx = 0;
            while (!(line = lines.get(i++)).equals("ENDSHOP")) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "CARD" -> {
                        // CARD <i> <kind> <key> <price>[|edition][|eternal]
                        Shop.CardItem c = shop.cards.get(cardIdx++);
                        assertEquals(p[2], c.kind, seed + " card kind");
                        String expKey = p[3];
                        String actKey;
                        if (c.kind.equals("joker")) actKey = c.joker.def.key();
                        else if (c.kind.equals("playing")) actKey = "play:" + c.card.rank() + "." + c.card.suit();
                        else actKey = c.key;
                        assertEquals(expKey, actKey, seed + " card key");
                        assertEquals(Long.parseLong(p[4]), c.price, seed + " card price");
                    }
                    case "PACK" -> {
                        Shop.PackItem pk = shop.packs.get(packIdx++);
                        assertEquals(p[2], pk.pack.key, seed + " pack key");
                        assertEquals(Long.parseLong(p[3]), pk.price, seed + " pack price");
                    }
                    case "VOUCHER" -> {
                        String expKey = p[1];
                        if (expKey.equals("-")) {
                            assertEquals(null, shop.voucher, seed + " voucher none");
                        } else {
                            assertEquals(expKey, shop.voucher.voucher.key, seed + " voucher key");
                            assertEquals(Long.parseLong(p[2]), shop.voucher.price, seed + " voucher price");
                        }
                    }
                    case "FREEREROLL" -> assertEquals(Integer.parseInt(p[1]), shop.freeRerolls, seed + " freeRerolls");
                    default -> { /* SHOP header already consumed */ }
                }
            }
        }
    }

    private static List<String> readAll(String resource) throws IOException {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(ShopGoldenTest.class.getResourceAsStream(resource)),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isEmpty()) out.add(line);
            }
        }
        return out;
    }
}
