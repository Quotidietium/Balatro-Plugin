package cn.quotidietium.balatro.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 挑战模式修饰加载器：从 {@code /challengemods.txt}（由 gen-golden 从原版 data.js 导出）
 * 读取每个挑战的 mods，应用到 {@link RunState}（{@link Mods}）。
 *
 * <p>handSize/handsSet 仅写入 Mods 字段，由 {@code applyVouchersPassive} 应用；
 * jokers/money 在 createRun 内立即生效；其余布尔/数值修饰直接置位。
 */
public final class ChallengeMods {

    private static final Map<String, List<String[]>> BY_KEY = new HashMap<>();

    static {
        load();
    }

    private ChallengeMods() {
    }

    public static boolean exists(String challengeKey) {
        return BY_KEY.containsKey(challengeKey);
    }

    /** 把指定挑战的 mods 应用到状态（在 createRun 内、buildFullDeck 之前调用）。 */
    public static void applyTo(RunState s, String challengeKey) {
        List<String[]> mods = BY_KEY.get(challengeKey);
        if (mods == null) return;
        for (String[] kv : mods) applyOne(s, kv[0], kv[1]);
    }

    private static void applyOne(RunState s, String k, String v) {
        Mods m = s.mods;
        try {
            switch (k) {
                case "noInterest" -> m.noInterest = bool(v);
                case "freeReroll" -> m.freeReroll = bool(v);
                case "doubleInterest" -> m.doubleInterest = bool(v);
                case "minRewardMoney" -> m.minRewardMoney = Integer.parseInt(v);
                case "blindMult" -> m.blindMult = Double.parseDouble(v);
                case "handSize" -> m.handSize = Integer.parseInt(v);
                case "handsSet" -> m.handsSet = Integer.parseInt(v);
                case "doubleBoss" -> m.doubleBoss = bool(v);
                case "jokerTax" -> m.jokerTax = Double.parseDouble(v);
                case "allEternal" -> m.allEternal = bool(v);
                case "shopDiscount" -> m.shopDiscount = Double.parseDouble(v);
                case "facesToStone" -> m.facesToStone = bool(v);
                case "checkered" -> m.checkered = bool(v);
                case "numbersToFaces" -> m.numbersToFaces = bool(v);
                case "glassDouble" -> m.glassDouble = bool(v);
                case "inflation" -> m.inflation = bool(v);
                case "allStone" -> m.allStone = bool(v);
                case "must5" -> m.must5 = bool(v);
                case "rewardMult" -> m.rewardMult = Double.parseDouble(v);
                case "smallBigRewardHalf" -> m.smallBigRewardHalf = bool(v);
                case "noJokers" -> m.noJokers = bool(v);
                case "jokers" -> { for (String jk : v.split(",")) if (!jk.isEmpty()) s.gainJoker(jk, null); }
                case "money" -> s.money = Long.parseLong(v);
                default -> { /* 未知 mod（如 hands=-99，被 handsSet 覆盖）忽略 */ }
            }
        } catch (RuntimeException ignored) {
            // 单个 mod 值非法（如非数字）只跳过该条，不让整局 createRun 崩溃
        }
    }

    private static boolean bool(String v) {
        return "true".equals(v);
    }

    private static void load() {
        try (InputStream in = ChallengeMods.class.getResourceAsStream("/challengemods.txt")) {
            if (in == null) return;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                String curKey = null;
                List<String[]> cur = null;
                while ((line = r.readLine()) != null) {
                    if (line.isEmpty()) continue;
                    if (line.startsWith("CHALLENGE ")) {
                        curKey = line.substring(10);
                        cur = new ArrayList<>();
                        BY_KEY.put(curKey, cur);
                    } else if (line.equals("END")) {
                        curKey = null;
                        cur = null;
                    } else if (cur != null) {
                        int eq = line.indexOf('=');
                        if (eq > 0) cur.add(new String[]{line.substring(0, eq), line.substring(eq + 1)});
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            // 挑战 mods 缺失/损坏时退化为无挑战（不阻断插件加载）
        }
    }
}
