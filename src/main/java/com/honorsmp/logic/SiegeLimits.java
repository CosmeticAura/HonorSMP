package com.honorsmp.logic;

/** When a running siege is lost. */
public final class SiegeLimits {
    private SiegeLimits() {
    }

    /**
     * Loss if no member has been within the abandon radius for abandonSeconds, or the siege ran maxMinutes.
     * lastMemberNearMs is the last time at least one member was within siegeAbandonRadius (the caller updates it
     * while someone is near). Boundaries are inclusive: exactly 120 s / 15 min counts.
     */
    public static boolean endsAsLoss(long now, long startMs, long lastMemberNearMs, int abandonSeconds, int maxMinutes) {
        return now - lastMemberNearMs >= abandonSeconds * 1000L || now - startMs >= maxMinutes * 60_000L;
    }

    public static boolean abandoned(long now, long lastMemberNearMs, int abandonSeconds) {
        return now - lastMemberNearMs >= abandonSeconds * 1000L;
    }

    public static boolean timedOut(long now, long startMs, int maxMinutes) {
        return now - startMs >= maxMinutes * 60_000L;
    }
}
