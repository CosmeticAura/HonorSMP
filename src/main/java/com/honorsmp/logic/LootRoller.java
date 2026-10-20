package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure weighted loot roll. No Minecraft types; the caller turns drops into item stacks. */
public final class LootRoller {
    private LootRoller() {
    }

    /** One result: the entry that was hit, a concrete item id (null for books), and a count or level/power. */
    public record Drop(LootEntry entry, String itemId, int count, int level) {
    }

    public static boolean valid(LootEntry e) {
        if (e == null || e.weight <= 0 || e.type == null) return false;
        return switch (e.type) {
            case "item", "enchanted_gear" -> hasItems(e);
            case "book" -> true;
            default -> false;
        };
    }

    private static boolean hasItems(LootEntry e) {
        if (e.items == null) return false;
        for (String s : e.items) if (s != null && !s.isBlank()) return true;
        return false;
    }

    public static List<Drop> roll(LootTable table, Random rng) {
        List<Drop> out = new ArrayList<>();
        if (table == null || table.entries == null || table.rolls <= 0) return out;
        List<LootEntry> pool = new ArrayList<>();
        long total = 0;
        for (LootEntry e : table.entries) {
            if (valid(e)) {
                pool.add(e);
                total += e.weight;
            }
        }
        if (pool.isEmpty()) return out;
        for (int i = 0; i < Math.min(table.rolls, 64); i++) {
            long r = (long) (rng.nextDouble() * total);
            LootEntry hit = pool.get(pool.size() - 1);
            for (LootEntry e : pool) {
                if (r < e.weight) {
                    hit = e;
                    break;
                }
                r -= e.weight;
            }
            out.add(make(hit, rng));
        }
        return out;
    }

    private static Drop make(LootEntry e, Random rng) {
        String id = null;
        if (!e.type.equals("book")) {
            List<String> ids = new ArrayList<>();
            for (String s : e.items) if (s != null && !s.isBlank()) ids.add(s.trim());
            id = ids.get(rng.nextInt(ids.size()));
        }
        if (e.type.equals("item")) return new Drop(e, id, range(e.min, e.max, 1, 64 * 4, rng), 0);
        return new Drop(e, id, 1, e.useMaxLevel && e.type.equals("book") ? 0 : range(e.minLevel, e.maxLevel, 1, 100, rng));
    }

    /** Inclusive random int in [lo, hi], clamped to [floor, ceil]; swapped bounds are fixed. */
    public static int range(int lo, int hi, int floor, int ceil, Random rng) {
        if (lo > hi) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        lo = Math.max(floor, Math.min(ceil, lo));
        hi = Math.max(floor, Math.min(ceil, hi));
        return lo + rng.nextInt(hi - lo + 1);
    }

    /** Clamps a requested book level into 1..enchantMax. */
    public static int bookLevel(Drop d, int enchantMax) {
        if (d.entry().useMaxLevel) return Math.max(1, enchantMax);
        return Math.max(1, Math.min(enchantMax, d.level()));
    }
}
