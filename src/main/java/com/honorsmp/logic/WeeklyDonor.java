package com.honorsmp.logic;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Pure rules for the weekly top donor: the Monday 00:00 boundaries and who won the window before it. */
public final class WeeklyDonor {
    private WeeklyDonor() {}

    public record Award(String player, long time, int amount) {}

    public record Winner(String player, long total, long reachedAt) {}

    /** The most recent Monday 00:00 (in the given zone) at or before now, in epoch ms. */
    public static long mondayStart(long nowMs, ZoneId zone) {
        LocalDate day = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return day.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** The Monday 00:00 one week before the given Monday (calendar days, so daylight saving does not skew it). */
    public static long previousWeek(long mondayMs, ZoneId zone) {
        return Instant.ofEpochMilli(mondayMs).atZone(zone).toLocalDate().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** The Monday 00:00 one week after the given Monday. */
    public static long nextWeek(long mondayMs, ZoneId zone) {
        return Instant.ofEpochMilli(mondayMs).atZone(zone).toLocalDate().plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** True when the weekly run for the most recent Monday has not happened yet. */
    public static boolean due(long lastRunMonday, long nowMs, ZoneId zone) {
        return lastRunMonday < mondayStart(nowMs, zone);
    }

    /**
     * Winner among awards with from <= time < to: highest total, ties to whoever reached that total first (the time of
     * their last award in the window), then by uuid for a stable result. Excluded players (judges) and totals of 0 or
     * less never win. Null when nobody qualifies.
     */
    public static Winner select(List<Award> awards, Predicate<String> excluded, long from, long to) {
        Map<String, long[]> sums = new LinkedHashMap<>(); // player -> {total, reachedAt}
        for (Award a : awards) {
            if (a.player() == null || a.amount() <= 0 || a.time() < from || a.time() >= to || excluded.test(a.player())) continue;
            long[] s = sums.computeIfAbsent(a.player(), k -> new long[] {0, 0});
            s[0] += a.amount();
            s[1] = Math.max(s[1], a.time());
        }
        List<Winner> all = new ArrayList<>();
        for (var e : sums.entrySet()) all.add(new Winner(e.getKey(), e.getValue()[0], e.getValue()[1]));
        return all.stream()
                .min(Comparator.comparingLong(Winner::total).reversed()
                        .thenComparingLong(Winner::reachedAt).thenComparing(Winner::player))
                .orElse(null);
    }
}
