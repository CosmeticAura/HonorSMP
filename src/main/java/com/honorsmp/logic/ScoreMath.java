package com.honorsmp.logic;

public final class ScoreMath {
    private ScoreMath() {}

    public static int clamp(long score, HonorConfig c) {
        if (score > c.maxScore) return c.maxScore;
        if (score < c.minScore) return c.minScore;
        return (int) score;
    }

    /** New score after adding delta, clamped to the configured range. */
    public static int apply(int score, int delta, HonorConfig c) {
        return clamp((long) score + delta, c);
    }

    /**
     * Applies a delta that is limited by a per-day budget. Returns the delta actually allowed.
     * @param used amount of the budget already used today (positive number)
     * @param cap  max budget per day (positive number)
     * @param perUnit penalty per action (negative number)
     */
    public static int dailyCapped(int used, int cap, int perUnit) {
        int cost = Math.abs(perUnit);
        int left = cap - used;
        if (left <= 0) return 0;
        int allowed = Math.min(cost, left);
        return perUnit < 0 ? -allowed : allowed;
    }
}
