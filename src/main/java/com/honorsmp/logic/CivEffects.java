package com.honorsmp.logic;

import com.honorsmp.logic.CivUpgrades.Upgrade;

/** Pure helpers for civ upgrade effects and the upgrades menu. No Minecraft types. */
public final class CivEffects {
    private CivEffects() {}

    /** Effect values for one player. Zero everywhere = nothing applied. */
    public record Values(int resistancePercent, double strengthBonus, int speedPercent, int hasteLevel, int fortitudeHp) {
        public static final Values NONE = new Values(0, 0, 0, 0, 0);
    }

    /** Active levels in {@link Upgrade#values()} order (resistance, strength, speed, haste, fortitude); all 0 when switched off. */
    public static Values values(HonorConfig c, boolean active, int resistance, int strength, int speed, int haste, int fortitude) {
        if (!active) return Values.NONE;
        return new Values(CivUpgrades.resistancePercent(c, resistance), CivUpgrades.strengthBonus(c, strength),
                CivUpgrades.speedPercent(c, speed), CivUpgrades.hasteLevel(c, haste), CivUpgrades.fortitudeHp(c, fortitude));
    }

    /** Multiplier for incoming damage: 10 percent -> 0.9. Clamped to 0..1. */
    public static float damageMultiplier(int resistancePercent) {
        return Math.max(0f, Math.min(1f, 1f - resistancePercent / 100f));
    }

    // menu layout (3 rows)
    public static final int INFO_SLOT = 4;
    public static final int FIRST_SLOT = 11;

    public static int slotOf(Upgrade u) { return FIRST_SLOT + u.ordinal(); }

    /** Upgrade shown in that slot, null when the slot holds none. */
    public static Upgrade upgradeAt(int slot) {
        int i = slot - FIRST_SLOT;
        Upgrade[] all = Upgrade.values();
        return i >= 0 && i < all.length ? all[i] : null;
    }

    /** "2d 5h", "3h 10m", "5m"; negative values are shown without sign. */
    public static String duration(long ms) {
        long m = Math.abs(ms) / 60_000L;
        long d = m / 1440, h = (m % 1440) / 60, mm = m % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + mm + "m";
        return Math.max(1, mm) + "m";
    }

    /** Short description of one upgrade level, e.g. "5% less damage taken". Level 0 gives "none". */
    public static String describe(HonorConfig c, Upgrade u, int level) {
        if (level < 1) return "none";
        return switch (u) {
            case RESISTANCE -> CivUpgrades.resistancePercent(c, level) + "% less damage taken";
            case STRENGTH -> "+" + trim(CivUpgrades.strengthBonus(c, level)) + " attack damage";
            case SPEED -> "+" + CivUpgrades.speedPercent(c, level) + "% movement speed";
            case HASTE -> "Haste " + (CivUpgrades.hasteLevel(c, level) <= 1 ? "I" : "II");
            case FORTITUDE -> "+" + CivUpgrades.fortitudeHp(c, level) + " max health";
        };
    }

    private static String trim(double d) { return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d); }
}
