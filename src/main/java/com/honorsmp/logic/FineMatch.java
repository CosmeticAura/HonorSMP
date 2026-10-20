package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Decides whether offered stacks pay a fine. Items match when item id, enchantments (also stored
 * ones on books), custom name and custom data are equal, and damage is equal (an undamaged item has
 * damage 0, whether or not it carries a damage value). Counts add up per kind of item. Payment is
 * all or nothing: when anything is short nothing is used.
 */
public final class FineMatch {
    private FineMatch() {}

    /** One stack, reduced to what matters for matching. */
    public record Desc(String id, int count, int damage, Map<String, Integer> enchants, String name, String data) {
        public Desc {
            enchants = enchants == null ? Map.of() : new TreeMap<>(enchants);
            name = name == null ? "" : name;
            data = data == null ? "" : data;
        }

        public String key() {
            return id + "|" + Math.max(0, damage) + "|" + new TreeMap<>(enchants) + "|" + name + "|" + data;
        }

        public Desc withCount(int n) {
            return new Desc(id, n, damage, enchants, name, data);
        }
    }

    /** used.get(i) = how many items of offered stack i pay the fine. missing = what is still owed when !complete. */
    public record Result(boolean complete, List<Integer> used, List<Desc> missing) {}

    public static Result match(List<Desc> owed, List<Desc> offered) {
        Map<String, Integer> need = new LinkedHashMap<>();
        Map<String, Desc> sample = new LinkedHashMap<>();
        for (Desc d : owed) {
            if (d.count() <= 0) continue;
            need.merge(d.key(), d.count(), Integer::sum);
            sample.putIfAbsent(d.key(), d);
        }
        Map<String, Integer> left = new LinkedHashMap<>(need);
        List<Integer> used = new ArrayList<>();
        for (Desc o : offered) {
            Integer l = left.get(o.key());
            int take = l == null || o.count() <= 0 ? 0 : Math.min(l, o.count());
            if (take > 0) left.put(o.key(), l - take);
            used.add(take);
        }
        List<Desc> missing = new ArrayList<>();
        for (var e : left.entrySet()) if (e.getValue() > 0) missing.add(sample.get(e.getKey()).withCount(e.getValue()));
        if (!missing.isEmpty()) {
            // all or nothing: a partial payment uses nothing
            List<Integer> none = new ArrayList<>();
            for (int i = 0; i < offered.size(); i++) none.add(0);
            return new Result(false, none, missing);
        }
        return new Result(true, used, List.of());
    }
}
