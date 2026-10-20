package com.honorsmp.logic;

import java.util.List;

/**
 * One weighted entry in a bounty loot table (config/honorsmp.json).
 * type "item": gives min..max of a random id from items. type "book": enchanted book, level minLevel..maxLevel,
 * or the enchantment's own maximum if maxLevel is true. type "enchanted_gear": one random item from items,
 * enchanted as if at an enchanting table with power minLevel..maxLevel.
 */
public class LootEntry {
    public String type = "item";
    public List<String> items = List.of();
    public int weight = 1;
    public int min = 1;
    public int max = 1;
    public int minLevel = 1;
    public int maxLevel = 1;
    /** Books only: use the enchantment's maximum level instead of minLevel/maxLevel. */
    public boolean useMaxLevel = false;

    public LootEntry() {
    }

    public static LootEntry item(int weight, int min, int max, String... ids) {
        LootEntry e = new LootEntry();
        e.type = "item";
        e.items = List.of(ids);
        e.weight = weight;
        e.min = min;
        e.max = max;
        return e;
    }

    public static LootEntry book(int weight, int minLevel, int maxLevel, boolean useMax) {
        LootEntry e = new LootEntry();
        e.type = "book";
        e.weight = weight;
        e.minLevel = minLevel;
        e.maxLevel = maxLevel;
        e.useMaxLevel = useMax;
        return e;
    }

    public static LootEntry gear(int weight, int minPower, int maxPower, String... ids) {
        LootEntry e = new LootEntry();
        e.type = "enchanted_gear";
        e.items = List.of(ids);
        e.weight = weight;
        e.minLevel = minPower;
        e.maxLevel = maxPower;
        return e;
    }
}
