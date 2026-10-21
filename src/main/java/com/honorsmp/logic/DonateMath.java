package com.honorsmp.logic;

import com.honorsmp.HonorData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Pure /donate rules: honor conversion, leftover-point carry-over, the overall daily cap and the per-type
 * daily caps ({@code donateItemDailyCaps}). Donations are never refused: points that cannot earn honor are dropped.
 *
 * <p>Types: "food" (all food combined), "golden_apple", "enchanted_golden_apple", "totem_of_undying",
 * "ender_pearl", or the bare id of any other configured extra item. See {@link DonationRules#category}.
 */
public final class DonateMath {
    private DonateMath() {}

    public static final String FOOD = "food";

    /**
     * @param honor          total honor awarded (sum of byType, already limited by every cap)
     * @param byType         honor awarded per type (only types in this donation)
     * @param newCarry       leftover points per donated type after this donation (0 for capped types)
     * @param remainingToday overall honor still earnable today before this donation
     * @param capReached     true when something was dropped or the overall cap is used up afterwards
     */
    public record Quote(int honor, Map<String, Integer> byType, Map<String, Integer> newCarry,
                        int remainingToday, boolean capReached) {}

    public static String todayKey(java.time.LocalDate d) {
        return d.toString();
    }

    /** Midnight reset: overall and per-type earned totals. Leftover points are kept (they are not honor). */
    public static void rollDay(HonorData.PlayerRecord r, String today) {
        if (!today.equals(r.donateDayKey)) {
            r.donateDayKey = today;
            r.donatedHonorToday = 0;
            r.donatedTypeToday = new LinkedHashMap<>();
        }
    }

    private static boolean isToday(HonorData.PlayerRecord r, String today) {
        return today.equals(r.donateDayKey);
    }

    public static int remainingToday(HonorData.PlayerRecord r, String today, HonorConfig cfg) {
        int used = isToday(r, today) ? r.donatedHonorToday : 0;
        return Math.max(0, cfg.donateDailyCap - used);
    }

    /** Honor/day cap for a type, or -1 when the type has no per-type cap (missing from the map or negative). */
    public static int typeCap(HonorConfig cfg, String type) {
        if (cfg.donateItemDailyCaps == null) return -1;
        Integer c = cfg.donateItemDailyCaps.get(type);
        return c == null || c < 0 ? -1 : c;
    }

    public static int earnedToday(HonorData.PlayerRecord r, String today, String type) {
        if (!isToday(r, today) || r.donatedTypeToday == null) return 0;
        Integer e = r.donatedTypeToday.get(type);
        return e == null ? 0 : Math.max(0, e);
    }

    /** Honor this type can still earn today from its own cap alone (Integer.MAX_VALUE when uncapped). */
    public static int typeRemaining(HonorData.PlayerRecord r, String today, HonorConfig cfg, String type) {
        int cap = typeCap(cfg, type);
        return cap < 0 ? Integer.MAX_VALUE : Math.max(0, cap - earnedToday(r, today, type));
    }

    /** Honor this type can still earn today, limited by its own cap and the overall cap. */
    public static int earnableToday(HonorData.PlayerRecord r, String today, HonorConfig cfg, String type) {
        return Math.min(typeRemaining(r, today, cfg, type), remainingToday(r, today, cfg));
    }

    /** Leftover points stored for a type. The pre-1.5.0 single carry counts as food until the next donation. */
    public static int carryOf(HonorData.PlayerRecord r, String type) {
        int c = 0;
        if (r.donateCarryTypes != null) {
            Integer v = r.donateCarryTypes.get(type);
            if (v != null) c = Math.max(0, v);
        }
        if (FOOD.equals(type)) c += Math.max(0, r.donateCarry);
        return c;
    }

    /** "ender_pearl" to "Ender pearl". */
    public static String label(String type) {
        String s = type.replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Types in a stable order: configured caps first (config order), then the rest alphabetically. */
    public static List<String> order(java.util.Collection<String> types, HonorConfig cfg) {
        List<String> out = new ArrayList<>();
        if (cfg.donateItemDailyCaps != null) {
            for (String k : cfg.donateItemDailyCaps.keySet()) if (types.contains(k)) out.add(k);
        }
        for (String k : new TreeSet<>(types)) if (!out.contains(k)) out.add(k);
        return out;
    }

    /**
     * Honor for the donated points per type. For each type: raw = (points + carry) / pointsPerHonor, awarded =
     * min(raw, type cap left, overall cap left); types are handled in a fixed order and the overall cap is shared
     * between them. A type that ends at its cap (or when the overall cap is used up) drops its leftover points;
     * a type still under its cap keeps points % pointsPerHonor. Judges earn nothing and keep their carry.
     * Pure: nothing is changed; use {@link #apply} to store the result. This is also the chest preview.
     */
    public static Quote quote(Map<String, Long> pointsByType, HonorData.PlayerRecord r, String today, HonorConfig cfg, boolean judge) {
        int overall = remainingToday(r, today, cfg);
        Map<String, Integer> byType = new LinkedHashMap<>();
        Map<String, Integer> carry = new LinkedHashMap<>();
        if (judge) {
            for (String t : order(pointsByType.keySet(), cfg)) {
                byType.put(t, 0);
                carry.put(t, carryOf(r, t));
            }
            return new Quote(0, byType, carry, overall, overall == 0);
        }
        int per = Math.max(1, cfg.honorPerHungerPoints);
        int left = overall;
        int sum = 0;
        boolean dropped = false;
        for (String t : order(pointsByType.keySet(), cfg)) {
            long pts = Math.max(0, pointsByType.get(t));
            long total = pts + carryOf(r, t);
            long raw = total / per;
            int allowed = Math.min(typeRemaining(r, today, cfg, t), left);
            int award = (int) Math.min(raw, allowed);
            left -= award;
            sum += award;
            boolean capped = award >= allowed; // type cap or overall cap used up (also when nothing was allowed)
            if (raw > award) dropped = true;
            byType.put(t, award);
            carry.put(t, capped ? 0 : (int) (total % per));
        }
        return new Quote(sum, byType, carry, overall, dropped || left == 0);
    }

    /** Stores a quote: earned totals (overall and per type) and the new leftover points. */
    public static void apply(Quote q, HonorData.PlayerRecord r, String today) {
        rollDay(r, today);
        if (r.donatedTypeToday == null) r.donatedTypeToday = new LinkedHashMap<>();
        if (r.donateCarryTypes == null) r.donateCarryTypes = new LinkedHashMap<>();
        r.donatedHonorToday += q.honor();
        for (var e : q.byType().entrySet()) {
            if (e.getValue() > 0) r.donatedTypeToday.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (var e : q.newCarry().entrySet()) {
            if (e.getValue() > 0) r.donateCarryTypes.put(e.getKey(), e.getValue());
            else r.donateCarryTypes.remove(e.getKey());
        }
        if (q.newCarry().containsKey(FOOD)) r.donateCarry = 0; // old single carry has been folded into food
    }
}
