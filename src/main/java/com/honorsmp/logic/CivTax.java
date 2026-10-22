package com.honorsmp.logic;

/**
 * Pure math for the weekly civ upgrade tax. All times are epoch ms; {@code due == 0} means no clock is running.
 *
 * <ul>
 * <li>Owed = totalLevels * members * taxPerLevelPerMemberPerWeek. Zero levels (or no members) owe nothing.</li>
 * <li>The first upgrade bought starts the clock: due = now + interval ({@link #firstDue}). Buying more later keeps
 *     the same due date. Losing every level (disband / reset) clears the clock (due = 0).</li>
 * <li>Late = levels &gt; 0 and now &gt;= due. While late, all upgrades are off ({@link #upgradesActive}); nobody loses honor.</li>
 * <li>Only ONE period is ever owed, however many weeks were missed: debts do not stack.</li>
 * <li>Paying is allowed when late or when due within the warning window. Paying while late sets due = now + interval
 *     (missed weeks are forgiven). Paying early (inside the window) sets due = oldDue + interval, so the days left
 *     are not lost. Either way the upgrades are active again immediately.</li>
 * <li>The 24h warning is due when levels &gt; 0, not late, and due - now &lt;= warning window.</li>
 * </ul>
 */
public final class CivTax {
    private CivTax() {}

    private static final long HOUR = 3_600_000L;
    private static final long DAY = 24 * HOUR;

    public static long intervalMs(HonorConfig c) { return Math.max(1, c.taxIntervalDays) * DAY; }

    public static long warningMs(HonorConfig c) { return Math.max(0, c.taxWarningHours) * HOUR; }

    public static long owed(HonorConfig c, int totalLevels, int members) {
        if (totalLevels <= 0 || members <= 0) return 0;
        return (long) totalLevels * members * Math.max(0, c.taxPerLevelPerMemberPerWeek);
    }

    /** Due time when the first upgrade is bought at {@code now}. */
    public static long firstDue(HonorConfig c, long now) { return now + intervalMs(c); }

    public static boolean isLate(int totalLevels, long due, long now) {
        return totalLevels > 0 && due > 0 && now >= due;
    }

    /** Upgrades work when owned and the tax is not late. */
    public static boolean upgradesActive(HonorConfig c, int totalLevels, long due, long now) {
        return c.civUpgradesEnabled && totalLevels > 0 && !isLate(totalLevels, due, now);
    }

    public static boolean warningDue(HonorConfig c, int totalLevels, long due, long now) {
        return totalLevels > 0 && due > 0 && !isLate(totalLevels, due, now) && due - now <= warningMs(c);
    }

    /** Whether /civ tax pay is accepted: something is owed and it is late or inside the warning window. */
    public static boolean canPay(HonorConfig c, int totalLevels, int members, long due, long now) {
        return owed(c, totalLevels, members) > 0 && due > 0 && now >= due - warningMs(c);
    }

    /** New due date after a successful payment (see class comment). */
    public static long dueAfterPay(HonorConfig c, long due, long now) {
        return now >= due ? now + intervalMs(c) : due + intervalMs(c);
    }

    /** Milliseconds until due (negative when late). */
    public static long msUntilDue(long due, long now) { return due - now; }
}
