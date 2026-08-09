package cn.quotidietium.balatro.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * 一张已获得的小丑的运行时实例，对应 balatro state.jokers[i]。
 * 持有可变状态：失效标记、版本、累积字段（extra）、租赁/易腐等。
 */
public final class JokerInstance {
    public final Joker def;
    public boolean debuff;       // 永久失效（Boss/挑战）
    public boolean debuffHand;   // 本次出牌临时失效（绯红之心）
    public Data.Edition edition; // 版本加成（闪膜/镭射/多彩）
    public final Map<String, Object> extra = new HashMap<>(); // 累积型小丑状态
    public boolean rental;       // 租赁：每回合 -$3
    public boolean perishable;   // 易腐：5 回合后失效
    public int perishCount = 5;

    public JokerInstance(Joker def) {
        this.def = def;
    }
}
