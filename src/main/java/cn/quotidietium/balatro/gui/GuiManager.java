package cn.quotidietium.balatro.gui;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.command.BalatroCommand;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.session.GameSession;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * 开局向导 GUI 管理器：{@code /balatro gui} 打开的箱子界面菜单。
 *
 * <p>菜单流程：{@code MAIN(模式) → DECK(牌组) → STAKE(赌注) → [CHALLENGE(挑战)] → CONFIRM(确认/种子/开始)}。
 * 安全约定：
 * <ul>
 *   <li>只认 {@link GuiHolder}（不认标题）；所有点击/拖拽一律取消，杜绝物品被偷入/取出；</li>
 *   <li>选择状态存于 {@link GuiState}（纯逻辑），点击只改状态再整体重画，槽位映射统一走 {@link GuiLayout}；</li>
 *   <li>界面开/关统一延后 1 tick：InventoryClickEvent 处理器内不同步 open/closeInventory
 *       （见 note/references/papo-服务端.md §3）；</li>
 *   <li>每玩家 150ms 点击节流（对齐 BoardListener），防篡改客户端刷点击包反复重建整页；</li>
 *   <li>种子经聊天框输入（60 秒超时）；等待表用 ConcurrentHashMap + 异步线程原子认领，
 *       异步聊天事件仅捕获文本，实际处理回主线程；</li>
 *   <li>玩家退出 / 插件关停时清理状态并关闭界面。</li>
 * </ul>
 */
public final class GuiManager implements Listener {

    /** 种子聊天输入的超时（毫秒）。 */
    private static final long SEED_INPUT_TIMEOUT_MS = 60_000L;

    /** 每玩家点击节流（与 BoardListener 的 150ms 约定一致），防篡改客户端高速刷点击包拖垮主线程。 */
    private static final long CLICK_THROTTLE_MS = 150L;

    // ---- 确认页固定槽位（54 格；返回/开始/取消沿用 GuiLayout 的底行约定） ----
    private static final int CONFIRM_DECK_SLOT = 10;
    private static final int CONFIRM_STAKE_SLOT = 12;
    private static final int CONFIRM_MODE_SLOT = 14;
    private static final int CONFIRM_SEED_SLOT = 16;
    private static final int CONFIRM_START_SLOT = 49;
    private static final int CONFIRM_CANCEL_SLOT = 51;

    // ---- 主菜单固定槽位（27 格） ----
    private static final int MAIN_INFO_SLOT = 4;
    private static final int MAIN_NORMAL_SLOT = 11;
    private static final int MAIN_CHALLENGE_SLOT = 15;
    private static final int MAIN_CLOSE_SLOT = 22;

    /** 各列表菜单顶行中部的「当前选择」提示槽。 */
    private static final int LIST_INFO_SLOT = 4;

    private final BalatroPlugin plugin;
    private final Map<UUID, GuiState> states = new HashMap<>();
    // AsyncChatEvent 在异步线程读写本表（onChat），主线程同时 put/remove/clear：
    // 必须用 ConcurrentHashMap，且 onChat 以原子 remove 一次性认领（防同一玩家
    // 快速连发两条聊天时被双重吞并/双重处理）。
    private final Map<UUID, Long> pendingSeeds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    /**
     * 标记当前 tick 内的「程序化关闭」（openMenu/closeInventoryLater 触发的 closeInventory）。
     * InventoryCloseEvent 处理器据此区分：程序化关闭不清 pendingSeeds（promptSeed 流程
     * 先设 pendingSeeds 再关界面）；玩家主动按 ESC 关闭才清 pendingSeeds，防止其下一条
     * 普通聊天被吞作种子。
     */
    private final Set<UUID> internalClose = new HashSet<>();

    public GuiManager(BalatroPlugin plugin) {
        this.plugin = plugin;
    }

    // ================= 打开与构建 =================

    /** {@code /balatro gui} 入口：打开主菜单。 */
    public void openGui(Player player) {
        openMenu(player, MenuType.MAIN);
    }

    private GuiState stateOf(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), k -> new GuiState());
    }

    private void openMenu(Player player, MenuType type) {
        // 玩家已回到 GUI 流程（不在种子聊天输入上下文）：取消其待处理的种子等待，
        // 否则他接下来的一条普通聊天会被吞作种子、并被强制弹到 CONFIRM 页打断当前浏览。
        pendingSeeds.remove(player.getUniqueId());
        GuiState st = stateOf(player);
        GuiHolder holder = new GuiHolder(type);
        Inventory inv = switch (type) {
            case MAIN -> buildMain(holder);
            case DECK -> buildDeck(holder, st);
            case STAKE -> buildStake(holder, st);
            case CHALLENGE -> buildChallenge(holder, st);
            case CONFIRM -> buildConfirm(holder, st);
        };
        holder.bind(inv);
        // openInventory 会触发旧界面的 InventoryCloseEvent；标记为程序化关闭，
        // 使 onClose 不误清 pendingSeeds（本方法开头已主动清，此标记防重复处理）。
        internalClose.add(player.getUniqueId());
        try {
            player.openInventory(inv);
        } finally {
            internalClose.remove(player.getUniqueId());
        }
    }

    /**
     * 下一 tick 再打开菜单。InventoryClickEvent 处理器内不同步调用 openInventory
     * （见 note/references/papo-服务端.md §3：同步开/关界面可致客户端幽灵物品与包序错乱）。
     * 选择状态（GuiState）在点击当下已同步修改，界面延后 1 tick 重建不受影响。
     */
    private void openMenuLater(Player player, MenuType type) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            try {
                openMenu(player, type);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("GUI 打开菜单异常（玩家 " + player.getName()
                        + "，菜单 " + type + "）：" + ex);
            }
        });
    }

    /** 下一 tick 再关闭界面（同上约束；玩家已下线则跳过）。 */
    private void closeInventoryLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                // 标记程序化关闭：promptSeed 流程先设 pendingSeeds 再关界面，
                // 此标记使 InventoryCloseEvent 不清 pendingSeeds（保留种子输入等待）。
                internalClose.add(player.getUniqueId());
                try {
                    player.closeInventory();
                } finally {
                    internalClose.remove(player.getUniqueId());
                }
            }
        });
    }

    private Inventory buildMain(GuiHolder holder) {
        Inventory inv = Bukkit.createInventory(holder, GuiLayout.SIZE_MAIN,
                Component.text("小丑牌 · 开局向导", NamedTextColor.GOLD));
        fillBorder(inv, GuiLayout.SIZE_MAIN);
        inv.setItem(MAIN_INFO_SLOT, GuiItems.item(Material.BOOK, "选择开局模式", NamedTextColor.GOLD,
                "标准局：15 牌组 × 8 赌注，通关 8 底注",
                "挑战局：在标准局上叠加 20 种挑战规则",
                "后续可继续选择 牌组 / 赌注 / 种子"));
        inv.setItem(MAIN_NORMAL_SLOT, GuiItems.item(Material.LIME_DYE, "标准局", NamedTextColor.GREEN,
                "经典玩法：通关 8 个底注", "点击进入牌组选择"));
        inv.setItem(MAIN_CHALLENGE_SLOT, GuiItems.item(Material.NETHER_STAR, "挑战局", NamedTextColor.LIGHT_PURPLE,
                "叠加一条挑战规则（共 20 种）", "点击进入牌组选择"));
        inv.setItem(MAIN_CLOSE_SLOT, GuiItems.item(Material.BARRIER, "关闭", NamedTextColor.RED));
        return inv;
    }

    private Inventory buildDeck(GuiHolder holder, GuiState st) {
        Inventory inv = Bukkit.createInventory(holder, GuiLayout.SIZE_LIST,
                Component.text("选择牌组", NamedTextColor.GOLD));
        fillBorder(inv, GuiLayout.SIZE_LIST);
        List<Data.Deck> decks = Data.DECKS;
        for (int i = 0; i < decks.size(); i++) {
            Data.Deck d = decks.get(i);
            ItemStack it = GuiItems.item(GuiItems.deckMaterial(d.key()),
                    d.name(), NamedTextColor.YELLOW, d.desc(), "key: " + d.key());
            if (i == st.deckIdx()) {
                it = GuiItems.glint(it);
            }
            inv.setItem(GuiLayout.slotForIndex(GuiLayout.SIZE_LIST, i), it);
        }
        inv.setItem(LIST_INFO_SLOT, infoItem("当前牌组", st.deck().name() + " — " + st.deck().desc()));
        inv.setItem(GuiLayout.backSlot(GuiLayout.SIZE_LIST),
                GuiItems.item(Material.ARROW, "返回：模式选择", NamedTextColor.YELLOW));
        inv.setItem(GuiLayout.nextSlot(GuiLayout.SIZE_LIST),
                GuiItems.item(Material.LIME_DYE, "下一步：赌注选择", NamedTextColor.GREEN));
        return inv;
    }

    private Inventory buildStake(GuiHolder holder, GuiState st) {
        Inventory inv = Bukkit.createInventory(holder, GuiLayout.SIZE_MAIN,
                Component.text("选择赌注", NamedTextColor.GOLD));
        fillBorder(inv, GuiLayout.SIZE_MAIN);
        List<Data.Stake> stakes = Data.STAKES;
        for (int i = 0; i < stakes.size(); i++) {
            Data.Stake s = stakes.get(i);
            ItemStack it = GuiItems.item(GuiItems.stakeMaterial(i),
                    i + " " + s.name(), NamedTextColor.YELLOW, s.desc(), "赌注效果向上累加（含更低赌注）");
            if (i == st.stakeIdx()) {
                it = GuiItems.glint(it);
            }
            inv.setItem(GuiLayout.slotForIndex(GuiLayout.SIZE_MAIN, i), it);
        }
        inv.setItem(LIST_INFO_SLOT, infoItem("当前赌注",
                st.stakeIdx() + " " + st.stake().name() + " — " + st.stake().desc()));
        inv.setItem(GuiLayout.backSlot(GuiLayout.SIZE_MAIN),
                GuiItems.item(Material.ARROW, "返回：牌组选择", NamedTextColor.YELLOW));
        inv.setItem(GuiLayout.nextSlot(GuiLayout.SIZE_MAIN),
                GuiItems.item(Material.LIME_DYE,
                        st.mode() == GuiState.Mode.CHALLENGE ? "下一步：挑战选择" : "下一步：确认开局",
                        NamedTextColor.GREEN));
        return inv;
    }

    private Inventory buildChallenge(GuiHolder holder, GuiState st) {
        Inventory inv = Bukkit.createInventory(holder, GuiLayout.SIZE_LIST,
                Component.text("选择挑战", NamedTextColor.GOLD));
        fillBorder(inv, GuiLayout.SIZE_LIST);
        List<Data.Challenge> challenges = Data.CHALLENGES;
        for (int i = 0; i < challenges.size(); i++) {
            Data.Challenge c = challenges.get(i);
            ItemStack it = GuiItems.item(GuiItems.challengeMaterial(c.key()),
                    c.name(), NamedTextColor.LIGHT_PURPLE, c.desc(), "key: " + c.key());
            if (i == st.challengeIdx()) {
                it = GuiItems.glint(it);
            }
            inv.setItem(GuiLayout.slotForIndex(GuiLayout.SIZE_LIST, i), it);
        }
        inv.setItem(LIST_INFO_SLOT, infoItem("当前挑战", st.challenge().name() + " — " + st.challenge().desc()));
        inv.setItem(GuiLayout.backSlot(GuiLayout.SIZE_LIST),
                GuiItems.item(Material.ARROW, "返回：赌注选择", NamedTextColor.YELLOW));
        inv.setItem(GuiLayout.nextSlot(GuiLayout.SIZE_LIST),
                GuiItems.item(Material.LIME_DYE, "下一步：确认开局", NamedTextColor.GREEN));
        return inv;
    }

    private Inventory buildConfirm(GuiHolder holder, GuiState st) {
        boolean challengeMode = st.mode() == GuiState.Mode.CHALLENGE;
        Inventory inv = Bukkit.createInventory(holder, GuiLayout.SIZE_LIST,
                Component.text("确认开局", NamedTextColor.GOLD));
        fillBorder(inv, GuiLayout.SIZE_LIST);

        inv.setItem(CONFIRM_DECK_SLOT, GuiItems.item(GuiItems.deckMaterial(st.deck().key()),
                "牌组：" + st.deck().name(), NamedTextColor.YELLOW, st.deck().desc(), "点击重新选择"));
        inv.setItem(CONFIRM_STAKE_SLOT, GuiItems.item(GuiItems.stakeMaterial(st.stakeIdx()),
                "赌注：" + st.stakeIdx() + " " + st.stake().name(), NamedTextColor.YELLOW,
                st.stake().desc(), "点击重新选择"));
        if (challengeMode) {
            inv.setItem(CONFIRM_MODE_SLOT, GuiItems.item(GuiItems.challengeMaterial(st.challenge().key()),
                    "挑战：" + st.challenge().name(), NamedTextColor.LIGHT_PURPLE,
                    st.challenge().desc(), "点击重新选择"));
        } else {
            inv.setItem(CONFIRM_MODE_SLOT, GuiItems.item(Material.LIME_DYE,
                    "模式：标准局", NamedTextColor.GREEN, "通关 8 个底注", "点击返回模式选择"));
        }
        inv.setItem(CONFIRM_SEED_SLOT, GuiItems.item(Material.NAME_TAG,
                "种子：" + (st.seed() == null ? "随机" : st.seed()), NamedTextColor.AQUA,
                "左键：在聊天框输入种子（60 秒内）",
                "右键：恢复随机种子",
                "同一种子可复现同一局（抽牌/商店/小丑完全一致）"));

        inv.setItem(GuiLayout.backSlot(GuiLayout.SIZE_LIST),
                GuiItems.item(Material.ARROW,
                        challengeMode ? "返回：挑战选择" : "返回：赌注选择", NamedTextColor.YELLOW));
        inv.setItem(CONFIRM_START_SLOT, GuiItems.item(Material.EMERALD, "▶ 开始游戏", NamedTextColor.GREEN,
                "以当前选择开始一局"));
        inv.setItem(CONFIRM_CANCEL_SLOT, GuiItems.item(Material.BARRIER, "取消", NamedTextColor.RED));
        return inv;
    }

    private static ItemStack infoItem(String title, String detail) {
        return GuiItems.item(Material.PAPER, title, NamedTextColor.GOLD, detail);
    }

    /** 顶行 + 底行边框（27 格：0~8、18~26；54 格：0~8、45~53），随后被功能槽覆盖。 */
    private static void fillBorder(Inventory inv, int size) {
        ItemStack filler = GuiItems.filler();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, filler);
        }
        for (int i = size - 9; i < size; i++) {
            inv.setItem(i, filler);
        }
    }

    // ================= 事件处理 =================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        // 菜单界面内的一切点击一律取消：含数字键/Shift/双击/玩家背包侧，杜绝物品移动
        // （取消不受下方节流影响：物品保护绝不削弱）
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 只响应顶部菜单本体内的点击（玩家背包侧仅拦截不响应）
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
            return;
        }
        // 客户端不可信，可高速刷点击包：每玩家 150ms 节流，防选择点击反复重建整页拖垮主线程
        if (!throttle(player)) {
            return;
        }
        int slot = e.getSlot();
        try {
            dispatchClick(player, holder.type(), slot, e.getClick());
        } catch (RuntimeException ex) {
            // 兜底：菜单异常不应逃逸到事件分发器；关界面防止玩家卡在半状态菜单
            plugin.getLogger().warning("GUI 点击处理异常（玩家 " + player.getName()
                    + "，菜单 " + holder.type() + "，槽位 " + slot + "）：" + ex);
            player.sendMessage("§c处理点击时出错，请重新打开 /balatro gui。");
            closeInventoryLater(player);
        }
    }

    private boolean throttle(Player player) {
        long now = System.currentTimeMillis();
        long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < CLICK_THROTTLE_MS) {
            return false;
        }
        lastClick.put(player.getUniqueId(), now);
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        // 拖拽可能跨顶部菜单与玩家背包：只要顶部是我们的菜单一律取消
        if (e.getView().getTopInventory().getHolder() instanceof GuiHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        states.remove(id);
        pendingSeeds.remove(id);
        lastClick.remove(id);
    }

    /**
     * 玩家主动关闭界面（按 ESC / 被其他插件强制关闭）时清理待处理种子等待。
     *
     * <p>场景：玩家在确认页点种子图标 → 进入 60 秒聊天输入模式 → 按 ESC 放弃。
     * 若不清 pendingSeeds，其下一条普通聊天会被吞作种子并强制弹回确认页。
     * 程序化关闭（openMenu/closeInventoryLater）由 internalClose 标记排除。
     */
    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof GuiHolder)) return;
        if (!(e.getPlayer() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        if (internalClose.remove(id)) {
            // 程序化关闭（openMenu/closeInventoryLater）：不清 pendingSeeds
            return;
        }
        // 玩家主动关闭：清待处理种子，避免下一条聊天被吞
        pendingSeeds.remove(id);
    }

    /** 关停（onDisable / reload）：关闭所有本插件菜单界面并清空状态。 */
    public void closeAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            try {
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof GuiHolder) {
                    p.closeInventory();
                }
            } catch (RuntimeException ignored) {
                // 关停阶段个别玩家清理失败不影响其余
            }
        }
        states.clear();
        pendingSeeds.clear();
        lastClick.clear();
    }

    // ================= 点击分派 =================

    private void dispatchClick(Player player, MenuType type, int slot, ClickType click) {
        GuiState st = stateOf(player);
        switch (type) {
            case MAIN -> {
                if (slot == MAIN_NORMAL_SLOT) {
                    st.setMode(GuiState.Mode.NORMAL);
                    clickSound(player);
                    openMenuLater(player, MenuType.DECK);
                } else if (slot == MAIN_CHALLENGE_SLOT) {
                    st.setMode(GuiState.Mode.CHALLENGE);
                    clickSound(player);
                    openMenuLater(player, MenuType.DECK);
                } else if (slot == MAIN_CLOSE_SLOT) {
                    clickSound(player);
                    closeInventoryLater(player);
                }
            }
            case DECK -> {
                if (slot == GuiLayout.backSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.MAIN);
                    return;
                }
                if (slot == GuiLayout.nextSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.STAKE);
                    return;
                }
                int idx = GuiLayout.indexOfSlot(GuiLayout.SIZE_LIST, slot);
                if (idx >= 0 && st.setDeckIdx(idx)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.DECK); // 重画以更新选中光效
                }
            }
            case STAKE -> {
                if (slot == GuiLayout.backSlot(GuiLayout.SIZE_MAIN)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.DECK);
                    return;
                }
                if (slot == GuiLayout.nextSlot(GuiLayout.SIZE_MAIN)) {
                    clickSound(player);
                    openMenuLater(player, st.mode() == GuiState.Mode.CHALLENGE ? MenuType.CHALLENGE : MenuType.CONFIRM);
                    return;
                }
                int idx = GuiLayout.indexOfSlot(GuiLayout.SIZE_MAIN, slot);
                if (idx >= 0 && st.setStakeIdx(idx)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.STAKE);
                }
            }
            case CHALLENGE -> {
                if (slot == GuiLayout.backSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.STAKE);
                    return;
                }
                if (slot == GuiLayout.nextSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.CONFIRM);
                    return;
                }
                int idx = GuiLayout.indexOfSlot(GuiLayout.SIZE_LIST, slot);
                if (idx >= 0 && st.setChallengeIdx(idx)) {
                    clickSound(player);
                    openMenuLater(player, MenuType.CHALLENGE);
                }
            }
            case CONFIRM -> dispatchConfirmClick(player, st, slot, click);
        }
    }

    private void dispatchConfirmClick(Player player, GuiState st, int slot, ClickType click) {
        boolean challengeMode = st.mode() == GuiState.Mode.CHALLENGE;
        if (slot == CONFIRM_DECK_SLOT) {
            clickSound(player);
            openMenuLater(player, MenuType.DECK);
        } else if (slot == CONFIRM_STAKE_SLOT) {
            clickSound(player);
            openMenuLater(player, MenuType.STAKE);
        } else if (slot == CONFIRM_MODE_SLOT) {
            clickSound(player);
            openMenuLater(player, challengeMode ? MenuType.CHALLENGE : MenuType.MAIN);
        } else if (slot == CONFIRM_SEED_SLOT) {
            if (click.isRightClick()) {
                st.clearSeed();
                clickSound(player);
                player.sendMessage("§7已恢复随机种子。");
                openMenuLater(player, MenuType.CONFIRM);
            } else {
                promptSeed(player);
            }
        } else if (slot == GuiLayout.backSlot(GuiLayout.SIZE_LIST)) {
            clickSound(player);
            openMenuLater(player, challengeMode ? MenuType.CHALLENGE : MenuType.STAKE);
        } else if (slot == CONFIRM_START_SLOT) {
            clickSound(player);
            // 立即捕获不可变开局参数（此时玩家已被节流+界面将关，状态不会再变）；
            // 开局整体延后 1 tick，保持「先关界面、后开局」的顺序
            GuiState.StartRequest req = st.toStartRequest();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    startRun(player, req);
                }
            });
        } else if (slot == CONFIRM_CANCEL_SLOT) {
            clickSound(player);
            closeInventoryLater(player);
        }
    }

    private static void clickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.2f);
    }

    // ================= 种子聊天输入 =================

    private void promptSeed(Player player) {
        // 先同步登记等待（保证玩家的下一条聊天必被吞），界面关闭延后 1 tick
        pendingSeeds.put(player.getUniqueId(), System.currentTimeMillis() + SEED_INPUT_TIMEOUT_MS);
        closeInventoryLater(player);
        clickSound(player);
        player.sendMessage("§e请在聊天框输入本局种子（60 秒内有效）；输入 §fcancel§e 取消。");
        player.sendMessage("§7仅允许字母/数字/下划线/连字符，长度 1~32。当前输入会被吞掉不会广播。");
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        // 原子 remove 一次性认领：异步线程与主线程并发安全；
        // 同一玩家快速连发两条聊天时只有第一条能被吞并处理，杜绝双重设置/双重弹窗
        Long expiry = pendingSeeds.remove(id);
        if (expiry == null) {
            return;
        }
        if (System.currentTimeMillis() > expiry) {
            // 已超时：按普通聊天放行，但告知玩家种子输入已过期（避免困惑为何上条消息没反应）
            Player p = e.getPlayer();
            p.sendMessage("§7种子输入已超时（60 秒），本次聊天正常发送。");
            return;
        }
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (msg.startsWith("/")) {
            // 命令不吞（让玩家可以正常执行其他命令）；本次种子输入等待已随 remove 结束
            return;
        }
        e.setCancelled(true);
        // 异步事件只捕获文本，实际状态修改与界面操作回主线程
        Bukkit.getScheduler().runTask(plugin, () -> handleSeedInput(id, msg));
    }

    private void handleSeedInput(UUID playerId, String msg) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        GuiState st = states.get(playerId);
        if (st == null) {
            return;
        }
        if (msg.equalsIgnoreCase("cancel") || msg.equals("取消")) {
            player.sendMessage("§7已取消种子输入。");
        } else if (!Rng.isValidSeed(msg)) {
            player.sendMessage("§c无效种子：仅允许字母/数字/下划线/连字符，长度 1~32。仍使用原设置。");
        } else {
            st.setSeed(msg);
            player.sendMessage("§a种子已设置为 §f" + msg + "§a。");
        }
        // 若等待期间已通过命令开了局，则不再弹出确认页
        if (!plugin.sessionManager().isActive(player)) {
            try {
                openMenu(player, MenuType.CONFIRM);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("GUI 种子确认页重开异常（玩家 " + player.getName() + "）：" + ex);
            }
        }
    }

    // ================= 开局 =================

    /** 在点击后下一 tick 执行（参数已在点击时捕获为不可变 {@link GuiState.StartRequest}）。 */
    private void startRun(Player player, GuiState.StartRequest req) {
        player.closeInventory();
        if (plugin.sessionManager().isActive(player)) {
            // 双保险：命令层也挡，但 GUI 打开期间玩家可能用命令开了局
            player.sendMessage("§c你已在一局中，先用 /balatro quit。");
            return;
        }
        GameSession s;
        try {
            s = plugin.sessionManager().start(player, req.deckKey(), req.stakeIdx(), req.seed(), req.challengeKey());
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("GUI 开局异常（玩家 " + player.getName() + "）：" + ex);
            player.sendMessage("§c开局失败，请重试或联系管理员。");
            return;
        }
        if (s == null) {
            player.sendMessage("§c开局失败（可能 RunStart 被其他插件取消）。");
            return;
        }
        states.remove(player.getUniqueId()); // 开局成功后清空向导状态
        BalatroCommand.sendRunInfo(player, s, req.deckKey(), req.stakeIdx(), req.challengeKey());
        player.sendMessage(s.handDebug());
    }
}
