package cn.quotidietium.balatro.gui;

/** GUI 菜单类型。点击分派只认 Holder 中的类型，不认界面标题（防伪造）。 */
public enum MenuType {
    /** 主菜单：选择标准局 / 挑战局。 */
    MAIN,
    /** 牌组选择（15）。 */
    DECK,
    /** 赌注选择（8）。 */
    STAKE,
    /** 挑战选择（20，仅挑战模式经过）。 */
    CHALLENGE,
    /** 确认页：汇总 + 种子设置 + 开始。 */
    CONFIRM
}
