package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * Pure bounty bookkeeping, generic over the item type. Every operation only moves stacks between
 * lists (never copies), so {@link #total()} plus whatever the caller holds outside is conserved.
 * Stacks live in two places: bounties per target, and a claim box per player.
 */
public final class BountyEscrow<T> {
    /** How the escrow sees an item stack. {@code withCount} returns a new stack of the same kind. */
    public interface Ops<T> {
        boolean same(T a, T b);

        int count(T t);

        T withCount(T t, int n);

        int maxStack(T t);
    }

    private final Ops<T> ops;
    private final Map<String, List<T>> bounties = new LinkedHashMap<>();
    private final Map<String, List<T>> boxes = new LinkedHashMap<>();
    private final Set<String> openBoxes = new HashSet<>();
    private final List<String> journal = new ArrayList<>();

    public BountyEscrow(Ops<T> ops) {
        this.ops = ops;
    }

    // ------------------------------------------------------------ merging

    /** Adds item to list, topping up identical stacks first and splitting at max stack size. */
    public void merge(List<T> list, T item) {
        if (item == null || ops.count(item) <= 0) return;
        int left = ops.count(item);
        int max = Math.max(1, ops.maxStack(item));
        for (int i = 0; i < list.size() && left > 0; i++) {
            T s = list.get(i);
            if (ops.same(s, item) && ops.count(s) < max) {
                int add = Math.min(left, max - ops.count(s));
                list.set(i, ops.withCount(s, ops.count(s) + add));
                left -= add;
            }
        }
        while (left > 0) {
            int n = Math.min(left, max);
            list.add(ops.withCount(item, n));
            left -= n;
        }
    }

    private long sum(List<T> l) {
        long n = 0;
        for (T t : l) n += ops.count(t);
        return n;
    }

    // ------------------------------------------------------------ setting

    /** Starts a set session. The caller's UI holds the items; they only enter the escrow in commit. */
    public SetSession startSet(String target) {
        return new SetSession(target);
    }

    public final class SetSession {
        private final String target;
        private boolean committed;

        private SetSession(String target) {
            this.target = target;
        }

        /**
         * Commits the contents exactly once. Returns the stacks the caller must give back to the
         * setter (everything if the target is invalid, nothing if accepted or if already committed).
         */
        public List<T> commit(List<T> contents, boolean targetValid) {
            if (committed) return new ArrayList<>(); // second close: contents were already handled
            committed = true;
            List<T> items = new ArrayList<>();
            for (T t : contents) if (t != null && ops.count(t) > 0) items.add(t);
            if (items.isEmpty()) return new ArrayList<>();
            if (!targetValid) {
                journal.add("return " + sum(items) + " to setter (target " + target + " invalid)");
                return items;
            }
            List<T> list = bounties.computeIfAbsent(target, k -> new ArrayList<>());
            for (T t : items) merge(list, t);
            journal.add("set " + sum(items) + " on " + target);
            return new ArrayList<>();
        }

        public boolean isCommitted() {
            return committed;
        }
    }

    // ------------------------------------------------------------ kill payout

    /** Moves every bounty item on target into killer's box in one step. Returns the item count moved. */
    public long payout(String target, String killer) {
        List<T> moved = bounties.remove(target);
        if (moved == null || moved.isEmpty()) return 0;
        long n = sum(moved);
        List<T> box = boxes.computeIfAbsent(killer, k -> new ArrayList<>());
        for (T t : moved) merge(box, t);
        journal.add("payout " + n + " from " + target + " to " + killer);
        return n;
    }

    // ------------------------------------------------------------ claim box

    /**
     * Takes up to capacity stacks out of the box for the UI (which then owns them). Returns null if
     * this player's box is already open. The rest stays in the box.
     */
    public List<T> openBox(String owner, int capacity) {
        if (!openBoxes.add(owner)) return null;
        List<T> box = boxes.getOrDefault(owner, new ArrayList<>());
        int n = Math.min(capacity, box.size());
        List<T> out = new ArrayList<>(box.subList(0, n));
        List<T> rest = new ArrayList<>(box.subList(n, box.size()));
        if (rest.isEmpty()) boxes.remove(owner);
        else boxes.put(owner, rest);
        journal.add("open box " + owner + " took " + sum(out));
        return out;
    }

    /** Puts what is left in the UI back. Returns false (and does nothing) if the box is not open. */
    public boolean closeBox(String owner, List<T> remaining) {
        if (!openBoxes.remove(owner)) return false;
        List<T> box = boxes.computeIfAbsent(owner, k -> new ArrayList<>());
        for (T t : remaining) merge(box, t);
        if (box.isEmpty()) boxes.remove(owner);
        journal.add("close box " + owner + " returned " + sum(remaining));
        return true;
    }

    public boolean isBoxOpen(String owner) {
        return openBoxes.contains(owner);
    }

    /** Adds stacks straight to a box (e.g. recovery of items that were in a UI at crash time). */
    public void addToBox(String owner, T item) {
        merge(boxes.computeIfAbsent(owner, k -> new ArrayList<>()), item);
    }

    // ------------------------------------------------------------ migration / loading

    /** Old emerald-count bounties become emerald stacks. Returns total emeralds migrated. */
    public long migrateEmeralds(Map<String, Integer> old, IntFunction<T> emerald) {
        long total = 0;
        for (Map.Entry<String, Integer> e : old.entrySet()) {
            int n = e.getValue() == null ? 0 : e.getValue();
            if (n <= 0) continue;
            merge(bounties.computeIfAbsent(e.getKey(), k -> new ArrayList<>()), emerald.apply(n));
            total += n;
        }
        if (total > 0) journal.add("migrated " + total + " emeralds");
        return total;
    }

    /** Loads a saved bounty list for a target (used at startup). */
    public void loadBounty(String target, T item) {
        merge(bounties.computeIfAbsent(target, k -> new ArrayList<>()), item);
    }

    public void loadBox(String owner, T item) {
        addToBox(owner, item);
    }

    // ------------------------------------------------------------ queries

    /** Drops every bounty, claim box and open-box marker (full reset). */
    public void clear() {
        bounties.clear();
        boxes.clear();
        openBoxes.clear();
        journal.clear();
    }

    public Map<String, List<T>> bounties() {
        return Collections.unmodifiableMap(bounties);
    }

    public Map<String, List<T>> boxes() {
        return Collections.unmodifiableMap(boxes);
    }

    public long itemsOn(String target) {
        return sum(bounties.getOrDefault(target, List.of()));
    }

    public int stacksOn(String target) {
        return bounties.getOrDefault(target, List.of()).size();
    }

    public boolean hasBounty(String target) {
        return stacksOn(target) > 0;
    }

    public long boxItems(String owner) {
        return sum(boxes.getOrDefault(owner, List.of()));
    }

    /** Everything held by the escrow (bounties and boxes, excluding items currently inside a UI). */
    public long total() {
        long n = 0;
        for (List<T> l : bounties.values()) n += sum(l);
        for (List<T> l : boxes.values()) n += sum(l);
        return n;
    }

    public List<String> journal() {
        return Collections.unmodifiableList(journal);
    }
}
