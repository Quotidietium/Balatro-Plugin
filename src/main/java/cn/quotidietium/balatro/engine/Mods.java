package cn.quotidietium.balatro.engine;

/**
 * 本局修饰（mods），对应 balatro state.mods。
 * 由牌组/赌注/挑战效果在 createRun 时设置；后续版本（挑战/牌组）会扩展更多字段。
 * 0.1.0：白注 + 红牌组，所有字段默认值（mostly false/0）。
 */
public final class Mods {
    public boolean noInterest;       // 绿色牌组
    public boolean redStake;         // 红注：小盲无奖励
    public boolean greenStake;       // 绿注：目标分加速 ×1.15^(ante-1)
    public boolean blackStake;       // 黑注：商店可能出现永恒小丑
    public boolean purpleStake;      // 紫注：目标分加速 ×1.3^(ante-1)
    public boolean orangeStake;      // 橙注：易腐小丑
    public boolean goldStake;        // 金注：租赁小丑
    public boolean plasma;           // 等离子牌组：chips/mult 取平均
    public boolean spectralInShop;   // 幽灵牌组/预言球：幻灵牌进商店
    public boolean doubleInterest;   // 挑战：利息翻倍
    public boolean freeReroll;       // 挑战：商店重掷免费
    public boolean allEternal;       // 挑战：所有小丑永恒
    public boolean facesToStone;     // 挑战：人头牌变石头
    public boolean checkered;        // 挑战：仅黑桃红桃
    public boolean allStone;         // 挑战：全石头
    public boolean numbersToFaces;   // 挑战：数字牌变人头
    public boolean glassDouble;      // 挑战：玻璃破碎概率翻倍
    public boolean inflation;        // 挑战：商店通胀
    public boolean doubleBoss;       // 挑战：双 Boss
    public boolean must5;            // 挑战：必须出满 5 张
    public boolean noJokers;         // 挑战：无法获得小丑
    public int handsSet;             // 挑战：固定出牌次数（0 表示不覆盖）
    public int handSize;             // 挑战：手牌上限调整
    public double blindMult;         // 挑战：盲注倍率（0 表示不覆盖）
    public double jokerTax;          // 挑战：每小丑目标分 +比例
    public double rewardMult;        // 挑战：奖励倍率
    public double shopDiscount;      // 挑战：商店折扣
    public int minRewardMoney;       // 挑战：低于此金额无出牌奖励
    public boolean smallBigRewardHalf; // 挑战：小/大盲奖励减半
    // ---- 以下 4 项为对齐真版挑战机制的修正（R102，用户拍板「对齐原版机制」；REF 上游描述与 mods 不一致） ----
    public boolean noBlindReward;    // 煎蛋卷（真版）：所有盲注无奖励金
    public boolean noHandPay;        // 煎蛋卷（真版）：剩余出牌不再产生金钱
    public boolean faceDouble;       // 十五分钟城市（真版）：人头牌翻倍（替换所有 A/2/3）
    public boolean xrayFacedown;     // X 光视界（真版）：抽到的牌 1/4 概率面朝下
    /** 禁入券（真版煎蛋卷：种子基金/摇钱树不进商店券池）。空 = 无禁入。 */
    public final java.util.Set<String> bannedVouchers = new java.util.HashSet<>();
    /** 禁入小丑（真版煎蛋卷：奔月/火箭/黄金/卫星不进商店与随机小丑池）。空 = 无禁入。 */
    public final java.util.Set<String> bannedJokers = new java.util.HashSet<>();
    // ---- R122：真版疯狂世界（Mad World）对齐 ----
    /** 禁现 Boss（真版疯狂世界：植物禁现）。空 = 无禁现。 */
    public final java.util.Set<String> bannedBosses = new java.util.HashSet<>();
    /** 牌组点数带 [rankMin, rankMax]（真版疯狂世界：仅 2~9 共 32 张）。0 = 标准 2~14。 */
    public int rankMin; // 0 表示未启用
    public int rankMax;
    // ---- R123：其余 13 挑战真版对齐（balatrowiki.org Challenge Decks）----
    public boolean chipsCapByMoney;   // 富者愈富：单手筹码不得超当前金钱
    public boolean playedDebuff;      // 孤注一掷(Double or Nothing)：计分后的牌失效
    public boolean redSealDeck;       // 孤注一掷：全牌组红蜡封
    public boolean glassDeck;         // 易碎品：全牌组玻璃
    public boolean typecastTrigger;   // 刻板印象：击败第 4 底注 Boss 后全员永恒+槽位归零
    public boolean luxuryTax;         // 奢侈品税(真版)：每持 $5 手牌上限 -1（基础 10）
    public boolean inflationPerBuy;   // 通货膨胀(真版)：每次购买永久 +$1（重掷不涨；持有物售价同步涨）
    public boolean discardCost;       // 金针：每次弃牌花费 $1
    public boolean smallBigNoReward;  // 残酷(真版)：小/大盲注无奖励金
    public int discardsSet;           // 固定弃牌数（0 = 不覆盖）
    public int jokerSlotsSet = -1;    // 固定小丑槽（-1 = 不覆盖；0 是合法值——无丑牌）
    /** 禁入塔罗（易碎品：7 张增强塔罗）。 */
    public final java.util.Set<String> bannedTarots = new java.util.HashSet<>();
    /** 禁入幻灵（易碎品：魔宠/冷酷/咒言）。 */
    public final java.util.Set<String> bannedSpectrals = new java.util.HashSet<>();
    /** 禁入补充包（易碎品：标准包；无丑牌：丑牌包）。 */
    public final java.util.Set<String> bannedPacks = new java.util.HashSet<>();
    /** 禁入标签（无丑牌：8 个产出类标签；易碎品：标准标签）。 */
    public final java.util.Set<String> bannedTags = new java.util.HashSet<>();
}
