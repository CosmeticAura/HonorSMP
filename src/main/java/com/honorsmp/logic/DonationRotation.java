package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Pure sharing math for donations (no Minecraft imports).
 *
 * <p>Rule: recipients are sorted by UUID string (stable). Everyone gets floor(amount / n). The remainder is
 * handed out one item each to the next recipients in that sorted list, starting at the saved rotation index
 * of that item type (index modulo the current list size, so a changed recipient list is always safe).
 * The new index is (start + remainder) modulo n, saved per item type, so the next donation of the same
 * item continues where this one stopped and over time everyone gets the same amount.
 */
public final class DonationRotation {
    private DonationRotation() {}

    public static final int MAX_NOTICES = 20;

    /** Sorted unique recipients; the order the rotation index refers to. */
    public static List<String> sorted(Collection<String> recipients) {
        return new ArrayList<>(new TreeSet<>(recipients));
    }

    /** Who gets how much of one item type, and the rotation index to save. */
    public record Shares(Map<String, Long> perRecipient, int nextIndex) {}

    /** Splits {@code amount} between the recipients. Recipients must not be empty. */
    public static Shares distribute(Collection<String> recipients, long amount, int savedIndex) {
        List<String> list = sorted(recipients);
        int n = list.size();
        if (n == 0) throw new IllegalArgumentException("recipients must not be empty");
        long total = Math.max(0, amount);
        long base = total / n;
        int rem = (int) (total % n);
        int start = Math.floorMod(savedIndex, n);
        Map<String, Long> out = new LinkedHashMap<>();
        for (String u : list) out.put(u, base);
        for (int k = 0; k < rem; k++) out.merge(list.get((start + k) % n), 1L, Long::sum);
        return new Shares(out, (start + rem) % n);
    }

    /**
     * Shares a whole donation (item key to amount). Updates {@code rotation} in place and returns
     * recipient to (item key to amount, only above 0). With no recipients nothing is touched and null is
     * returned (the caller must not consume the items).
     */
    public static Map<String, Map<String, Long>> share(Map<String, Integer> rotation, Map<String, Long> donated,
                                                       Collection<String> recipients) {
        if (recipients == null || sorted(recipients).isEmpty()) return null;
        Map<String, Map<String, Long>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : donated.entrySet()) {
            long amount = e.getValue() == null ? 0 : e.getValue();
            if (amount <= 0) continue;
            Integer saved = rotation.get(e.getKey());
            Shares s = distribute(recipients, amount, saved == null ? 0 : saved);
            rotation.put(e.getKey(), s.nextIndex());
            for (Map.Entry<String, Long> r : s.perRecipient().entrySet()) {
                if (r.getValue() > 0) out.computeIfAbsent(r.getKey(), k -> new LinkedHashMap<>()).put(e.getKey(), r.getValue());
            }
        }
        return out;
    }

    /** "3 Golden Apple, 1 Apple" from display name to count (insertion order). */
    public static String describe(Map<String, Long> namesToCounts) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> e : namesToCounts.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) parts.add(e.getValue() + " " + e.getKey());
        }
        return String.join(", ", parts);
    }

    public static String notice(String items, String donor) {
        return "You got " + items + " from " + donor + ". Use /donation to claim it.";
    }

    /** Adds a notice: an identical one already waiting is moved to the end, and the list is cut to the newest MAX_NOTICES. */
    public static void addNotice(List<String> list, String msg) {
        list.remove(msg);
        list.add(msg);
        while (list.size() > MAX_NOTICES) list.remove(0);
    }
}
