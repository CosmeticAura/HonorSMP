package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivChestRows;
import com.honorsmp.logic.CivEffects;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import com.honorsmp.logic.UpgradeUi;
import com.honorsmp.logic.UpgradeUi.Action;
import com.honorsmp.logic.UpgradeUi.Status;
import net.minecraft.registry.Registries;
import com.honorsmp.logic.Wallet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** /civ upgrades (1.7.0): the one menu for upgrades, upkeep and chest rows (see {@link Menu}). Items are only taken in {@link #buy} and {@link #buyRow}, after a confirm click. */
public final class CivUpgradeMenu {
    private CivUpgradeMenu() {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE d MMM HH:mm").withZone(ZoneId.systemDefault());

    public static String date(long ms) { return DATE.format(Instant.ofEpochMilli(ms)); }

    // ------------------------------------------------------------ wallet over the player inventory

    public static String idOf(ItemStack s) { return Registries.ITEM.getId(s.getItem()).toString(); }

    /** Plain items of this id in the main inventory (36 slots). */
    public static int count(ServerPlayerEntity p, String itemId) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty() && s.getComponentChanges().isEmpty() && idOf(s).equals(itemId)) n += s.getCount();
        }
        return n;
    }

    public static Wallet wallet(ServerPlayerEntity p) {
        return new Wallet() {
            @Override public int count(String itemId) { return CivUpgradeMenu.count(p, itemId); }
            @Override public void take(String itemId, int amount) { remove(p, itemId, amount); }
        };
    }

    private static void remove(ServerPlayerEntity p, String itemId, int amount) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36 && amount > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty() || !s.getComponentChanges().isEmpty() || !idOf(s).equals(itemId)) continue;
            int t = Math.min(amount, s.getCount());
            s.decrement(t);
            amount -= t;
        }
        inv.markDirty();
    }

    // ------------------------------------------------------------ menu (1.7.0: one menu for upgrades, upkeep and chest rows)

    private static final UpgradeUi.Confirm CONFIRM = new UpgradeUi.Confirm();

    private static final int BUY_SLOT = 11, PAY_SLOT = 13, PAUSE_SLOT = 15, BACK_SLOT = 18;

    private static Item iconOf(Upgrade u) {
        return switch (u) {
            case RESISTANCE -> Items.IRON_CHESTPLATE;
            case STRENGTH -> Items.IRON_SWORD;
            case SPEED -> Items.SUGAR;
            case HASTE -> Items.GOLDEN_PICKAXE;
            case FORTITUDE -> Items.GOLDEN_APPLE;
        };
    }

    private static Text gray(String s) { return Text.literal(s).formatted(Formatting.GRAY); }

    private static Formatting colorOf(Status s) {
        return switch (s) {
            case ON -> Formatting.GREEN;
            case PAUSED -> Formatting.YELLOW;
            case UNPAID -> Formatting.RED;
            case NOT_BOUGHT -> Formatting.GRAY;
        };
    }

    private static void deny(ServerPlayerEntity who) {
        who.sendMessage(Text.literal(UpgradeUi.ONLY_MANAGERS).formatted(Formatting.RED), true);
    }

    /** The lore shared by an upgrade's icon on the main page and its header on its own page. */
    private static List<Text> summaryLore(UpgradeView v) {
        List<Text> lore = new ArrayList<>();
        lore.add(gray("Level " + v.level() + " of " + CivUpgrades.MAX_LEVEL));
        lore.add(gray(v.does()));
        lore.add(Text.literal("Status: " + v.status().label).formatted(colorOf(v.status())));
        if (v.status() != Status.NOT_BOUGHT) lore.add(gray("Paid until: " + v.paidUntil()));
        lore.add(gray("Daily cost: " + v.dailyCost()));
        return lore;
    }

    /** Main page: one icon per upgrade, one for chest rows. */
    public static void open(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null) {
            p.sendMessage(Text.literal("You are not in a civilization.").formatted(Formatting.RED));
            return;
        }
        CONFIRM.clear(p.getUuid());
        long now = System.currentTimeMillis();
        boolean manage = CivUpgradeApi.canManage(civ, p.getUuid().toString());
        Menu m = Menu.create(Text.literal("Upgrades: " + civ.name), 3);

        List<Text> info = new ArrayList<>();
        if (!h.config.civUpgradesEnabled) {
            info.add(Text.literal("Civ upgrades are disabled").formatted(Formatting.RED));
        } else if (!CivUpgradeApi.hasUpgrades(civ)) {
            info.add(gray("No upgrades yet, so no upkeep."));
        } else {
            info.add(gray("Daily upkeep (running upgrades): " + CivUpkeep.describe(CivUpgradeApi.owedPerDay(civ, h.config))));
            List<Upgrade> lateOnes = CivUpgradeApi.lateUpgrades(civ, now);
            if (!lateOnes.isEmpty()) info.add(Text.literal("UNPAID: " + names(lateOnes) + " (off until paid)").formatted(Formatting.RED));
        }
        info.add(gray("Click an upgrade to buy, pay or pause it."));
        if (!manage) info.add(gray("Only the leader or officers can use the buttons."));
        m.icon(CivEffects.INFO_SLOT, Menu.icon(Items.GOLDEN_PICKAXE, Text.literal("Civ upgrades").formatted(Formatting.GREEN), info));

        for (Upgrade u : Upgrade.values()) {
            UpgradeView v = UpgradeView.of(civ, u, h.config, now, manage);
            List<Text> lore = summaryLore(v);
            lore.add(Text.literal(v.clickHint()).formatted(Formatting.YELLOW));
            m.set(CivEffects.slotOf(u), Menu.icon(iconOf(u), Text.literal(u.display).formatted(v.status() == Status.NOT_BOUGHT ? Formatting.WHITE : colorOf(v.status())), lore),
                    (who, r, s) -> openUpgrade(who, u));
        }
        addRowsIcon(m, civ, manage);
        m.backOrClose(null);
        m.open(p);
    }

    private static String names(List<Upgrade> list) {
        List<String> n = new ArrayList<>();
        for (Upgrade u : list) n.add(u.display);
        return String.join(", ", n);
    }

    private static ItemStack button(Item item, String name, Formatting color, boolean armed, List<Text> lore) {
        List<Text> l = new ArrayList<>(lore);
        if (armed) l.add(Text.literal("Click again within 5 seconds").formatted(Formatting.YELLOW));
        return Menu.icon(armed ? Items.YELLOW_CONCRETE : item,
                Text.literal(armed ? "Click again to confirm" : name).formatted(armed ? Formatting.YELLOW : color), l);
    }

    /** One upgrade's own page: only the buttons that apply, plus Back. */
    public static void openUpgrade(ServerPlayerEntity p, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null) return;
        long now = System.currentTimeMillis();
        boolean manage = CivUpgradeApi.canManage(civ, p.getUuid().toString());
        UpgradeView v = UpgradeView.of(civ, u, h.config, now, manage);
        Menu m = Menu.create(Text.literal(u.display + ": " + civ.name), 3);
        List<Text> head = summaryLore(v);
        if (!manage) head.add(gray("Only the leader or officers can use the buttons."));
        if (!h.config.civUpgradesEnabled) head.add(Text.literal("Civ upgrades are disabled").formatted(Formatting.RED));
        m.icon(CivEffects.INFO_SLOT, Menu.icon(iconOf(u), Text.literal(u.display).formatted(colorOf(v.status())), head));

        java.util.UUID id = p.getUuid();
        for (Action a : v.actions()) {
            switch (a) {
                case BUY -> {
                    int next = v.level() + 1;
                    String name = v.level() == 0 ? "Buy level 1" : "Upgrade to level " + next;
                    boolean armed = CONFIRM.armed(id, "buy:" + u.id, now);
                    m.set(BUY_SLOT, button(Items.LIME_CONCRETE, name, Formatting.GREEN, armed, List.of(
                            gray(CivEffects.describe(h.config, u, next)),
                            Text.literal("Price: " + CivUpkeep.describe(CivUpgrades.buyCost(h.config, u, next))).formatted(Formatting.YELLOW),
                            gray("Daily cost after: " + CivUpgrades.upkeepPerDay(h.config, u, next) + " " + CivUpkeep.pretty(CivUpgrades.itemOf(h.config, u))),
                            gray("Taken from your inventory"))), (who, r, s) -> onBuyClick(who, u));
                }
                case PAY -> m.set(PAY_SLOT, Menu.icon(Items.CHEST, Text.literal("Pay 1 day").formatted(Formatting.GREEN), List.of(
                        gray("Daily cost: " + CivUpkeep.describe(CivUpgradeApi.upgradeUpkeepPerDay(civ, u, h.config))),
                        gray("Opens the pay chest. Put the items in and close it."),
                        gray("You can pay up to " + v.payRoom() + (v.payRoom() == 1 ? " day" : " days") + " now."))),
                        (who, r, s) -> onPayClick(who, u));
                case RESUME -> m.set(PAY_SLOT, Menu.icon(Items.CHEST, Text.literal("Resume").formatted(Formatting.GREEN), List.of(
                        gray("Same as paying: opens the pay chest."),
                        gray("Daily cost: " + CivUpkeep.describe(CivUpgradeApi.upgradeUpkeepPerDay(civ, u, h.config))),
                        gray("Paying switches " + u.display + " back on."))),
                        (who, r, s) -> onPayClick(who, u));
                case PAUSE -> {
                    boolean armed = CONFIRM.armed(id, "pause:" + u.id, now);
                    m.set(PAUSE_SLOT, button(Items.ORANGE_CONCRETE, "Pause", Formatting.GOLD, armed, List.of(
                            gray("Turns " + u.display + " off and stops the cost."),
                            gray("The level is kept. Resume to turn it on again."))), (who, r, s) -> onPauseClick(who, u));
                }
            }
        }
        if (v.actions().isEmpty() && manage && h.config.civUpgradesEnabled) {
            m.icon(PAY_SLOT, Menu.icon(Items.GRAY_STAINED_GLASS_PANE, Text.literal("Nothing to do right now").formatted(Formatting.GRAY), List.of(
                    gray("Max level and paid the most days ahead."))));
        }
        m.set(BACK_SLOT, Menu.icon(Items.ARROW, "Back", Formatting.WHITE), (who, r, s) -> open(who));
        m.open(p);
    }

    private static void onBuyClick(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) { deny(who); return; }
        if (!CONFIRM.click(who.getUuid(), "buy:" + u.id, System.currentTimeMillis())) {
            openUpgrade(who, u); // button now reads "Click again to confirm"
            return;
        }
        buy(who, u);
    }

    private static void onPayClick(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) { deny(who); return; }
        who.closeHandledScreen();
        String e = h.upkeepPay.openFor(who, civ, u);
        if (e != null) {
            who.sendMessage(Text.literal(e).formatted(Formatting.RED));
            openUpgrade(who, u);
        }
    }

    private static void onPauseClick(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) { deny(who); return; }
        if (!CONFIRM.click(who.getUuid(), "pause:" + u.id, System.currentTimeMillis())) {
            openUpgrade(who, u);
            return;
        }
        String err = pauseUpgrade(h, civ, who, u);
        if (err != null) who.sendMessage(Text.literal(err).formatted(Formatting.RED), true);
        openUpgrade(who, u);
    }

    /** Pauses one upgrade for a leader or officer; returns an error message or null. */
    public static String pauseUpgrade(Honor h, Civ civ, ServerPlayerEntity who, Upgrade u) {
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) return UpgradeUi.ONLY_MANAGERS;
        if (CivUpgradeApi.ownedLevel(civ, u) <= 0) return "Your civ does not own " + u.display;
        if (CivUpgradeApi.isPaused(civ, u)) return u.display + " is already paused";
        CivUpgradeApi.pause(civ, u, System.currentTimeMillis());
        h.markDirty();
        CivPerks.syncCiv(h, civ);
        for (String id : civ.members) {
            ServerPlayerEntity mm = h.online(id);
            if (mm != null) mm.sendMessage(Text.literal("[" + civ.name + "] " + who.getName().getString() + " paused " + u.display
                    + ". It is off and costs nothing until you resume it in /civ upgrades.").formatted(Formatting.GOLD));
        }
        return null;
    }

    private static void buy(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null || !CivUpgradeApi.canManage(civ, who.getUuid().toString())) {
            who.closeHandledScreen();
            return;
        }
        var res = CivUpgradeApi.buy(civ, u, h.config, System.currentTimeMillis(), wallet(who));
        if (res.ok()) {
            h.markDirty();
            CivPerks.syncCiv(h, civ);
            for (String id : civ.members) {
                ServerPlayerEntity mm = h.online(id);
                if (mm != null) mm.sendMessage(Text.literal("[" + civ.name + "] " + who.getName().getString() + " bought " + u.display + " "
                        + res.newLevel() + " (" + CivEffects.describe(h.config, u, res.newLevel()) + ")").formatted(Formatting.GOLD));
            }
        } else {
            String msg = switch (res.check()) {
                case NOT_ENOUGH_ITEMS -> "Not enough " + CivUpkeep.pretty(res.missing());
                case UPKEEP_UNPAID -> "Pay " + u.display + "'s upkeep first (Pay 1 day on this page)";
                case PAUSED -> u.display + " is paused: Resume it first (on this page)";
                case MAX_LEVEL -> u.display + " is already at max level";
                case DISABLED -> "Civ upgrades are disabled";
                default -> "Cannot buy that";
            };
            who.sendMessage(Text.literal(msg).formatted(Formatting.RED), true);
            who.sendMessage(Text.literal(msg + " (only plain, unnamed, unenchanted items count)").formatted(Formatting.RED));
        }
        openUpgrade(who, u); // refresh
    }

    // ------------------------------------------------------------ chest rows (1.6.8; own page since 1.7.0)

    private static void addRowsIcon(Menu m, Civ civ, boolean manage) {
        Honor h = Honor.I;
        int bought = CivUpgradeApi.chestRows(civ);
        int next = CivChestRows.nextRow(bought);
        List<Text> lore = new ArrayList<>();
        lore.add(gray("Rows owned: " + bought + " of " + CivChestRows.TOTAL_ROWS));
        lore.add(gray(CivChestRows.infoLine(bought)));
        if (next == 0) lore.add(Text.literal("All rows bought").formatted(Formatting.GREEN));
        else {
            lore.add(gray("Next row: " + CivUpkeep.describe(CivChestRows.costOf(h.config.civChestRowCosts, next))));
            lore.add(gray("Bought once, no daily upkeep."));
        }
        lore.add(Text.literal(!manage ? "Click to look. Only the leader or officers can buy." : next == 0 ? "Click to look" : "Click to buy the next row").formatted(Formatting.YELLOW));
        m.set(CivChestRows.MENU_SLOT, Menu.icon(Items.CHEST, Text.literal("Chest rows").formatted(next == 0 ? Formatting.GREEN : Formatting.WHITE), lore),
                (who, r, s) -> openRows(who));
    }

    /** Chest rows page: rows owned, Buy next row (with price), Back. */
    public static void openRows(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null) return;
        boolean manage = CivUpgradeApi.canManage(civ, p.getUuid().toString());
        int bought = CivUpgradeApi.chestRows(civ);
        int next = CivChestRows.nextRow(bought);
        Menu m = Menu.create(Text.literal("Chest rows: " + civ.name), 3);
        List<Text> head = new ArrayList<>();
        head.add(gray("Rows owned: " + bought + " of " + CivChestRows.TOTAL_ROWS));
        head.add(gray(CivChestRows.infoLine(bought)));
        if (!manage) head.add(gray("Only the leader or officers can buy rows."));
        m.icon(CivEffects.INFO_SLOT, Menu.icon(Items.CHEST, Text.literal("Chest rows").formatted(Formatting.GREEN), head));
        if (next != 0) {
            boolean armed = CONFIRM.armed(p.getUuid(), "rows", System.currentTimeMillis());
            m.set(PAY_SLOT, button(Items.LIME_CONCRETE, "Buy next row", Formatting.GREEN, armed, List.of(
                    gray("Row " + next + " adds 9 slots to the civ chest."),
                    Text.literal("Price: " + CivUpkeep.describe(CivChestRows.costOf(h.config.civChestRowCosts, next))).formatted(Formatting.YELLOW),
                    gray("Bought once, no daily upkeep."), gray("Taken from your inventory"))), (who, r, s) -> onRowsClick(who));
        }
        m.set(BACK_SLOT, Menu.icon(Items.ARROW, "Back", Formatting.WHITE), (who, r, s) -> open(who));
        m.open(p);
    }

    private static void onRowsClick(ServerPlayerEntity who) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) { deny(who); return; }
        if (CivChestRows.nextRow(CivUpgradeApi.chestRows(civ)) == 0) {
            who.sendMessage(Text.literal("All 6 chest rows are already bought").formatted(Formatting.RED), true);
            return;
        }
        if (!CONFIRM.click(who.getUuid(), "rows", System.currentTimeMillis())) {
            openRows(who);
            return;
        }
        buyRow(who);
    }

    private static void buyRow(ServerPlayerEntity who) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null || !CivUpgradeApi.canManage(civ, who.getUuid().toString())) {
            who.closeHandledScreen();
            return;
        }
        var res = CivUpgradeApi.buyChestRow(civ, h.config, wallet(who));
        if (res.ok()) {
            // paid items go to the judges' treasury, in stacks
            for (var e : res.taken().entrySet()) {
                var item = Registries.ITEM.get(net.minecraft.util.Identifier.of(e.getKey()));
                int left = e.getValue();
                while (left > 0) {
                    int n = Math.min(left, new ItemStack(item).getMaxCount());
                    h.treasury.addItem(new ItemStack(item, n));
                    left -= n;
                }
            }
            h.markDirty();
            h.civChest.rowsChanged(civ); // anyone with the chest open gets it closed so the new row shows
            for (String id : civ.members) {
                ServerPlayerEntity mm = h.online(id);
                if (mm != null) mm.sendMessage(Text.literal("[" + civ.name + "] " + who.getName().getString() + " bought a civ chest row ("
                        + CivChestRows.infoLine(res.bought()) + ")").formatted(Formatting.GOLD));
            }
            h.saveData();
        } else {
            String msg = switch (res.status()) {
                case NOT_ENOUGH_ITEMS -> "Not enough " + CivUpkeep.pretty(res.missing());
                case ALL_BOUGHT -> "All 6 chest rows are already bought";
                default -> "Cannot buy that row yet";
            };
            who.sendMessage(Text.literal(msg).formatted(Formatting.RED), true);
            who.sendMessage(Text.literal(msg + " (only plain, unnamed, unenchanted items count)").formatted(Formatting.RED));
        }
        openRows(who); // refresh
    }
}
