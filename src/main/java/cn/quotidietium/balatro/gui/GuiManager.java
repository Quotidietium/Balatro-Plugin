package cn.quotidietium.balatro.gui;

import cn.quotidietium.balatro.BalatroPlugin;
import cn.quotidietium.balatro.command.BalatroCommand;
import cn.quotidietium.balatro.engine.Data;
import cn.quotidietium.balatro.engine.Rng;
import cn.quotidietium.balatro.session.GameSession;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 *   <li>种子经聊天框输入（60 秒超时），异步聊天事件仅捕获文本，实际处理回主线程；</li>
 *   <li>玩家退出 / 插件关停时清理状态并关闭界面。</li>
 * </ul>
 */
public final class GuiManager implements Listener {

    /** 种子聊天输入的超时（毫秒）。 */
    private static final long SEED_INPUT_TIMEOUT_MS = 60_000L;

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
    private final Map<UUID, Long> pendingSeeds = new HashMap<>();

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
        player.openInventory(inv);
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
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 只响应顶部菜单本体内的点击（玩家背包侧仅拦截不响应）
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) {
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
            player.closeInventory();
        }
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
    }

    // ================= 点击分派 =================

    private void dispatchClick(Player player, MenuType type, int slot, ClickType click) {
        GuiState st = stateOf(player);
        switch (type) {
            case MAIN -> {
                if (slot == MAIN_NORMAL_SLOT) {
                    st.setMode(GuiState.Mode.NORMAL);
                    clickSound(player);
                    openMenu(player, MenuType.DECK);
                } else if (slot == MAIN_CHALLENGE_SLOT) {
                    st.setMode(GuiState.Mode.CHALLENGE);
                    clickSound(player);
                    openMenu(player, MenuType.DECK);
                } else if (slot == MAIN_CLOSE_SLOT) {
                    clickSound(player);
                    player.closeInventory();
                }
            }
            case DECK -> {
                if (slot == GuiLayout.backSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenu(player, MenuType.MAIN);
                    return;
                }
                if (slot == GuiLayout.nextSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenu(player, MenuType.STAKE);
                    return;
                }
                int idx = GuiLayout.indexOfSlot(GuiLayout.SIZE_LIST, slot);
                if (idx >= 0 && st.setDeckIdx(idx)) {
                    clickSound(player);
                    openMenu(player, MenuType.DECK); // 重画以更新选中光效
                }
            }
            case STAKE -> {
                if (slot == GuiLayout.backSlot(GuiLayout.SIZE_MAIN)) {
                    clickSound(player);
                    openMenu(player, MenuType.DECK);
                    return;
                }
                if (slot == GuiLayout.nextSlot(GuiLayout.SIZE_MAIN)) {
                    clickSound(player);
                    openMenu(player, st.mode() == GuiState.Mode.CHALLENGE ? MenuType.CHALLENGE : MenuType.CONFIRM);
                    return;
                }
                int idx = GuiLayout.indexOfSlot(GuiLayout.SIZE_MAIN, slot);
                if (idx >= 0 && st.setStakeIdx(idx)) {
                    clickSound(player);
                    openMenu(player, MenuType.STAKE);
                }
            }
            case CHALLENGE -> {
                if (slot == GuiLayout.backSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenu(player, MenuType.STAKE);
                    return;
                }
                if (slot == GuiLayout.nextSlot(GuiLayout.SIZE_LIST)) {
                    clickSound(player);
                    openMenu(player, MenuType.CONFIRM);
                    return;
                }
                int idx = GuiLayout.indexOfSlot(GuiLayout.SIZE_LIST, slot);
                if (idx >= 0 && st.setChallengeIdx(idx)) {
                    clickSound(player);
                    openMenu(player, MenuType.CHALLENGE);
                }
            }
            case CONFIRM -> dispatchConfirmClick(player, st, slot, click);
        }
    }

    private void dispatchConfirmClick(Player player, GuiState st, int slot, ClickType click) {
        boolean challengeMode = st.mode() == GuiState.Mode.CHALLENGE;
        if (slot == CONFIRM_DECK_SLOT) {
            clickSound(player);
            openMenu(player, MenuType.DECK);
        } else if (slot == CONFIRM_STAKE_SLOT) {
            clickSound(player);
            openMenu(player, MenuType.STAKE);
        } else if (slot == CONFIRM_MODE_SLOT) {
            clickSound(player);
            openMenu(player, challengeMode ? MenuType.CHALLENGE : MenuType.MAIN);
        } else if (slot == CONFIRM_SEED_SLOT) {
            if (click.isRightClick()) {
                st.clearSeed();
                clickSound(player);
                player.sendMessage("§7已恢复随机种子。");
                openMenu(player, MenuType.CONFIRM);
            } else {
                promptSeed(player);
            }
        } else if (slot == GuiLayout.backSlot(GuiLayout.SIZE_LIST)) {
            clickSound(player);
            openMenu(player, challengeMode ? MenuType.CHALLENGE : MenuType.STAKE);
        } else if (slot == CONFIRM_START_SLOT) {
            clickSound(player);
            startRun(player, st);
        } else if (slot == CONFIRM_CANCEL_SLOT) {
            clickSound(player);
            player.closeInventory();
        }
    }

    private static void clickSound(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 1.0f, 1.2f);
    }

    // ================= 种子聊天输入 =================

    private void promptSeed(Player player) {
        pendingSeeds.put(player.getUniqueId(), System.currentTimeMillis() + SEED_INPUT_TIMEOUT_MS);
        player.closeInventory();
        clickSound(player);
        player.sendMessage("§e请在聊天框输入本局种子（60 秒内有效）；输入 §fcancel§e 取消。");
        player.sendMessage("§7仅允许字母/数字/下划线/连字符，长度 1~32。当前输入会被吞掉不会广播。");
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Long expiry = pendingSeeds.get(id);
        if (expiry == null) {
            return;
        }
        if (System.currentTimeMillis() > expiry) {
            // 已超时：按普通聊天放行
            pendingSeeds.remove(id);
            return;
        }
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (msg.startsWith("/")) {
            // 命令不吞（让玩家可以正常执行其他命令），但结束本次种子输入等待
            pendingSeeds.remove(id);
            return;
        }
        e.setCancelled(true);
        pendingSeeds.remove(id);
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
            openMenu(player, MenuType.CONFIRM);
        }
    }

    // ================= 开局 =================

    private void startRun(Player player, GuiState st) {
        if (plugin.sessionManager().isActive(player)) {
            // 双保险：命令层也挡，但 GUI 打开期间玩家可能用命令开了局
            player.sendMessage("§c你已在一局中，先用 /balatro quit。");
            player.closeInventory();
            return;
        }
        GuiState.StartRequest req = st.toStartRequest();
        player.closeInventory();
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
