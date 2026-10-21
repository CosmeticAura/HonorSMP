package com.honorsmp.logic;

import com.honorsmp.HonorData;
import java.util.ArrayList;
import java.util.UUID;

/** Pure math for the automatic honor events (1.4.0): per-N counters, daily caps, boss once-a-day, owner check. */
public final class AutoRules {
    private AutoRules() {}

    /** New counter value and honor to give for one counted action. */
    public record Step(int count, int honor) {}

    public static String todayKey(java.time.LocalDate d) {
        return d.toString();
    }

    /** Resets every "today" field when the day changed (midnight server time). Progress counts carry over. */
    public static void rollDay(HonorData.PlayerRecord r, String today) {
        if (r.bossesToday == null) r.bossesToday = new ArrayList<>();
        if (!today.equals(r.autoDayKey)) {
            r.autoDayKey = today;
            r.cureHonorToday = 0;
            r.harvestHonorToday = 0;
            r.breedHonorToday = 0;
            r.tradeHonorToday = 0;
            r.bossesToday.clear();
        }
    }

    public static int remaining(int usedToday, int cap) {
        return Math.max(0, cap - usedToday);
    }

    /** Honor for a flat reward under a daily cap (partial if only part of it fits). */
    public static int capped(int amount, int usedToday, int cap) {
        if (amount <= 0) return 0;
        return Math.min(amount, remaining(usedToday, cap));
    }

    /**
     * One more action of a "honor per N actions" rule. Every {@code per}-th action pays {@code honor} (capped);
     * the counter restarts even when the cap blocked the payout, so it never piles up.
     */
    public static Step counter(int count, int per, int honor, int usedToday, int cap) {
        int n = count + 1;
        if (n < Math.max(1, per)) return new Step(n, 0);
        return new Step(0, capped(honor, usedToday, cap));
    }

    /** Wheat, carrots, potatoes: age 7. Beetroot and nether wart: age 3. Anything else never counts. */
    public static boolean matureCrop(String blockId, int age) {
        return switch (blockId) {
            case "minecraft:wheat", "minecraft:carrots", "minecraft:potatoes" -> age >= 7;
            case "minecraft:beetroots", "minecraft:nether_wart" -> age >= 3;
            default -> false;
        };
    }

    /** Boss paid once per type per player per day. Returns true and records it when it should pay. */
    public static boolean claimBoss(HonorData.PlayerRecord r, String bossType) {
        if (r.bossesToday == null) r.bossesToday = new ArrayList<>();
        if (r.bossesToday.contains(bossType)) return false;
        r.bossesToday.add(bossType);
        return true;
    }

    /** A pet kill costs honor only when the pet has an owner and it is somebody else. */
    public static boolean petPenalty(UUID killer, UUID owner) {
        return killer != null && owner != null && !owner.equals(killer);
    }

    /** Only a player killer (direct or through a projectile they shot) triggers the villager penalty. */
    public static boolean villagerPenalty(boolean victimIsVillagerOrTrader, boolean killerIsPlayer) {
        return victimIsVillagerOrTrader && killerIsPlayer;
    }
}
