package com.honorsmp.logic;

/**
 * State machine of one running siege: WARNING -> WAVE (1..waves) -> WAVE_DELAY -> WAVE ... -> WON, or LOST.
 * Pure: the game side feeds it the situation once per second and does what the returned action says.
 *
 * The loss timers (abandon, max minutes) start when wave 1 starts, not during the warning.
 */
public final class SiegeFlow {
    public enum Phase { WARNING, WAVE, WAVE_DELAY, WON, LOST }

    public enum Action {
        NONE,
        /** The caller must spawn wave {@link #wave()} now. */
        START_WAVE,
        /** The wave just ended; more waves follow after the delay (the caller may announce it). */
        WAVE_CLEARED,
        WON,
        LOST
    }

    private final int waves;
    private final int warnSeconds;
    private final int delaySeconds;
    private final int abandonSeconds;
    private final int maxMinutes;

    private Phase phase = Phase.WARNING;
    private int wave = 0;
    private final long warnStartMs;
    private long startMs = 0;
    private long lastNearMs = 0;
    private long delayStartMs = 0;

    public SiegeFlow(long nowMs, int warnSeconds, int waves, int delaySeconds, int abandonSeconds, int maxMinutes) {
        this.warnStartMs = nowMs;
        this.warnSeconds = Math.max(0, warnSeconds);
        this.waves = Math.max(1, waves);
        this.delaySeconds = Math.max(0, delaySeconds);
        this.abandonSeconds = abandonSeconds;
        this.maxMinutes = maxMinutes;
    }

    public Phase phase() {
        return phase;
    }

    /** Current wave (1-based), 0 while still in the warning. */
    public int wave() {
        return wave;
    }

    public int waves() {
        return waves;
    }

    public boolean finished() {
        return phase == Phase.WON || phase == Phase.LOST;
    }

    public boolean running() {
        return phase == Phase.WAVE || phase == Phase.WAVE_DELAY;
    }

    public long startMs() {
        return startMs;
    }

    /** True once the warning countdown is over (the siege starts as soon as enough members are near). */
    public boolean warningOver(long nowMs) {
        return nowMs - warnStartMs >= warnSeconds * 1000L;
    }

    /**
     * @param membersNearHome   enough members are near the home to begin (only used in WARNING)
     * @param anyMemberNearAbandon at least one member is within the abandon radius (only used once running)
     * @param mobsLeft          living siege mobs of the current wave
     */
    public Action tick(long nowMs, boolean membersNearHome, boolean anyMemberNearAbandon, int mobsLeft) {
        switch (phase) {
            case WARNING:
                if (warningOver(nowMs) && membersNearHome) {
                    phase = Phase.WAVE;
                    wave = 1;
                    startMs = nowMs;
                    lastNearMs = nowMs;
                    return Action.START_WAVE;
                }
                return Action.NONE;
            case WAVE:
            case WAVE_DELAY:
                if (anyMemberNearAbandon) {
                    lastNearMs = nowMs;
                }
                if (phase == Phase.WAVE && mobsLeft <= 0) {
                    if (wave >= waves) {
                        phase = Phase.WON;
                        return Action.WON;
                    }
                    if (lost(nowMs)) {
                        return lose();
                    }
                    phase = Phase.WAVE_DELAY;
                    delayStartMs = nowMs;
                    return Action.WAVE_CLEARED;
                }
                if (lost(nowMs)) {
                    return lose();
                }
                if (phase == Phase.WAVE_DELAY && nowMs - delayStartMs >= delaySeconds * 1000L) {
                    wave++;
                    phase = Phase.WAVE;
                    return Action.START_WAVE;
                }
                return Action.NONE;
            default:
                return Action.NONE;
        }
    }

    private boolean lost(long nowMs) {
        return SiegeLimits.endsAsLoss(nowMs, startMs, lastNearMs, abandonSeconds, maxMinutes);
    }

    private Action lose() {
        phase = Phase.LOST;
        return Action.LOST;
    }

    /** Judge stop, civ disbanded, home lost, server stop: ends as a loss (no reward). */
    public void stop() {
        if (!finished()) {
            phase = Phase.LOST;
        }
    }
}
