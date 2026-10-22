package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivEffects;
import com.honorsmp.logic.CivUpgrades;
import com.honorsmp.logic.CivUpgrades.BuyCheck;
import com.honorsmp.logic.CivUpgrades.Upgrade;
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

    /** Plain items only: no custom name, enchants or other component changes. */
    static boolean plain(ItemStack s, Item item) {
        return s.isOf(item) && s.getComponentChanges().isEmpty();
    }

    public static Wallet wallet(ServerPlayerEntity p) {
        return new Wallet() {
            @Override public int emeralds() { return count(p, Items.EMERALD); }
            @Override public int diamonds() { return count(p, Items.DIAMOND); }
            @Override public void take(int e, int d) {
                remove(p, Items.EMERALD, e);
                remove(p, Items.DIAMOND, d);
            }
        };
    }

    private static int count(ServerPlayerEntity p, Item item) {
        PlayerInventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) if (plain(inv.getStack(i), item)) n += inv.getStack(i).getCount();
        return n;
    }

    private static void remove(ServerPlayerEntity p, Item item, int amount) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36 && amount > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (!plain(s, item)) continue;
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
        long owed = CivUpgradeApi.taxOwed(civ, h.config);
        if (owed <= 0) {
            info.add(gray("No upgrades yet, so no tax."));
        } else {
            info.add(gray("Weekly tax: " + owed + " emeralds"));
            long due = CivUpgradeApi.taxDueAt(civ);
            info.add(late ? Text.literal("UNPAID since " + date(due) + ": upgrades are off. /civ tax pay").formatted(Formatting.RED)
                    : gray("Due: " + date(due) + " (in " + CivEffects.duration(due - now) + ")"));
        }
        info.add(gray(manage ? "Click an upgrade to buy its next level." : "Only the leader or officers can buy upgrades."));
        m.icon(CivEffects.INFO_SLOT, Menu.icon(Items.EMERALD, Text.literal("Civ upgrades").formatted(Formatting.GREEN), info));

        for (Upgrade u : Upgrade.values()) {
            int lvl = CivUpgradeApi.ownedLevel(civ, u);
            boolean max = lvl >= CivUpgrades.MAX_LEVEL;
            List<Text> lore = new ArrayList<>();
            lore.add(gray("Level: " + lvl + " / " + CivUpgrades.MAX_LEVEL + (lvl > 0 && late ? " (off: tax unpaid)" : "")));
            lore.add(gray("Now: " + CivEffects.describe(h.config, u, lvl)));
            if (max) {
                lore.add(Text.literal("Max level").formatted(Formatting.GREEN));
            } else {
                int next = lvl + 1;
                lore.add(gray("Next: " + CivEffects.describe(h.config, u, next)));
                int em = CivUpgrades.emeraldCost(h.config, next), di = CivUpgrades.diamondCost(h.config, next);
                lore.add(Text.literal("Cost: " + em + " emeralds" + (di > 0 ? ", " + di + " diamonds" : "")).formatted(Formatting.YELLOW));
                if (manage) lore.add(Text.literal(late ? "Pay the tax first" : "Click to buy").formatted(late ? Formatting.RED : Formatting.GREEN));
            }
            ItemStack icon = Menu.icon(iconOf(u), Text.literal(u.display).formatted(lvl > 0 && !late ? Formatting.GREEN : Formatting.WHITE), lore);
            m.set(CivEffects.slotOf(u), icon, (who, r, s) -> onUpgradeClick(who, u));
        }
        m.backOrClose(null);
        m.open(p);
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
            who.sendMessage(Text.literal("Pay the tax first: /civ tax pay").formatted(Formatting.RED), true);
            return;
        }
        confirm(who, civ, u, lvl + 1);
    }

    private static void confirm(ServerPlayerEntity p, Civ civ, Upgrade u, int next) {
        Honor h = Honor.I;
        int em = CivUpgrades.emeraldCost(h.config, next), di = CivUpgrades.diamondCost(h.config, next);
        Menu m = Menu.create(Text.literal("Buy " + u.display + " " + next + "?"), 3);
        m.icon(4, Menu.icon(iconOf(u), Text.literal(u.display + " level " + next).formatted(Formatting.GREEN), List.of(
                gray(CivEffects.describe(h.config, u, next)),
                Text.literal("Cost: " + em + " emeralds" + (di > 0 ? ", " + di + " diamonds" : "")).formatted(Formatting.YELLOW),
                gray("Adds " + h.config.taxPerLevelPerMemberPerWeek + " emeralds per member to the weekly tax."))));
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
                case NOT_ENOUGH_EMERALDS -> "Not enough emeralds";
                case NOT_ENOUGH_DIAMONDS -> "Not enough diamonds";
                case TAX_UNPAID -> "Pay the tax first: /civ tax pay";
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
