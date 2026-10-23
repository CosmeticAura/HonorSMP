package com.honorsmp.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure math for the daily civ upgrade upkeep (1.6.5, replaces the weekly emerald tax). Times are epoch ms;
 * {@code due == 0} means no clock is running.
 *
 * <ul>
 * <li>Owed per day = for every owned upgrade level, that upgrade's item times its per-day amount (config).</li>
 * <li>The first upgrade bought starts the clock: due = now + 24h ({@link #firstDue}). Buying more later keeps the same
 *     due date. Losing every level (disband / reset) clears it.</li>
 * <li>Late = levels &gt; 0 and now &gt;= due. While late, all upgrades are off; nobody loses honor. Only ONE day is
 *     ever owed however long it was late: missed days are forgiven.</li>
 * <li>Each paid day moves the due date 24h on, counted from the old due date, or from now when late. Paying is
 *     capped so the paid-through time never goes beyond now + {@code civUpkeepMaxAheadDays} (3).</li>
 * <li>The warning is due when levels &gt; 0, not late, and due - now &lt;= civUpkeepWarningHours (2h).</li>
 * </ul>
 */
public final class CivUpkeep {
    private CivUpkeep() {}

    public static final long HOUR = 3_600_000L;
    public static final long DAY = 24 * HOUR;

    public static long warningMs(HonorConfig c) { return Math.max(0, c.civUpkeepWarningHours) * HOUR; }

    public static long maxAheadMs(HonorConfig c) { return Math.max(1, c.civUpkeepMaxAheadDays) * DAY; }

    /** Due time when the first upgrade is bought at {@code now}. */
    public static long firstDue(long now) { return now + DAY; }

    public static boolean isLate(int totalLevels, long due, long now) {
        return totalLevels > 0 && due > 0 && now >= due;
    }

    /** Upgrades work when owned and the upkeep is not late. */
    public static boolean upgradesActive(HonorConfig c, int totalLevels, long due, long now) {
        return c.civUpgradesEnabled && totalLevels > 0 && !isLate(totalLevels, due, now);
    }

    public static boolean warningDue(HonorConfig c, int totalLevels, long due, long now) {
        return totalLevels > 0 && due > 0 && !isLate(totalLevels, due, now) && due - now <= warningMs(c);
    }

    /** Whole days that can still be paid right now, 0 when already paid the maximum ahead (or nothing is owed). */
    public static int payableDays(HonorConfig c, int totalLevels, long due, long now) {
        if (totalLevels <= 0 || due <= 0) return 0;
        long base = Math.max(due, now);
        long room = now + maxAheadMs(c) - base;
        return room < DAY ? 0 : (int) (room / DAY);
    }

    /** New due date after paying {@code days} whole days. */
    public static long dueAfterPay(long due, long now, int days) { return Math.max(due, now) + days * DAY; }

    /** Days the offered items cover (each owed item needs its amount per day), at most {@code cap}. 0 if nothing is owed. */
    public static int coveredDays(Map<String, Integer> perDay, Map<String, Integer> offered, int cap) {
        if (perDay == null || perDay.isEmpty() || cap <= 0) return 0;
        int days = cap;
        for (var e : perDay.entrySet()) {
            if (e.getValue() <= 0) continue;
            int have = offered == null ? 0 : offered.getOrDefault(e.getKey(), 0);
            days = Math.min(days, have / e.getValue());
        }
        return days;
    }

    /** perDay times days. */
    public static Map<String, Integer> cost(Map<String, Integer> perDay, int days) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (var e : perDay.entrySet()) if (e.getValue() > 0) out.put(e.getKey(), e.getValue() * days);
        return out;
    }

    /** Clock for a civ that has upgrades but no upkeep date yet (existing civs after the 1.6.5 update): 24h from now. */
    public static long migrateDue(int totalLevels, long due, long now) {
        return totalLevels > 0 && due <= 0 ? now + DAY : due;
    }

    /** "minecraft:lapis_lazuli" to "lapis lazuli". */
    public static String pretty(String itemId) {
        if (itemId == null) return "";
        int i = itemId.indexOf(':');
        return (i >= 0 ? itemId.substring(i + 1) : itemId).replace('_', ' ');
    }

    /** "32 redstone, 16 iron ingot". */
    public static String describe(Map<String, Integer> items) {
        StringBuilder sb = new StringBuilder();
        for (var e : items.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getValue()).append(' ').append(pretty(e.getKey()));
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }
}
