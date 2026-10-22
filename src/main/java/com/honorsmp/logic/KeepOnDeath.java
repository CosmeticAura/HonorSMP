package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Which inventory slots a player keeps on death. Kept slots are removed from what drops, so nothing is both. */
public final class KeepOnDeath {
    private KeepOnDeath() {}

    public static final int HOTBAR_SIZE = 9;
    public static final int OFF_HAND_SLOT = 40;

    /** kept: slot to item. remaining: copy of the inventory with the kept slots emptied (this is what may drop). */
    public record Result<T>(Map<Integer, T> kept, List<T> remaining) {}

    public static boolean isKeptSlot(int slot) {
        return (slot >= 0 && slot < HOTBAR_SIZE) || slot == OFF_HAND_SLOT;
    }

    public static <T> Result<T> select(List<T> inventory, Predicate<T> isEmpty, T emptyValue, boolean keepHotbar) {
        Map<Integer, T> kept = new LinkedHashMap<>();
        List<T> remaining = new ArrayList<>(inventory);
        if (keepHotbar) {
            for (int i = 0; i < inventory.size(); i++) {
                if (!isKeptSlot(i)) continue;
                T item = inventory.get(i);
                if (item == null || isEmpty.test(item)) continue;
                kept.put(i, item);
                remaining.set(i, emptyValue);
            }
        }
        return new Result<>(kept, remaining);
    }
}
