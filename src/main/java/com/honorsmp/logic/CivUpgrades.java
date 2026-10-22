package com.honorsmp.logic;

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
    public enum BuyCheck { OK, DISABLED, MAX_LEVEL, TAX_UNPAID, NOT_ENOUGH_EMERALDS, NOT_ENOUGH_DIAMONDS }

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

    /** Emeralds to buy {@code level} (1 or 2). */
    public static int emeraldCost(HonorConfig c, int level) { return Math.max(0, at(c.upgradeEmeraldCost, level, level == 1 ? 32 : 64)); }

    /** Diamonds to buy {@code level} (1 or 2). */
    public static int diamondCost(HonorConfig c, int level) { return Math.max(0, at(c.upgradeDiamondCost, level, level == 1 ? 0 : 8)); }

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
     * @param taxLate true when the civ's tax is late; buying is blocked until it is paid
     */
    public static BuyCheck canBuy(HonorConfig c, int current, boolean taxLate, int emeralds, int diamonds) {
        if (!c.civUpgradesEnabled) return BuyCheck.DISABLED;
        if (current >= MAX_LEVEL) return BuyCheck.MAX_LEVEL;
        if (taxLate) return BuyCheck.TAX_UNPAID;
        int next = current + 1;
        if (emeralds < emeraldCost(c, next)) return BuyCheck.NOT_ENOUGH_EMERALDS;
        if (diamonds < diamondCost(c, next)) return BuyCheck.NOT_ENOUGH_DIAMONDS;
        return BuyCheck.OK;
    }
}
