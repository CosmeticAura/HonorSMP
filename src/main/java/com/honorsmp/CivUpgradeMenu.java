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
        boolean late = CivUpgradeApi.isLate(civ, now);
        Menu m = Menu.create(Text.literal("Upgrades: " + civ.name), 3);

        List<Text> info = new ArrayList<>();
        Map<String, Integer> owed = CivUpgradeApi.owedPerDay(civ, h.config);
        if (owed.isEmpty()) {
            info.add(gray("No upgrades yet, so no upkeep."));
        } else {
            info.add(gray("Daily upkeep: " + CivUpkeep.describe(owed)));
            long due = CivUpgradeApi.upkeepDueAt(civ);
            info.add(late ? Text.literal("UNPAID since " + date(due) + ": upgrades are off. /civ upkeep pay").formatted(Formatting.RED)
                    : gray("Paid until: " + date(due) + " (in " + CivEffects.duration(due - now) + ")"));
        }
        info.add(gray(manage ? "Click an upgrade to buy its next level." : "Only the leader or officers can buy upgrades."));
        m.icon(CivEffects.INFO_SLOT, Menu.icon(Items.GOLDEN_PICKAXE, Text.literal("Civ upgrades").formatted(Formatting.GREEN), info));

        for (Upgrade u : Upgrade.values()) {
            int lvl = CivUpgradeApi.ownedLevel(civ, u);
            boolean max = lvl >= CivUpgrades.MAX_LEVEL;
            List<Text> lore = new ArrayList<>();
            lore.add(gray("Level: " + lvl + " / " + CivUpgrades.MAX_LEVEL + (lvl > 0 && late ? " (off: upkeep unpaid)" : "")));
            lore.add(gray("Now: " + CivEffects.describe(h.config, u, lvl)));
            if (max) {
                lore.add(Text.literal("Max level").formatted(Formatting.GREEN));
            } else {
                int next = lvl + 1;
                lore.add(gray("Next: " + CivEffects.describe(h.config, u, next)));
                lore.add(Text.literal("Cost: " + CivUpkeep.describe(CivUpgrades.buyCost(h.config, u, next))).formatted(Formatting.YELLOW));
                lore.add(gray("Upkeep after buying: " + CivUpgrades.upkeepPerDay(h.config, u, next) + " " + CivUpkeep.pretty(CivUpgrades.itemOf(h.config, u)) + " a day"));
                if (manage) lore.add(Text.literal(late ? "Pay the upkeep first" : "Click to buy").formatted(late ? Formatting.RED : Formatting.GREEN));
            }
            ItemStack icon = Menu.icon(iconOf(u), Text.literal(u.display).formatted(lvl > 0 && !late ? Formatting.GREEN : Formatting.WHITE), lore);
            m.set(CivEffects.slotOf(u), icon, (who, r, s) -> onUpgradeClick(who, u));
        }
        addRowsIcon(m, civ, manage);
        m.backOrClose(null);
        m.open(p);
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
        if (CivUpgradeApi.isLate(civ, System.currentTimeMillis())) {
            who.sendMessage(Text.literal("Pay the upkeep first: /civ upkeep pay").formatted(Formatting.RED), true);
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
                case UPKEEP_UNPAID -> "Pay the upkeep first: /civ upkeep pay";
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
