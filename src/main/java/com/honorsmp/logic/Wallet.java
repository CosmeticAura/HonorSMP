package com.honorsmp.logic;

/** What a buyer/payer has, so buying and paying stay testable without Minecraft. The UI wraps a player inventory. */
public interface Wallet {
    int emeralds();

    int diamonds();

    /** Removes the items. Only called after the counts were checked. */
    void take(int emeralds, int diamonds);
}
