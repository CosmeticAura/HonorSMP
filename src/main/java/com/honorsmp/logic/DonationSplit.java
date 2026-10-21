package com.honorsmp.logic;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pure sharing math: equal split per item type with a leftover pot that carries to the next donation. */
public final class DonationSplit {
    private DonationSplit() {}

    /** What each recipient gets and what stays in the pot. */
    public record Result(long each, long newPot) {}

    /** Pot plus the new amount is split between recipients; the remainder becomes the new pot. */
    public static Result split(long pot, long amount, int recipients) {
        if (recipients <= 0) throw new IllegalArgumentException("recipients must be above 0");
        long total = Math.max(0, pot) + Math.max(0, amount);
        return new Result(total / recipients, total % recipients);
    }

    /**
     * Shares a whole donation (item id to amount). Updates {@code pots} in place and returns the amount
     * each recipient gets per item id. With no recipients nothing is touched and null is returned
     * (the caller must not consume the items).
     */
    public static Map<String, Long> apply(Map<String, Long> pots, Map<String, Long> donated, int recipients) {
        if (recipients <= 0) return null;
        Map<String, Long> each = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : donated.entrySet()) {
            long amount = e.getValue() == null ? 0 : e.getValue();
            if (amount <= 0) continue;
            Long pot = pots.get(e.getKey());
            Result r = split(pot == null ? 0 : pot, amount, recipients);
            each.put(e.getKey(), r.each());
            if (r.newPot() > 0) pots.put(e.getKey(), r.newPot());
            else pots.remove(e.getKey());
        }
        return each;
    }
}
