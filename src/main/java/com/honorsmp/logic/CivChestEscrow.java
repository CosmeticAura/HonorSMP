package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules of the shared civ chest, generic over the item type.
 *
 * <p>Single-owner rule: a civ's stacks are in exactly one place at any time. While nobody has the
 * chest open they sit in {@code stored}; {@link #open} MOVES them out to the one viewer's live
 * inventory (and records the lock), and {@link #close} moves what is left in that inventory back,
 * once. Because the stacks are moved (never copied) and the lock admits one viewer, there is never a
 * second holder that could write the same stacks back, so closing twice, disconnecting after closing,
 * or a second opener cannot duplicate or lose anything.
 */
public final class CivChestEscrow<T> {
    public enum Status { OK, NOT_MEMBER, BUSY }

    /** Result of {@link #open}. {@code items} is only non-empty for OK; the caller's UI owns them from then on. */
    public record Opened<T>(Status status, List<T> items) {}

    public static final String BUSY_MESSAGE = "Someone in your civ is using the chest";

    private final BountyEscrow.Ops<T> ops;
    private final int capacity;
    private final Map<String, List<T>> stored = new HashMap<>();
    /** civ id to the player uuid that has it open. */
    private final Map<String, String> viewers = new HashMap<>();

    public CivChestEscrow(BountyEscrow.Ops<T> ops, int capacity) {
        this.ops = ops;
        this.capacity = capacity;
    }

    /** Leader, officers, members and guards may use the chest. */
    public static boolean mayUse(String uuid, String leader, Collection<String> officers, Collection<String> members,
                                 Collection<String> guards) {
        if (uuid == null) return false;
        return uuid.equals(leader) || officers.contains(uuid) || members.contains(uuid) || guards.contains(uuid);
    }

    private boolean live(T t) {
        return t != null && ops.count(t) > 0;
    }

    private long sum(List<T> l) {
        long n = 0;
        for (T t : l) if (live(t)) n += ops.count(t);
        return n;
    }

    // ------------------------------------------------------------ loading

    /** Startup: adds a saved stack (kept in saved order, not merged, so slots keep their layout). */
    public void load(String civId, T item) {
        if (!live(item)) return;
        stored.computeIfAbsent(civId, k -> new ArrayList<>()).add(item);
    }

    // ------------------------------------------------------------ open / close

    /** Opens the chest for one player. Moves up to {@code capacity} stacks out to the caller. */
    public Opened<T> open(String civId, String user, boolean mayUse) {
        return open(civId, user, mayUse, capacity);
    }

    /** Like {@link #open(String, String, boolean)} with the number of open slots (1.6.8: bought rows), at most the capacity. */
    public Opened<T> open(String civId, String user, boolean mayUse, int slots) {
        int capacity = Math.max(0, Math.min(this.capacity, slots));
        if (!mayUse) return new Opened<>(Status.NOT_MEMBER, List.of());
        if (viewers.containsKey(civId)) return new Opened<>(Status.BUSY, List.of());
        viewers.put(civId, user);
        List<T> all = stored.getOrDefault(civId, new ArrayList<>());
        int n = Math.min(capacity, all.size());
        List<T> out = new ArrayList<>(all.subList(0, n));
        List<T> rest = new ArrayList<>(all.subList(n, all.size()));
        if (rest.isEmpty()) stored.remove(civId);
        else stored.put(civId, rest); // overflow beyond the screen size (only from odd data) stays stored
        return new Opened<>(Status.OK, out);
    }

    /**
     * Writes the viewer's remaining stacks back. Returns false and does nothing unless {@code user} is the
     * current viewer, which makes a second close (or a disconnect after a close) harmless.
     */
    public boolean close(String civId, String user, List<T> remaining) {
        if (!user.equals(viewers.get(civId))) return false;
        viewers.remove(civId);
        List<T> list = stored.computeIfAbsent(civId, k -> new ArrayList<>());
        List<T> back = new ArrayList<>();
        for (T t : remaining) if (live(t)) back.add(t);
        list.addAll(0, back); // screen contents first, overflow behind them
        if (list.isEmpty()) stored.remove(civId);
        return true;
    }

    /**
     * 1.6.8: the chest only has {@code slots} open slots. Stacks beyond that (an old chest with locked rows, or more
     * than the open rows hold) are removed from the stored list and returned, so the caller can hand them to the
     * leader's donation box. Does nothing while the chest is open. Every stack ends up in exactly one list.
     */
    public List<T> trim(String civId, int slots) {
        if (viewers.containsKey(civId)) return new ArrayList<>();
        List<T> all = stored.get(civId);
        if (all == null) return new ArrayList<>();
        var fit = CivChestRows.fit(all, slots);
        List<T> keep = new ArrayList<>();
        List<T> over = new ArrayList<>();
        for (T t : fit.keep()) (live(t) ? keep : over).add(t);
        for (T t : fit.overflow()) if (live(t)) over.add(t);
        if (keep.isEmpty()) stored.remove(civId);
        else stored.put(civId, keep);
        return over;
    }

    /** The uuid with the chest open, or null. */
    public String viewerOf(String civId) {
        return viewers.get(civId);
    }

    public boolean isOpen(String civId) {
        return viewers.containsKey(civId);
    }

    // ------------------------------------------------------------ disband

    /**
     * Takes every stack out of a civ's chest (to be handed to the leader) and forgets the civ's chest.
     * Returns null, changing nothing, while the chest is open: close the viewer first. A second call
     * returns an empty list, so the contents can only be handed out once.
     */
    public List<T> disband(String civId) {
        if (viewers.containsKey(civId)) return null;
        List<T> all = stored.remove(civId);
        return all == null ? new ArrayList<>() : all;
    }

    // ------------------------------------------------------------ queries

    /** Copy of what is stored for a civ (not counting what a viewer holds). */
    public List<T> stored(String civId) {
        return new ArrayList<>(stored.getOrDefault(civId, List.of()));
    }

    public long storedItems(String civId) {
        return sum(stored.getOrDefault(civId, List.of()));
    }

    /** Drops everything (full reset). */
    public void clear() {
        stored.clear();
        viewers.clear();
    }
}
