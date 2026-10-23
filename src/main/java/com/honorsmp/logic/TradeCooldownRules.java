package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 1.6.4 (replaces the NoTradeCooldown mod): villager trades never lock, except when what the villager gives is on
 * the tradeCooldownItems list, is enchanted gear, or is something /donate accepts. tradeCooldownAlwaysFree wins
 * over all of that. Pure rules; the Minecraft side only passes in the id, the tag ids and two facts about the stack.
 */
public final class TradeCooldownRules {
    private TradeCooldownRules() {}

    /** "emerald" and "minecraft:emerald" are the same; "#swords" and "#minecraft:swords" too. Null/blank gives "". */
    public static String norm(String s) {
        if (s == null) return "";
        String t = s.trim();
        boolean tag = t.startsWith("#");
        if (tag) t = t.substring(1).trim();
        if (t.isEmpty()) return "";
        if (t.indexOf(':') < 0) t = "minecraft:" + t;
        return (tag ? "#" : "") + t.toLowerCase(java.util.Locale.ROOT);
    }

    /** True when the list has this item id, or has "#tag" for any of the item's tags. */
    public static boolean matches(Collection<String> list, String itemId, Collection<String> tagIds) {
        if (list == null || list.isEmpty()) return false;
        String id = norm(itemId);
        for (String entry : list) {
            String e = norm(entry);
            if (e.isEmpty()) continue;
            if (e.startsWith("#")) {
                if (tagIds == null) continue;
                for (String t : tagIds) if (e.equals("#" + norm(t))) return true;
            } else if (e.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Should this trade keep vanilla behaviour (use count goes up, it locks after maxUses and restocks)?
     * false = the trade never locks.
     *
     * @param sellItemId    registry id of what the villager gives
     * @param tagIds        item tag ids of that stack (without '#')
     * @param nutrition     food hunger value of the stack, -1 when it has none
     * @param enchantedGear true when the stack is a tool, weapon or armor piece that carries enchantments
     */
    public static boolean locks(String sellItemId, Collection<String> tagIds, int nutrition, boolean enchantedGear, HonorConfig cfg) {
        if (!cfg.noTradeCooldown) return true; // feature off: plain vanilla
        if (matches(cfg.tradeCooldownAlwaysFree, sellItemId, tagIds)) return false;
        if (matches(cfg.tradeCooldownItems, sellItemId, tagIds)) return true;
        if (enchantedGear) return true;
        // anything /donate accepts today (read from the donation rules, so new donation items are covered)
        return DonationRules.pointsPerItem(norm(sellItemId), nutrition, true, cfg) >= 0;
    }

    // ------------------------------------------------------------ defaults

    public static List<String> defaultBlocklist() {
        List<String> l = new ArrayList<>();
        l.add("minecraft:emerald");
        l.add("minecraft:diamond");
        for (String id : new String[] {"golden_apple", "enchanted_golden_apple", "golden_carrot", "totem_of_undying", "ender_pearl",
                "name_tag", "saddle"}) l.add("minecraft:" + id);
        // every ore, raw ore, ingot, block and gem of the 1.6.0 donation list (no nuggets in it)
        for (String id : HonorConfig.defaultMaterialPoints().keySet()) if (!l.contains(id)) l.add(id);
        // every diamond and netherite tool, weapon and armor piece
        for (String mat : new String[] {"diamond", "netherite"}) {
            for (String part : new String[] {"sword", "pickaxe", "axe", "shovel", "hoe", "spear", "helmet", "chestplate", "leggings",
                    "boots", "horse_armor", "nautilus_armor"}) l.add("minecraft:" + mat + "_" + part);
        }
        return l;
    }

    public static List<String> defaultAlwaysFree() {
        return new ArrayList<>(List.of("minecraft:enchanted_book", "minecraft:experience_bottle", "minecraft:ender_pearl"));
    }
}
