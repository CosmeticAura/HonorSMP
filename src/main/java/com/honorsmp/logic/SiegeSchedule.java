package com.honorsmp.logic;

import java.util.Random;

/** When sieges happen. All times are epoch milliseconds. */
public final class SiegeSchedule {
    public static final long DAY_MS = 24L * 60 * 60 * 1000;

    private SiegeSchedule() {
    }

    /** A random time minDays..maxDays (real days) after lastSiegeMs. */
    public static long rollNext(long lastSiegeMs, Random rnd, double minDays, double maxDays) {
        double lo = Math.max(0, minDays);
        double hi = Math.max(lo, maxDays);
        double days = lo + (hi - lo) * rnd.nextDouble();
        return lastSiegeMs + Math.round(days * DAY_MS);
    }

    /**
     * The schedule to use now: if nextMs is 0 (new civ, or data from before sieges existed) roll from now, so that
     * upgrading never raids anybody immediately. Otherwise unchanged. Caller stores the result when it differs.
     */
    public static long ensureScheduled(long now, long nextMs, Random rnd, double minDays, double maxDays) {
        return nextMs <= 0 ? rollNext(now, rnd, minDays, maxDays) : nextMs;
    }

    /** True when the scheduled time has come. An unscheduled civ (0) is never due. */
    public static boolean due(long now, long nextMs) {
        return nextMs > 0 && now >= nextMs;
    }

    /** A siege starts only when due, the civ has a home and enough members are near it. Otherwise it keeps waiting. */
    public static boolean shouldStart(long now, long nextMs, boolean hasHome, int membersNearHome, int minMembers) {
        return due(now, nextMs) && hasHome && membersNearHome >= minMembers;
    }

    /** True from warnSeconds before the start until the start (the "siege incoming" warning window). */
    public static boolean inWarning(long now, long nextMs, int warnSeconds) {
        return nextMs > 0 && now < nextMs && now >= nextMs - warnSeconds * 1000L;
    }

    /** Next siege after one ended (win or loss): rolled from the end time. */
    public static long rollAfterEnd(long endMs, Random rnd, double minDays, double maxDays) {
        return rollNext(endMs, rnd, minDays, maxDays);
    }
}
