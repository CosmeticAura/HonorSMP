package com.honorsmp.logic;

/** Pure rules for the honorable-side rank perks (max health, keep on death, gold name). */
public final class RankPerks {
    private RankPerks() {}

    private static int at(int[] a, int i, int fallback) {
        return a == null || i < 0 || i >= a.length ? fallback : a[i];
    }

    /** Extra max health in HP (2 = one heart). 0 for anyone not on the honorable side. */
    public static int healthBonus(Tier t, HonorConfig c) {
        if (t == null || !t.isHonorable()) return 0;
        return Math.max(0, at(c.healthBonusHp, t.perkIndex(), 0));
    }

    private static boolean atLeast(Tier t, int minRank) {
        return t != null && t.isHonorable() && minRank > 0 && t.rank >= minRank;
    }

    public static boolean keepsXp(Tier t, HonorConfig c) { return atLeast(t, c.keepXpMinRank); }
    public static boolean keepsHotbar(Tier t, HonorConfig c) { return atLeast(t, c.keepHotbarMinRank); }
    public static boolean goldName(Tier t, HonorConfig c) { return atLeast(t, c.goldNameMinRank); }

    /** Health after the max dropped: never above the new max. */
    public static float clampHealth(float health, float max) { return Math.min(health, max); }
}
