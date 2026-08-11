package cn.quotidietium.balatro.engine.shop;

import cn.quotidietium.balatro.engine.Card;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.JokerInstance;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.engine.RunState;
import cn.quotidietium.balatro.engine.joker.JokerRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商店生成与交易，移植自 {@code engine.js} 商店段（genShop/makeJokerItem/buy/reroll 等）。
 * 小丑池与稀有度取自 {@link JokerRegistry}（含元数据），故 0.4.0 小丑全齐后即可正确生成。
 *
 * <p>0.2.0：本类提供纯逻辑；全息商店视图与回合流程接入（胜出后进商店）随后补齐。
 */
public final class Shop {

    private Shop() {
    }

    // ---- 商品数据 ----
    public static final class CardItem {
        public String kind; // joker/tarot/planet/spectral/playing
        public JokerInstance joker; // kind=joker
        public Card card;           // kind=playing
        public String key;          // tarot/planet/spectral 的 key
        public String name, desc;
        public long price;
        public boolean sold;

        public CardItem copy() {
            CardItem c = new CardItem();
            c.kind = kind; c.joker = joker; c.card = card; c.key = key;
            c.name = name; c.desc = desc; c.price = price; c.sold = sold;
            return c;
        }
    }

    public static final class PackItem {
        public Data.Pack pack;
        public String name, desc;
        public long price;
        public boolean sold;
    }

    public static final class VoucherItem {
        public Data.Voucher voucher;
        public String name, desc;
        public long price;
        public boolean sold;
    }

    public static final class ShopData {
        public List<CardItem> cards = new ArrayList<>();
        public List<PackItem> packs = new ArrayList<>();
        public VoucherItem voucher;
        public int rerollCount;
        public int freeRerolls;
    }

    // ---- 价格 / 负担 ----

    public static long shopPrice(RunState s, long base) {
        long p = base;
        if (hasVoucher(s, "liquidation")) p = (long) Math.ceil(p * 0.5);
        else if (hasVoucher(s, "clearance")) p = (long) Math.ceil(p * 0.75);
        if (s.mods.shopDiscount != 0) p = (long) Math.ceil(p * s.mods.shopDiscount);
        if (s.mods.inflation) p += s.inflation;
        return Math.max(1, p);
    }

    public static long jokerCost(JokerInstance j) {
        int add = j.edition == Data.Edition.FOIL ? 2 : j.edition == Data.Edition.HOLO ? 3
                : j.edition == Data.Edition.POLY ? 5 : j.edition == Data.Edition.NEGATIVE ? 5 : 0;
        return Math.max(1, j.def.cost() + add);
    }

    public static boolean canAfford(RunState s, long price) {
        long credit = s.flags != null && s.flags.get("credit") instanceof Number
                ? ((Number) s.flags.get("credit")).longValue() : 0;
        return s.money + credit >= price;
    }

    private static boolean hasVoucher(RunState s, String key) {
        return s.vouchers.contains(key);
    }

    // ---- 开店 / 生成 ----

    public static void openShop(RunState s) {
        s.phase = cn.quotidietium.balatro.engine.Phase.SHOP;
        genShop(s);
    }

    private static void genShop(RunState s) {
        Rng.Stream st = s.stream("shopgen" + s.roundCount);
        List<CardItem> cards = genShopCards(s);

        // 补充包 2 个
        List<PackItem> packs = new ArrayList<>();
        List<Data.Pack> packPool = new ArrayList<>();
        for (Data.Pack p : Data.PACKS) if (p.type != Data.PackType.SPECTRAL) packPool.add(p);
        for (int i = 0; i < 2; i++) {
            Data.Pack p = st.pick(packPool);
            if (s.nextShop.get("etherealPack") != null) {
                for (Data.Pack x : Data.PACKS) if (x.type == Data.PackType.SPECTRAL) p = x;
                s.nextShop.remove("etherealPack");
            }
            boolean free = s.nextShop.get("coupon") != null;
            PackItem pi = new PackItem();
            pi.pack = p; pi.name = p.name; pi.desc = p.size + " 张选 " + p.choose + " 张";
            pi.price = free ? 0 : shopPrice(s, p.cost);
            packs.add(pi);
        }

        // 优惠券
        VoucherItem voucher = null;
        List<Data.Voucher> avail = new ArrayList<>();
        for (Data.Voucher v : Data.VOUCHERS) {
            if (s.vouchers.contains(v.key)) continue;
            if (v.requires != null && !s.vouchers.contains(v.requires)) continue;
            avail.add(v);
        }
        if (!avail.isEmpty()) {
            Data.Voucher v = st.pick(avail);
            voucher = new VoucherItem();
            voucher.voucher = v; voucher.name = v.name; voucher.desc = v.desc;
            voucher.price = shopPrice(s, 10);
        }

        if (s.nextShop.get("coupon") != null) {
            for (CardItem c : cards) c.price = 0;
            s.nextShop.remove("coupon");
        }
        s.nextShop.remove("freeTarot");
        s.nextShop.remove("freePlanet");

        ShopData shop = new ShopData();
        shop.cards = cards;
        shop.packs = packs;
        shop.voucher = voucher;
        shop.rerollCount = 0;
        int freeRerolls = (s.flags != null && s.flags.get("freeRerolls") instanceof Number
                ? ((Number) s.flags.get("freeRerolls")).intValue() : 0);
        if (s.nextShop.get("freeReroll") != null) freeRerolls += 99;
        if (s.mods.freeReroll) freeRerolls += 99;
        shop.freeRerolls = freeRerolls;
        s.nextShop.remove("freeReroll");
        s.shop = shop;
    }

    private static List<CardItem> genShopCards(RunState s) {
        Rng.Stream st = s.stream("shopcards");
        List<CardItem> items = new ArrayList<>();
        int slots = s.shopSlots;

        List<int[]> weights = new ArrayList<>(); // {kindCode, w}
        // kindCode: 0 joker,1 tarot,2 planet,3 playing,4 spectral
        weights.add(new int[]{0, 70});
        weights.add(new int[]{1, hasVoucher(s, "tarott") ? 30 : hasVoucher(s, "tarotm") ? 15 : 8});
        weights.add(new int[]{2, hasVoucher(s, "planett") ? 30 : hasVoucher(s, "planetm") ? 15 : 8});
        if (hasVoucher(s, "magictrick")) weights.add(new int[]{3, 8});
        if (s.mods.spectralInShop || hasVoucher(s, "omen")) weights.add(new int[]{4, 4});
        if (s.mods.noJokers) weights.removeIf(w -> w[0] == 0);

        for (int i = 0; i < slots; i++) {
            int kind = weightedPick(st, weights);
            items.add(genShopItem(s, kind, i));
        }
        // 标签保证
        Object forceRarity = s.nextShop.get("rarity");
        if (forceRarity != null && !items.isEmpty()) {
            items.set(0, makeJokerItem(s, (Integer) forceRarity, null));
            s.nextShop.remove("rarity");
        }
        Object forceEdition = s.nextShop.get("edition");
        if (forceEdition != null && !items.isEmpty()) {
            items.set(items.size() - 1, makeJokerItem(s, null, (String) forceEdition));
            s.nextShop.remove("edition");
        }
        return items;
    }

    private static int weightedPick(Rng.Stream st, List<int[]> weights) {
        // 复用 Rng.weighted：把 int[] 包装。
        // 防御：weights 空 / 全权重 ≤0 时 Rng.weighted 返回 null（当前 genShopCards 保证
        // tarot(8)+planet(8) 总在，不可达；但作为「不信任输入」的最后一道防线）。
        int[] picked = st.weighted(weights, w -> w[1]);
        if (picked == null) return 1; // 退化到塔罗（非空、价格正常的稳妥默认）
        return picked[0];
    }

    private static CardItem genShopItem(RunState s, int kindCode, int slotIdx) {
        Rng.Stream st = s.stream("shopcards");
        switch (kindCode) {
            case 0: return makeJokerItem(s, null, null);
            case 1: {
                Data.Tarot t = st.pick(List.of(Data.Tarot.values()));
                boolean free = s.nextShop.get("freeTarot") != null;
                return item("tarot", t.key, t.name, t.desc, free ? 0 : shopPrice(s, 3));
            }
            case 2: {
                Data.Planet p = st.pick(List.of(Data.Planet.values()));
                boolean free = s.nextShop.get("freePlanet") != null
                        || (s.flags != null && Boolean.TRUE.equals(s.flags.get("freePlanets")));
                return item("planet", p.key, p.name, p.desc, free ? 0 : shopPrice(s, 3));
            }
            case 4: {
                Data.Spectral sp = st.pick(List.of(Data.Spectral.values()));
                return item("spectral", sp.key, sp.name, sp.desc, shopPrice(s, 4));
            }
            case 3: {
                Card c = s.randomPlayingCard();
                if (hasVoucher(s, "illusion")) {
                    Rng.Stream r = s.stream("illusion");
                    if (r.chance(0.4)) {
                        Data.Enhancement[] enhs = Data.Enhancement.values();
                        c.setEnh(enhs[r.range(0, enhs.length - 1)]);
                    }
                    if (r.chance(0.3)) {
                        Data.Edition[] eds = {Data.Edition.FOIL, Data.Edition.HOLO, Data.Edition.POLY};
                        c.setEdition(eds[r.range(0, 2)]);
                    }
                }
                CardItem it = new CardItem();
                it.kind = "playing"; it.card = c; it.name = s.cardName(c); it.desc = "游戏牌";
                it.price = shopPrice(s, 1);
                return it;
            }
            default: return null;
        }
    }

    private static CardItem item(String kind, String key, String name, String desc, long price) {
        CardItem it = new CardItem();
        it.kind = kind; it.key = key; it.name = name; it.desc = desc; it.price = price;
        return it;
    }

    public static CardItem makeJokerItem(RunState s, Integer forceRarity, String forceEdition) {
        Rng.Stream st = s.stream("shopjoker");
        Integer rarity = forceRarity;
        if (rarity == null) {
            double r = st.next() * 100;
            rarity = r < 70 ? 0 : r < 95 ? 1 : 2;
        }
        List<Joker> pool = new ArrayList<>();
        for (Joker j : JokerRegistry.allJokersOrdered()) {
            if (JokerRegistry.rarityOf(j.key()) != rarity) continue;
            if (j.key().equals("cavendish") && !s.grosDead) continue;
            if (s.mods.noJokers) continue;
            if (!Boolean.TRUE.equals(s.flags.get("allowDupes"))) {
                boolean owned = false;
                for (JokerInstance o : s.jokers) if (o.def.key().equals(j.key())) { owned = true; break; }
                if (owned) continue;
            }
            pool.add(j);
        }
        if (pool.isEmpty()) {
            Data.Tarot t = st.pick(List.of(Data.Tarot.values()));
            return item("tarot", t.key, t.name, t.desc, shopPrice(s, 3));
        }
        Joker def = st.pick(pool);
        Data.Edition edition = forceEdition != null ? parseEdition(forceEdition) : null;
        if (edition == null) {
            double chance = hasVoucher(s, "glowup") ? 0.2 : hasVoucher(s, "hone") ? 0.1 : 0.05;
            if (st.chance(chance)) edition = parseEdition(weightedEdition(st));
        }
        JokerInstance ji = new JokerInstance(def);
        ji.edition = edition;
        if (s.mods.blackStake && st.chance(0.3)) ji.eternal = true;
        if (s.mods.orangeStake && st.chance(0.3)) ji.perishable = true;
        if (s.mods.goldStake && st.chance(0.3)) ji.rental = true;
        if (s.mods.allEternal) ji.eternal = true;
        long price = shopPrice(s, jokerCost(ji));
        if (ji.rental) price = Math.max(1, price - 3);
        CardItem it = new CardItem();
        it.kind = "joker"; it.joker = ji; it.name = JokerRegistry.nameOf(def.key());
        it.desc = def.desc(); it.price = price;
        return it;
    }

    private static String weightedEdition(Rng.Stream st) {
        double r = st.next() * 100;
        if (r < 50) return "foil";
        if (r < 85) return "holo";
        return "poly";
    }

    private static Data.Edition parseEdition(String e) {
        if (e == null) return null;
        return switch (e) {
            case "foil" -> Data.Edition.FOIL;
            case "holo" -> Data.Edition.HOLO;
            case "poly" -> Data.Edition.POLY;
            case "negative" -> Data.Edition.NEGATIVE;
            default -> null;
        };
    }

    // ---- 交易 ----

    public static boolean buyCard(RunState s, int idx) {
        ShopData shop = s.shop;
        if (shop == null || idx < 0 || idx >= shop.cards.size()) return false;
        CardItem it = shop.cards.get(idx);
        if (it.sold || !canAfford(s, it.price)) return false;
        if (it.kind.equals("joker")) {
            if (s.jokerSpace() <= 0) return false;
            s.money -= it.price;
            s.jokers.add(it.joker);
            s.msg("获得小丑：" + it.name);
        } else if (it.kind.equals("playing")) {
            s.money -= it.price;
            s.addCardToDeck(it.card);
            s.msg("牌组加入：" + it.name);
        } else {
            s.money -= it.price;
            if (!s.addConsumableKey(it.kind, it.key)) {
                s.money += it.price;
                return false; // 消耗品槽已满
            }
            s.msg("获得：" + it.name);
        }
        it.sold = true;
        cn.quotidietium.balatro.engine.Engine.recomputeFlags(s);
        return true;
    }

    public static boolean buyPack(RunState s, int idx) {
        ShopData shop = s.shop;
        if (shop == null || idx < 0 || idx >= shop.packs.size()) return false;
        PackItem it = shop.packs.get(idx);
        if (it.sold || !canAfford(s, it.price)) return false;
        s.money -= it.price;
        it.sold = true;
        Packs.open(s, it.pack); // 进入补充包选择
        return true;
    }

    public static boolean buyVoucher(RunState s) {
        ShopData shop = s.shop;
        if (shop == null || shop.voucher == null || shop.voucher.sold) return false;
        if (!canAfford(s, shop.voucher.price)) return false;
        s.money -= shop.voucher.price;
        shop.voucher.sold = true;
        Data.Voucher v = shop.voucher.voucher;
        s.vouchers.add(v.key);
        s.msg("获得优惠券：" + v.name);
        if (v.key.equals("hieroglyph") || v.key.equals("petroglyph")) {
            s.ante = Math.max(1, s.ante - 1);
        }
        cn.quotidietium.balatro.engine.Engine.recomputeFlags(s);
        return true;
    }

    public static long reroll(RunState s) {
        ShopData shop = s.shop;
        if (shop == null) return -1;
        long cost = 5 + shop.rerollCount;
        if (hasVoucher(s, "reroll1")) cost -= 2;
        if (hasVoucher(s, "reroll2")) cost -= 2;
        cost = Math.max(0, cost);
        if (shop.freeRerolls > 0) { shop.freeRerolls--; cost = 0; }
        else if (!canAfford(s, cost)) return -1;
        s.money -= cost;
        shop.rerollCount++;
        shop.cards = genShopCardsPublic(s);
        for (JokerInstance j : new ArrayList<>(s.jokers)) if (!j.debuff) j.def.onReroll(s, j);
        return cost;
    }

    // 包级可见的生成入口（reroll 复用）
    private static List<CardItem> genShopCardsPublic(RunState s) {
        return genShopCards(s);
    }
}
