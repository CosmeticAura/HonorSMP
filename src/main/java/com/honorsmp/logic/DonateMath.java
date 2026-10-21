package com.honorsmp.logic;

import com.honorsmp.HonorData;

/** Pure /donate rules: hunger value of food, honor conversion, carry-over and daily cap. */
public final class DonateMath {
    private DonateMath() {}

    public record Quote(int honor, int newCarry, int remainingToday, boolean capReached) {}

    private static final String CAKE = "minecraft:cake";

    public static boolean donatable(String itemId, int nutritionOrMinus1, HonorConfig cfg) {
        return nutritionOrMinus1 >= 0 || CAKE.equals(itemId);
    }

    public static int hungerValue(String itemId, int nutritionOrMinus1, int count, HonorConfig cfg) {
        if (!donatable(itemId, nutritionOrMinus1, cfg)) return -1;
        if (cfg.donateZeroFoods != null && cfg.donateZeroFoods.contains(itemId)) return 0;
        if (CAKE.equals(itemId) && nutritionOrMinus1 < 0) return cfg.donateCakeHunger * count;
        if (CAKE.equals(itemId)) return cfg.donateCakeHunger * count;
        return nutritionOrMinus1 * count;
    }

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

    public static Quote quote(long hungerInChest, HonorData.PlayerRecord r, String today, HonorConfig cfg) {
        int per = Math.max(1, cfg.honorPerHungerPoints);
        long total = Math.max(0, hungerInChest) + r.donateCarry;
        long raw = total / per;
        int remaining = remainingToday(r, today, cfg);
        int honor = (int) Math.min(raw, remaining);
        return new Quote(honor, (int) (total % per), remaining, remaining == 0 || raw >= remaining);
    }

    public static boolean chestFull(long hungerInChest, HonorData.PlayerRecord r, String today, HonorConfig cfg) {
        return quote(hungerInChest, r, today, cfg).capReached();
    }

    public static void apply(Quote q, HonorData.PlayerRecord r, String today) {
        rollDay(r, today);
        r.donatedHonorToday += q.honor();
        r.donateCarry = q.newCarry();
    }
}
