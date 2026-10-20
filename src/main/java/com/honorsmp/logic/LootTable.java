package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.List;

/** Rolls picks (with replacement) from the weighted entries. */
public class LootTable {
    public int rolls = 1;
    public List<LootEntry> entries = new ArrayList<>();

    public LootTable() {
    }

    public LootTable(int rolls, List<LootEntry> entries) {
        this.rolls = rolls;
        this.entries = entries;
    }
}
