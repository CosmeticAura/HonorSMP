package com.honorsmp.logic;

/** Pure civ tax rules (no Minecraft types) so they can be unit tested. All times are epoch ms. */
public final class TaxMath {
    public static final long DAY_MS = 86_400_000L;
    public static final long HOUR_MS = 3_600_000L;

    private TaxMath() {}

    public static long amountDue(int members, int perMember) {
        return (long) Math.max(0, members) * Math.max(0, perMember);
    }

    /** First due date for a civ with no stored date: interval after the later of creation and now. */
    public static long initialDue(long created, long now, int intervalDays) {
        return Math.max(created, now) + intervalDays * DAY_MS;
    }

    /** Late means past the due time and the current cycle is not paid. */
    public static boolean isLate(long now, long due, boolean paid) {
        return !paid && now > due;
    }

    /** Started days late: 0 on time, 1 from the moment it is overdue, 2 after 24h, and so on. */
    public static int daysLate(long now, long due) {
        if (now <= due) return 0;
        return (int) ((now - due + DAY_MS - 1) / DAY_MS);
    }

    /** Days of penalty not yet applied this cycle. */
    public static int penaltyDaysOwed(long now, long due, int alreadyApplied) {
        return Math.max(0, daysLate(now, due) - Math.max(0, alreadyApplied));
    }

    /** Warn once per cycle when unpaid, not yet due and inside the warning window. */
    public static boolean shouldWarn(long now, long due, boolean paid, boolean alreadyWarned, int warnHours) {
        return !paid && !alreadyWarned && now < due && due - now <= warnHours * HOUR_MS;
    }

    /** Login reminder: unpaid and either late or inside the warning window. */
    public static boolean needsReminder(long now, long due, boolean paid, int warnHours) {
        return !paid && (now > due || due - now <= warnHours * HOUR_MS);
    }

    /** Due date after paying a late cycle: one interval after the old due date, but never in the past. */
    public static long nextDueAfterLatePay(long due, long now, int intervalDays) {
        long next = due + intervalDays * DAY_MS;
        return next <= now ? now + intervalDays * DAY_MS : next;
    }

    /** Rolls a paid cycle forward by whole intervals until the due date is in the future. */
    public static long rollForward(long due, long now, int intervalDays) {
        long step = Math.max(1, intervalDays) * DAY_MS;
        if (due > now) return due;
        long n = (now - due) / step + 1;
        return due + n * step;
    }
}
