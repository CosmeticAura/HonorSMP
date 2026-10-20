package com.honorsmp.logic;

/** Honor tiers. rank is 1..3 inside a side (1 = lowest tier of that side), 0 for neutral. */
public enum Tier {
    PARAGON("Paragon", Side.HONORABLE, 3),
    NOBLE("Noble", Side.HONORABLE, 2),
    HONORABLE("Honorable", Side.HONORABLE, 1),
    NEUTRAL("Neutral", Side.NEUTRAL, 0),
    OUTLAW("Outlaw", Side.DISHONORED, 1),
    MARAUDER("Marauder", Side.DISHONORED, 2),
    WARLORD("Warlord", Side.DISHONORED, 3);

    public final String display;
    public final Side side;
    public final int rank;

    Tier(String display, Side side, int rank) {
        this.display = display;
        this.side = side;
        this.rank = rank;
    }

    public boolean isHonorable() { return side == Side.HONORABLE; }
    public boolean isDishonored() { return side == Side.DISHONORED; }
    public boolean isNobleOrHigher() { return this == NOBLE || this == PARAGON; }

    /** Index 0..2 into a per-tier perk array, or -1 for neutral. */
    public int perkIndex() { return side == Side.NEUTRAL ? -1 : rank - 1; }

    public static Tier of(int score, HonorConfig c) {
        if (score >= c.paragonMin) return PARAGON;
        if (score >= c.nobleMin) return NOBLE;
        if (score >= c.honorableMin) return HONORABLE;
        if (score <= c.warlordMax) return WARLORD;
        if (score <= c.marauderMax) return MARAUDER;
        if (score <= c.outlawMax) return OUTLAW;
        return NEUTRAL;
    }
}
