package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivChestRows;
import com.honorsmp.logic.CivEffects;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.CivUpkeep;
import java.util.Map;
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

/** /civ upgrades: read-only chest menu (see {@link Menu}) plus a confirm screen. Items are only taken in {@link #buy}. */
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

    // ------------------------------------------------------------ menu

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

    public static void open(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null) {
            p.sendMessage(Text.literal("You are not in a civilization.").formatted(Formatting.RED));
            return;
        }
        long now = System.currentTimeMillis();
        boolean manage = CivUpgradeApi.canManage(civ, p.getUuid().toString());
        Menu m = Menu.create(Text.literal("Upgrades: " + civ.name), 3);

        List<Text> info = new ArrayList<>();
        Map<String, Integer> owed = CivUpgradeApi.owedPerDay(civ, h.config);
        if (!CivUpgradeApi.hasUpgrades(civ)) {
            info.add(gray("No upgrades yet, so no upkeep."));
        } else {
            info.add(gray("Daily upkeep (all running upgrades): " + CivUpkeep.describe(owed)));
            info.add(gray("Each upgrade has its own upkeep date: see the upkeep button."));
            List<Upgrade> lateOnes = CivUpgradeApi.lateUpgrades(civ, now);
            if (!lateOnes.isEmpty()) info.add(Text.literal("UNPAID: " + names(lateOnes) + " (off until paid)").formatted(Formatting.RED));
        }
        info.add(gray(manage ? "Click an upgrade to buy its next level." : "Only the leader or officers can buy upgrades."));
        m.icon(CivEffects.INFO_SLOT, Menu.icon(Items.GOLDEN_PICKAXE, Text.literal("Civ upgrades").formatted(Formatting.GREEN), info));

        for (Upgrade u : Upgrade.values()) {
            int lvl = CivUpgradeApi.ownedLevel(civ, u);
            boolean max = lvl >= CivUpgrades.MAX_LEVEL;
            boolean late = CivUpgradeApi.isLate(civ, u, now);
            boolean paused = CivUpgradeApi.isPaused(civ, u);
            List<Text> lore = new ArrayList<>();
            lore.add(gray("Level: " + lvl + " / " + CivUpgrades.MAX_LEVEL + (lvl > 0 && late ? " (off: upkeep unpaid)" : lvl > 0 && paused ? " (off: paused)" : "")));
            if (lvl > 0) lore.add(upkeepLine(civ, u, now));
            lore.add(gray("Now: " + CivEffects.describe(h.config, u, lvl)));
            if (max) {
                lore.add(Text.literal("Max level").formatted(Formatting.GREEN));
            } else {
                int next = lvl + 1;
                lore.add(gray("Next: " + CivEffects.describe(h.config, u, next)));
                lore.add(Text.literal("Cost: " + CivUpkeep.describe(CivUpgrades.buyCost(h.config, u, next))).formatted(Formatting.YELLOW));
                lore.add(gray("Upkeep after buying: " + CivUpgrades.upkeepPerDay(h.config, u, next) + " " + CivUpkeep.pretty(CivUpgrades.itemOf(h.config, u)) + " a day"));
                if (manage) lore.add(Text.literal(late ? "Pay this upgrade's upkeep first" : paused ? "Paused: pay its upkeep to switch it on" : "Click to buy")
                        .formatted(late || paused ? Formatting.RED : Formatting.GREEN));
            }
            ItemStack icon = Menu.icon(iconOf(u), Text.literal(u.display).formatted(lvl > 0 && !late && !paused ? Formatting.GREEN : Formatting.WHITE), lore);
            m.set(CivEffects.slotOf(u), icon, (who, r, s) -> onUpgradeClick(who, u));
        }
        addRowsIcon(m, civ, manage);
        if (CivUpgradeApi.hasUpgrades(civ)) {
            m.set(UPKEEP_SLOT, Menu.icon(Items.CLOCK, Text.literal("Upgrade upkeep").formatted(Formatting.GOLD), List.of(
                    gray("Pay or pause each upgrade on its own."),
                    gray(manage ? "Click to open" : "Only the leader or officers can pay."))), (who, r, s) -> openUpkeep(who));
        }
        m.backOrClose(null);
        m.open(p);
    }

    private static final int UPKEEP_SLOT = 26;

    private static String names(List<Upgrade> list) {
        List<String> n = new ArrayList<>();
        for (Upgrade u : list) n.add(u.display);
        return String.join(", ", n);
    }

    /** One line about an owned upgrade's own upkeep: paused, late, or paid until. */
    public static Text upkeepLine(Civ civ, Upgrade u, long now) {
        if (CivUpgradeApi.isPaused(civ, u)) return Text.literal("Upkeep: PAUSED (no cost). Paying turns it back on.").formatted(Formatting.YELLOW);
        long due = CivUpgradeApi.dueAt(civ, u);
        if (CivUpgradeApi.isLate(civ, u, now)) return Text.literal("UNPAID since " + date(due) + ": off until paid").formatted(Formatting.RED);
        if (due <= 0) return gray("Upkeep: not started");
        return gray("Paid until " + date(due) + " (in " + CivEffects.duration(due - now) + ")");
    }

    // ------------------------------------------------------------ upkeep menu (1.6.9): pay / pause one upgrade at a time

    public static void openUpkeep(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null) return;
        long now = System.currentTimeMillis();
        boolean manage = CivUpgradeApi.canManage(civ, p.getUuid().toString());
        Menu m = Menu.create(Text.literal("Upkeep: " + civ.name), 4);
        m.icon(CivEffects.INFO_SLOT, Menu.icon(Items.CLOCK, Text.literal("Upgrade upkeep").formatted(Formatting.GOLD), List.of(
                gray("Each upgrade is paid on its own."),
                gray("Click an upgrade to pay it (up to " + h.config.civUpkeepMaxAheadDays + " days ahead)."),
                gray("Pause an upgrade to stop its cost: it turns off, keeps its level."))));
        for (Upgrade u : Upgrade.values()) {
            int lvl = CivUpgradeApi.ownedLevel(civ, u);
            int slot = CivEffects.slotOf(u);
            if (lvl <= 0) {
                m.icon(slot, Menu.icon(Items.GRAY_STAINED_GLASS_PANE, Text.literal(u.display).formatted(Formatting.DARK_GRAY), List.of(gray("Not owned"))));
                continue;
            }
            boolean paused = CivUpgradeApi.isPaused(civ, u);
            boolean late = CivUpgradeApi.isLate(civ, u, now);
            List<Text> lore = new ArrayList<>();
            lore.add(gray("Level " + lvl + ": " + CivUpkeep.describe(CivUpgradeApi.upgradeUpkeepPerDay(civ, u, h.config)) + " a day"));
            lore.add(upkeepLine(civ, u, now));
            int room = CivUpgradeApi.payableDays(civ, u, h.config, now);
            lore.add(gray(room > 0 ? "Can pay up to " + room + (room == 1 ? " day" : " days") + " now" : "Paid the maximum ahead"));
            if (manage) lore.add(Text.literal(room > 0 ? (paused ? "Click to pay and switch on" : "Click to pay") : "Nothing to pay now").formatted(room > 0 ? Formatting.GREEN : Formatting.GRAY));
            m.set(slot, Menu.icon(iconOf(u), Text.literal(u.display).formatted(late ? Formatting.RED : paused ? Formatting.YELLOW : Formatting.GREEN), lore),
                    (who, r, s) -> onPayClick(who, u));
            if (paused) {
                m.icon(slot + 9, Menu.icon(Items.YELLOW_CONCRETE, Text.literal("Paused").formatted(Formatting.YELLOW), List.of(
                        gray("No cost, no effect, no warnings."), gray("Pay it to switch it on again."))));
            } else {
                m.set(slot + 9, Menu.icon(Items.ORANGE_CONCRETE, Text.literal("Pause " + u.display).formatted(Formatting.GOLD), List.of(
                        gray("Stops the upkeep and turns the upgrade off."), gray("The level is kept; paying turns it back on."),
                        gray(manage ? "Click to pause" : "Only the leader or officers can pause."))), (who, r, s) -> onPauseClick(who, u));
            }
        }
        m.set(27, Menu.icon(Items.ARROW, "Back", Formatting.WHITE), (who, r, s) -> open(who));
        m.open(p);
    }

    private static void onPayClick(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        who.closeHandledScreen();
        String e = h.upkeepPay.openFor(who, civ, u);
        if (e != null) {
            who.sendMessage(Text.literal(e).formatted(Formatting.RED));
            openUpkeep(who);
        }
    }

    private static void onPauseClick(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        String err = pauseUpgrade(h, civ, who, u);
        if (err != null) who.sendMessage(Text.literal(err).formatted(Formatting.RED), true);
        openUpkeep(who);
    }

    /** Pauses one upgrade for a leader or officer; returns an error message or null. Shared by the menu and /civ upkeep pause. */
    public static String pauseUpgrade(Honor h, Civ civ, ServerPlayerEntity who, Upgrade u) {
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) return "Only the leader or officers can pause an upgrade";
        if (CivUpgradeApi.ownedLevel(civ, u) <= 0) return "Your civ does not own " + u.display;
        if (CivUpgradeApi.isPaused(civ, u)) return u.display + " is already paused";
        CivUpgradeApi.pause(civ, u, System.currentTimeMillis());
        h.markDirty();
        CivPerks.syncCiv(h, civ);
        for (String id : civ.members) {
            ServerPlayerEntity mm = h.online(id);
            if (mm != null) mm.sendMessage(Text.literal("[" + civ.name + "] " + who.getName().getString() + " paused " + u.display
                    + ". It is off and costs nothing until you pay its upkeep again.").formatted(Formatting.GOLD));
        }
        return null;
    }

    // ------------------------------------------------------------ chest rows (1.6.8)

    private static void addRowsIcon(Menu m, Civ civ, boolean manage) {
        Honor h = Honor.I;
        int bought = CivUpgradeApi.chestRows(civ);
        int next = CivChestRows.nextRow(bought);
        List<Text> lore = new ArrayList<>();
        lore.add(gray(CivChestRows.infoLine(bought)));
        if (next == 0) {
            lore.add(Text.literal("All rows bought").formatted(Formatting.GREEN));
        } else {
            lore.add(Text.literal("Row " + next + " costs: " + CivUpkeep.describe(CivChestRows.costOf(h.config.civChestRowCosts, next))).formatted(Formatting.YELLOW));
            lore.add(gray("Bought once, no daily upkeep."));
            lore.add(manage ? Text.literal("Click to buy").formatted(Formatting.GREEN) : gray("Only the leader or officers can buy rows."));
        }
        m.set(CivChestRows.MENU_SLOT, Menu.icon(Items.CHEST, Text.literal("Chest rows").formatted(next == 0 ? Formatting.GREEN : Formatting.WHITE), lore),
                (who, r, s) -> onRowsClick(who));
    }

    private static void onRowsClick(ServerPlayerEntity who) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) {
            who.sendMessage(Text.literal("Only the leader or officers can buy chest rows").formatted(Formatting.RED), true);
            return;
        }
        int next = CivChestRows.nextRow(CivUpgradeApi.chestRows(civ));
        if (next == 0) {
            who.sendMessage(Text.literal("All 6 chest rows are already bought").formatted(Formatting.RED), true);
            return;
        }
        Menu m = Menu.create(Text.literal("Buy chest row " + next + "?"), 3);
        m.icon(4, Menu.icon(Items.CHEST, Text.literal("Chest row " + next).formatted(Formatting.GREEN), List.of(
                gray("Adds 9 slots to the civ chest."),
                Text.literal("Cost: " + CivUpkeep.describe(CivChestRows.costOf(h.config.civChestRowCosts, next))).formatted(Formatting.YELLOW),
                gray("Bought once, no daily upkeep."))));
        m.set(11, Menu.icon(Items.LIME_CONCRETE, "Confirm", Formatting.GREEN, "Take the cost from your inventory"), (w, r, s) -> buyRow(w));
        m.set(15, Menu.icon(Items.RED_CONCRETE, "Cancel", Formatting.RED), (w, r, s) -> open(w));
        m.open(who);
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
        open(who); // refresh
    }

    private static void onUpgradeClick(ServerPlayerEntity who, Upgrade u) {
        Honor h = Honor.I;
        if (h == null) return;
        Civ civ = h.civOf(who.getUuid().toString());
        if (civ == null) return;
        if (!CivUpgradeApi.canManage(civ, who.getUuid().toString())) {
            who.sendMessage(Text.literal("Only the leader or officers can buy upgrades").formatted(Formatting.RED), true);
            return;
        }
        int lvl = CivUpgradeApi.ownedLevel(civ, u);
        if (lvl >= CivUpgrades.MAX_LEVEL) {
            who.sendMessage(Text.literal(u.display + " is already at max level").formatted(Formatting.RED), true);
            return;
        }
        if (CivUpgradeApi.isPaused(civ, u)) {
            who.sendMessage(Text.literal(u.display + " is paused: pay its upkeep first (/civ upkeep pay " + u.id + ")").formatted(Formatting.RED), true);
            return;
        }
        if (CivUpgradeApi.isLate(civ, u, System.currentTimeMillis())) {
            who.sendMessage(Text.literal("Pay " + u.display + "'s upkeep first: /civ upkeep pay " + u.id).formatted(Formatting.RED), true);
            return;
        }
        confirm(who, civ, u, lvl + 1);
    }

    private static void confirm(ServerPlayerEntity p, Civ civ, Upgrade u, int next) {
        Honor h = Honor.I;
        Menu m = Menu.create(Text.literal("Buy " + u.display + " " + next + "?"), 3);
        m.icon(4, Menu.icon(iconOf(u), Text.literal(u.display + " level " + next).formatted(Formatting.GREEN), List.of(
                gray(CivEffects.describe(h.config, u, next)),
                Text.literal("Cost: " + CivUpkeep.describe(CivUpgrades.buyCost(h.config, u, next))).formatted(Formatting.YELLOW),
                gray("Daily upkeep for it: " + CivUpgrades.upkeepPerDay(h.config, u, next) + " " + CivUpkeep.pretty(CivUpgrades.itemOf(h.config, u)) + "."))));
        m.set(11, Menu.icon(Items.LIME_CONCRETE, "Confirm", Formatting.GREEN, "Take the cost from your inventory"),
                (who, r, s) -> buy(who, u));
        m.set(15, Menu.icon(Items.RED_CONCRETE, "Cancel", Formatting.RED), (who, r, s) -> open(who));
        m.open(p);
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
                case UPKEEP_UNPAID -> "Pay " + u.display + "'s upkeep first: /civ upkeep pay " + u.id;
                case PAUSED -> u.display + " is paused: pay its upkeep first: /civ upkeep pay " + u.id;
                case MAX_LEVEL -> u.display + " is already at max level";
                case DISABLED -> "Civ upgrades are disabled";
                default -> "Cannot buy that";
            };
            who.sendMessage(Text.literal(msg).formatted(Formatting.RED), true);
            who.sendMessage(Text.literal(msg + " (only plain, unnamed, unenchanted items count)").formatted(Formatting.RED));
        }
        open(who); // refresh
    }
}
