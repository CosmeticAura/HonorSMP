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

    /** The tier every rank perk is derived from. Judges get exactly the Paragon perks, never a dishonored side. */
    public static Tier effectiveTier(boolean isJudge, Tier t) {
        if (isJudge || t == Tier.JUDGE) return Tier.PARAGON;
        return t == null ? Tier.NEUTRAL : t;
    }

    /** Extra potion duration percent (beneficial effects). 0 unless honorable. */
    public static int potionBonusPercent(Tier t, HonorConfig c) {
        return t == null || !t.isHonorable() ? 0 : at(c.potionBonusPercent, t.perkIndex(), 0);
    }

    /** Villager trade discount percent. 0 unless honorable. */
    public static int tradeDiscountPercent(Tier t, HonorConfig c) {
        return t == null || !t.isHonorable() ? 0 : at(c.tradeDiscountPercent, t.perkIndex(), 0);
    }

    /** Rank part of the hunger slowdown percent. 0 unless honorable. */
    public static int hungerSlowPercent(Tier t, HonorConfig c) {
        return t == null || !t.isHonorable() ? 0 : at(c.hungerSlowPercent, t.perkIndex(), 0);
    }

    /** Bounty Compass: Noble and up. */
    public static boolean hasCompass(Tier t) { return t != null && t.isNobleOrHigher(); }

    private static boolean atLeast(Tier t, int minRank) {
        return t != null && t.isHonorable() && minRank > 0 && t.rank >= minRank;
    }

    public static boolean keepsXp(Tier t, HonorConfig c) { return atLeast(t, c.keepXpMinRank); }
    public static boolean keepsHotbar(Tier t, HonorConfig c) { return atLeast(t, c.keepHotbarMinRank); }
    public static boolean goldName(Tier t, HonorConfig c) { return atLeast(t, c.goldNameMinRank); }

    /** Health after the max dropped: never above the new max. */
    public static float clampHealth(float health, float max) { return Math.min(health, max); }
}
