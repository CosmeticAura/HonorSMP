package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.6.8: the civ chest has 6 rows. Row 1 is open once the chest is unlocked (3 members); rows 2 to 6 are bought
 * once each, in order, as a civ upgrade. Pure rules, no Minecraft types: row counting, prices, buying, the
 * click gate for locked slots and the migration of stacks that sit in locked rows.
 */
public final class CivChestRows {
    private CivChestRows() {}

    public static final int TOTAL_ROWS = 6;
    public static final int SLOTS_PER_ROW = 9;
    public static final int TOTAL_SLOTS = TOTAL_ROWS * SLOTS_PER_ROW;
    /** Rows that can be bought (2 to 6). */
    public static final int BUYABLE = TOTAL_ROWS - 1;

    /** Slot of the "Chest rows" icon in the 3-row /civ upgrades menu (bottom middle). */
    public static final int MENU_SLOT = 22;

    public static final String LOCKED_NAME = "Locked row, buy it in /civ upgrades";

    // ------------------------------------------------------------ rows and slots

    /** Bought rows, clamped to 0..5 (a missing or odd value in data.json counts as what it can be). */
    public static int clampBought(int bought) {
        return Math.max(0, Math.min(BUYABLE, bought));
    }

    /** Rows currently open: row 1 plus every bought row (1..6). */
    public static int unlockedRows(int bought) {
        return 1 + clampBought(bought);
    }

    /** Slots currently open (9..54); slots at or above this index are locked. */
    public static int unlockedSlots(int bought) {
        return unlockedRows(bought) * SLOTS_PER_ROW;
    }

    public static boolean isSlotUnlocked(int slot, int bought) {
        return slot >= 0 && slot < unlockedSlots(bought);
    }

    /** The row (2..6) that can be bought next, 0 when all rows are bought. */
    public static int nextRow(int bought) {
        int b = clampBought(bought);
        return b >= BUYABLE ? 0 : b + 2;
    }

    /** "Civ chest: 3 of 6 rows" */
    public static String infoLine(int bought) {
        return "Civ chest: " + unlockedRows(bought) + " of " + TOTAL_ROWS + " rows";
    }

    // ------------------------------------------------------------ prices

    /** Default prices, one list of "item:count" entries per row 2 to 6. Never emeralds. */
    public static List<List<String>> defaultCosts() {
        List<List<String>> l = new ArrayList<>();
        l.add(new ArrayList<>(List.of("minecraft:iron_ingot:32")));
        l.add(new ArrayList<>(List.of("minecraft:gold_ingot:32")));
        l.add(new ArrayList<>(List.of("minecraft:redstone:64", "minecraft:lapis_lazuli:32")));
        l.add(new ArrayList<>(List.of("minecraft:diamond:16")));
        l.add(new ArrayList<>(List.of("minecraft:netherite_ingot:2")));
        return l;
    }

    private static final java.util.Set<String> EMERALDS = java.util.Set.of("minecraft:emerald", "minecraft:emerald_block",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore");

    /** One "item:count" entry as item id to count; null when it cannot be read (no colon, no number, count below 1). */
    public static Map.Entry<String, Integer> parseEntry(String entry) {
        if (entry == null) return null;
        String s = entry.trim().toLowerCase(Locale.ROOT);
        int i = s.lastIndexOf(':');
        if (i <= 0 || i == s.length() - 1) return null;
        int n;
        try {
            n = Integer.parseInt(s.substring(i + 1).trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (n < 1) return null;
        String id = s.substring(0, i).trim();
        if (id.isEmpty()) return null;
        if (!id.contains(":")) id = "minecraft:" + id;
        if (EMERALDS.contains(id)) return null; // never emeralds: the row falls back to its default price
        return Map.entry(id, n);
    }

    private static Map<String, Integer> parseRow(List<String> row) {
        if (row == null || row.isEmpty()) return null;
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String e : row) {
            var p = parseEntry(e);
            if (p == null) return null; // one broken entry: the whole row falls back to its default
            m.merge(p.getKey(), p.getValue(), Integer::sum);
        }
        return m;
    }

    /**
     * Price of row {@code row} (2..6) from the config list (index 0 = row 2), item id to amount. A missing list,
     * a missing row or a row with an unreadable entry falls back to the default price of that row. Empty for an
     * out-of-range row.
     */
    public static Map<String, Integer> costOf(List<List<String>> configured, int row) {
        if (row < 2 || row > TOTAL_ROWS) return new LinkedHashMap<>();
        int idx = row - 2;
        if (configured != null && idx < configured.size()) {
            Map<String, Integer> m = parseRow(configured.get(idx));
            if (m != null) return m;
        }
        return parseRow(defaultCosts().get(idx));
    }

    // ------------------------------------------------------------ buying

    public enum Status { OK, ALL_BOUGHT, OUT_OF_ORDER, NOT_ENOUGH_ITEMS }

    /** {@code missing} is the item id that was short (NOT_ENOUGH_ITEMS only); {@code taken} is empty unless OK. */
    public record BuyResult(Status status, int bought, Map<String, Integer> taken, String missing) {
        public boolean ok() { return status == Status.OK; }
    }

    /** First item id of {@code need} the wallet does not cover, or null. */
    public static String missing(Map<String, Integer> need, Wallet w) {
        for (var e : need.entrySet()) if (w.count(e.getKey()) < e.getValue()) return e.getKey();
        return null;
    }

    /**
     * Buys row {@code row}. Only the next row can be bought (rows go in order, once each); anything else is refused
     * and nothing is taken. The price is checked in full before anything is taken, so it is all or nothing.
     *
     * @param bought rows bought so far (0..5)
     * @return the new count of bought rows on OK, else the unchanged one
     */
    public static BuyResult buy(List<List<String>> configured, int bought, int row, Wallet w) {
        int b = clampBought(bought);
        int next = nextRow(b);
        if (next == 0) return new BuyResult(Status.ALL_BOUGHT, b, Map.of(), null);
        if (row != next) return new BuyResult(Status.OUT_OF_ORDER, b, Map.of(), null);
        Map<String, Integer> cost = costOf(configured, row);
        String miss = missing(cost, w);
        if (miss != null) return new BuyResult(Status.NOT_ENOUGH_ITEMS, b, Map.of(), miss);
        for (var e : cost.entrySet()) w.take(e.getKey(), e.getValue());
        return new BuyResult(Status.OK, b + 1, cost, null);
    }

    // ------------------------------------------------------------ click gate

    /**
     * Whether vanilla may process this click. A click on a locked chest slot is refused for every click type
     * (pick up, shift click, number key, drop, creative clone, drag, double click). Clicks outside the container
     * (-999, -1) and on the player's own slots (index of the chest size and above) go through; shift clicks from the
     * player's slots are steered into unlocked slots only by the handler's quickMove, and the handler also refuses
     * locked slots for dragging and double click collecting.
     *
     * @param slotIndex     clicked slot index in the handler
     * @param unlockedSlots {@link #unlockedSlots}
     * @param containerSize number of chest slots (54)
     */
    public static boolean vanillaMayProcess(MenuClicks.Kind kind, int slotIndex, int unlockedSlots, int containerSize) {
        if (kind == null) return false;
        return !(slotIndex >= unlockedSlots && slotIndex < containerSize);
    }

    /** Whether a stack may be put into this chest slot (drag, double click collect, shift click target). */
    public static boolean mayInsert(int slotIndex, int unlockedSlots, int containerSize) {
        return slotIndex >= 0 && slotIndex < unlockedSlots && unlockedSlots <= containerSize;
    }

    // ------------------------------------------------------------ migration

    /** Result of {@link #fit}: what stays in the unlocked slots and what has no room there. */
    public record Fit<T>(List<T> keep, List<T> overflow) {}

    /**
     * Existing civ chests (before 1.6.8) may hold more stacks than the open rows have slots. The stacks are kept in
     * saved order and fill the open slots from the first one (empty gaps close up, as they always did); the stacks
     * that do not fit are returned as overflow so they can go to the leader's /donation box. Every stack ends up in
     * exactly one of the two lists.
     */
    public static <T> Fit<T> fit(List<T> stored, int unlockedSlots) {
        List<T> keep = new ArrayList<>();
        List<T> over = new ArrayList<>();
        if (stored != null) {
            for (T t : stored) {
                if (t == null) continue;
                if (keep.size() < unlockedSlots) keep.add(t);
                else over.add(t);
            }
        }
        return new Fit<>(keep, over);
    }

    /** Message for a leader whose chest had stacks in rows that are now locked. */
    public static String overflowNotice(long items) {
        return items + " items did not fit in the open rows of your civ chest and were moved to your donation box. Use /donation to claim them. Buy more rows in /civ upgrades.";
    }
}
