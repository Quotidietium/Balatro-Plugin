package cn.quotidietium.balatro.engine.joker;

import cn.quotidietium.balatro.engine.Joker;
import cn.quotidietium.balatro.engine.JokerInstance;
import java.util.HashMap;
import java.util.Map;

/**
 * 小丑注册表：key → {@link Joker}。启动时注册 {@link BasicJoker} 全部；
 * 后续版本（0.4.0 全小丑）继续向此注册。
 *
 * <p>其他模块/插件可经 {@link #register(Joker)} 追加自定义小丑。
 */
public final class JokerRegistry {

    private static final Map<String, Joker> BY_KEY = new HashMap<>();

    static {
        for (BasicJoker j : BasicJoker.values()) {
            register(j);
        }
    }

    private JokerRegistry() {
    }

    /** 注册（覆盖同名）。 */
    public static void register(Joker joker) {
        BY_KEY.put(joker.key(), joker);
    }

    /** 按 key 取定义，不存在返回 null。 */
    public static Joker byKey(String key) {
        return BY_KEY.get(key);
    }

    /** 按 key 创建一个运行时实例，不存在返回 null。 */
    public static JokerInstance create(String key) {
        Joker def = BY_KEY.get(key);
        return def == null ? null : new JokerInstance(def);
    }

    public static boolean exists(String key) {
        return BY_KEY.containsKey(key);
    }
}
