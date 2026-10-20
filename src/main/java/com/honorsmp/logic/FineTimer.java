package com.honorsmp.logic;

/** Pure math for fine penalties: one penalty per whole interval a fine stays unpaid. */
public final class FineTimer {
    private FineTimer() {}

    public static final long HOUR_MS = 3_600_000L;

    public static long intervalMs(double hours) {
        return Math.max(60_000L, Math.round(hours * HOUR_MS));
    }

    /** The time the current interval started: the last penalty, or creation if none yet. */
    public static long baseline(long createdAt, long lastPenaltyAt) {
        return lastPenaltyAt > createdAt ? lastPenaltyAt : createdAt;
    }

    /** Whole intervals elapsed since the baseline. Judges are exempt (always 0). */
    public static long penaltiesDue(long createdAt, long lastPenaltyAt, long now, long intervalMs, boolean judge) {
        if (judge || intervalMs <= 0) return 0;
        long elapsed = now - baseline(createdAt, lastPenaltyAt);
        return elapsed < intervalMs ? 0 : elapsed / intervalMs;
    }

    /** New lastPenaltyAt after charging {@code due} penalties: advances by whole intervals so the remainder carries over. */
    public static long advance(long createdAt, long lastPenaltyAt, long due, long intervalMs) {
        return baseline(createdAt, lastPenaltyAt) + due * intervalMs;
    }

    /** When the next penalty falls due. */
    public static long nextPenaltyAt(long createdAt, long lastPenaltyAt, long intervalMs) {
        return baseline(createdAt, lastPenaltyAt) + intervalMs;
    }

    /** Total honor to remove for {@code due} penalties (never negative, never overflows int). */
    public static int totalLoss(long due, int penaltyEach) {
        long t = due * Math.max(0, penaltyEach);
        return (int) Math.min(t, 100_000L);
    }

    /** A judge cannot be fined (judges have no honor rules). */
    public static boolean canBeFined(boolean targetIsJudge) {
        return !targetIsJudge;
    }
}
