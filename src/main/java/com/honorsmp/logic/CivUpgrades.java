package com.honorsmp.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure rules for civ upgrades: ids, levels, costs and effect values. Level 0 = not owned. */
public final class CivUpgrades {
    private CivUpgrades() {}

    public static final int MAX_LEVEL = 2;

    public enum Upgrade {
        RESISTANCE("resistance", "Resistance"),
        STRENGTH("strength", "Strength"),
        SPEED("speed", "Speed"),
        HASTE("haste", "Haste"),
        FORTITUDE("fortitude", "Fortitude");

        public final String id;
        public final String display;

        Upgrade(String id, String display) {
            this.id = id;
            this.display = display;
        }

        /** Null when the id is unknown (case-insensitive). */
        public static Upgrade byId(String id) {
            if (id == null) return null;
            for (Upgrade u : values()) if (u.id.equalsIgnoreCase(id)) return u;
            return null;
        }
    }

    /** Why a purchase is refused; OK = allowed. */
    public enum BuyCheck { OK, DISABLED, MAX_LEVEL, UPKEEP_UNPAID, PAUSED, NOT_ENOUGH_ITEMS }

    private static int at(int[] a, int level, int fallback) {
        if (level < 1) return 0;
        if (a == null || a.length == 0) return fallback;
        return a[Math.min(level, a.length) - 1];
    }

    private static double at(double[] a, int level, double fallback) {
        if (level < 1) return 0;
        if (a == null || a.length == 0) return fallback;
        return a[Math.min(level, a.length) - 1];
    }

    public static final String DIAMOND = "minecraft:diamond";

    /** Item an upgrade is paid in (daily upkeep and purchase). */
    public static String itemOf(HonorConfig c, Upgrade u) {
        String id = c.civUpgradeItems == null ? null : c.civUpgradeItems.get(u.id);
        if (id == null || id.isBlank()) id = HonorConfig.defaultUpgradeItems().get(u.id);
        return id.contains(":") ? id.trim().toLowerCase(java.util.Locale.ROOT) : "minecraft:" + id.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Items per day for owning {@code level} (1 or 2) of the upgrade; 0 for level 0. */
    public static int upkeepPerDay(HonorConfig c, Upgrade u, int level) {
        if (level < 1) return 0;
        int[] a = c.civUpkeepPerDay == null ? null : c.civUpkeepPerDay.get(u.id);
        if (a == null || a.length == 0) a = HonorConfig.defaultUpkeepPerDay().get(u.id);
        return Math.max(0, a[Math.min(level, a.length) - 1]);
    }

    /** Upgrade item needed to buy {@code level}: civBuyDays days of that level's upkeep. */
    public static int buyItemCost(HonorConfig c, Upgrade u, int level) { return Math.max(0, c.civBuyDays) * upkeepPerDay(c, u, level); }

    /** Diamonds to buy {@code level}: only level 2 asks for them. */
    public static int diamondCost(HonorConfig c, int level) { return level >= 2 ? Math.max(0, c.civLevel2Diamonds) : 0; }

    /** Everything buying {@code level} takes, item id to amount (the upgrade item and diamonds, merged if the same). */
    public static Map<String, Integer> buyCost(HonorConfig c, Upgrade u, int level) {
        Map<String, Integer> m = new LinkedHashMap<>();
        int n = buyItemCost(c, u, level);
        if (n > 0) m.merge(itemOf(c, u), n, Integer::sum);
        int d = diamondCost(c, level);
        if (d > 0) m.merge(DIAMOND, d, Integer::sum);
        return m;
    }

    /** Damage reduction in percent at that level (0 for level 0). */
    public static int resistancePercent(HonorConfig c, int level) { return at(c.upgradeResistancePercent, level, level == 1 ? 5 : 10); }

    /** Extra attack damage. */
    public static double strengthBonus(HonorConfig c, int level) { return at(c.upgradeStrengthBonus, level, level == 1 ? 0.5 : 1.0); }

    /** Extra speed in percent. */
    public static int speedPercent(HonorConfig c, int level) { return at(c.upgradeSpeedPercent, level, level == 1 ? 5 : 10); }

    /** Haste level to display (1 = Haste I); amplifier is this minus 1. 0 when not owned. */
    public static int hasteLevel(HonorConfig c, int level) { return at(c.upgradeHasteLevel, level, level); }

    /** Extra max health in HP. */
    public static int fortitudeHp(HonorConfig c, int level) { return at(c.upgradeFortitudeHp, level, level == 1 ? 2 : 4); }

    /**
     * Checks buying the next level of an upgrade.
     * @param current level now owned (0..MAX_LEVEL)
     * @param late true when the civ's upkeep is late; buying is blocked until it is paid
     * @param have item id to amount the buyer carries (plain items only)
     */
    public static BuyCheck canBuy(HonorConfig c, Upgrade u, int current, boolean late, Map<String, Integer> have) {
        return canBuy(c, u, current, late, false, have);
    }

    /** 1.6.9: {@code late} and {@code paused} are about THIS upgrade only; either one blocks buying its next level. */
    public static BuyCheck canBuy(HonorConfig c, Upgrade u, int current, boolean late, boolean paused, Map<String, Integer> have) {
        if (!c.civUpgradesEnabled) return BuyCheck.DISABLED;
        if (current >= MAX_LEVEL) return BuyCheck.MAX_LEVEL;
        if (paused) return BuyCheck.PAUSED;
        if (late) return BuyCheck.UPKEEP_UNPAID;
        return missing(buyCost(c, u, current + 1), have) == null ? BuyCheck.OK : BuyCheck.NOT_ENOUGH_ITEMS;
    }

    /** First item id of {@code need} that {@code have} does not cover, or null when everything is there. */
    public static String missing(Map<String, Integer> need, Map<String, Integer> have) {
        for (var e : need.entrySet()) if (have.getOrDefault(e.getKey(), 0) < e.getValue()) return e.getKey();
        return null;
    }
}
