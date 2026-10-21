package com.honorsmp.logic;

import com.honorsmp.HonorData;

/** Pure /donate rules: honor conversion, carry-over and daily cap. */
public final class DonateMath {
    private DonateMath() {}

    public record Quote(int honor, int newCarry, int remainingToday, boolean capReached) {}

    public static String todayKey(java.time.LocalDate d) {
        return d.toString();
    }

    public static void rollDay(HonorData.PlayerRecord r, String today) {
        if (!today.equals(r.donateDayKey)) {
            r.donateDayKey = today;
            r.donatedHonorToday = 0;
        }
    }

    public static int remainingToday(HonorData.PlayerRecord r, String today, HonorConfig cfg) {
        int used = today.equals(r.donateDayKey) ? r.donatedHonorToday : 0;
        return Math.max(0, cfg.donateDailyCap - used);
    }

    /**
     * Honor for {@code points} donated points. The daily cap never blocks donating: points above what
     * is still allowed today are dropped (carry 0). Judges earn nothing and their carry is untouched.
     */
    public static Quote quote(long points, HonorData.PlayerRecord r, String today, HonorConfig cfg, boolean judge) {
        int remaining = remainingToday(r, today, cfg);
        if (judge) return new Quote(0, r.donateCarry, remaining, remaining == 0);
        int per = Math.max(1, cfg.honorPerHungerPoints);
        long total = Math.max(0, points) + r.donateCarry;
        long raw = total / per;
        if (raw > remaining) return new Quote(remaining, 0, remaining, true);
        return new Quote((int) raw, (int) (total % per), remaining, raw >= remaining);
    }

    public static void apply(Quote q, HonorData.PlayerRecord r, String today) {
        rollDay(r, today);
        r.donatedHonorToday += q.honor();
        r.donateCarry = q.newCarry();
    }
}
