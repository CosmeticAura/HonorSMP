package com.honorsmp.logic;

/** What a buyer has, so buying stays testable without Minecraft. The UI wraps a player inventory (plain items only). */
public interface Wallet {
    /** How many of this item id (like "minecraft:redstone") the buyer has. */
    int count(String itemId);

    /** Removes the items. Only called after the counts were checked. */
    void take(String itemId, int amount);
}
