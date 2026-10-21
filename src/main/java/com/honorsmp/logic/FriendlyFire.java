package com.honorsmp.logic;

/** Members of the same civ (and their pets) cannot hurt each other. */
public final class FriendlyFire {
    private FriendlyFire() {}

    /** True when damage from attacker's civ to victim's civ must be blocked. Null/empty id = no civ. */
    public static boolean blocked(String attackerCivId, String victimCivId) {
        if (attackerCivId == null || victimCivId == null) return false;
        if (attackerCivId.isEmpty() || victimCivId.isEmpty()) return false;
        return attackerCivId.equals(victimCivId);
    }
}
