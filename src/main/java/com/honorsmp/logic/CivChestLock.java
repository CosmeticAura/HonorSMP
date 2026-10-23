package com.honorsmp.logic;

import java.util.HashMap;
import java.util.Map;

/**
 * 1.6.6: the civ chest cannot be opened for a while after a player was hurt by another player.
 * Pure rules, no Minecraft types, so the timing and the "who counts" decision are unit tested.
 */
public final class CivChestLock {
    private final Map<String, Long> last = new HashMap<>();

    /**
     * Who hurt {@code victim}: the uuid of a real player behind this damage, or null when it does not
     * count (mob damage, fall, fire, the victim's own arrow or pet...). Pass null for what is absent.
     *
     * @param attackerPlayer the damage source's attacker when it is a real player
     * @param attackerOwner  the owner when the attacker is a player's pet, projectile, TNT or crystal
     * @param sourcePlayer   the damage source's direct source (arrow, potion, firework...) when it is a player
     * @param sourceOwner    the owner of that direct source
     */
    public static String culprit(String victim, String attackerPlayer, String attackerOwner, String sourcePlayer, String sourceOwner) {
        for (String c : new String[] {attackerPlayer, attackerOwner, sourcePlayer, sourceOwner}) {
            if (c != null && !c.equals(victim)) return c;
        }
        return null;
    }

    /** Records a hit on {@code victim} when it came from another player. Returns whether it counted. */
    public synchronized boolean hit(String victim, long nowMs, String attackerPlayer, String attackerOwner, String sourcePlayer, String sourceOwner) {
        if (culprit(victim, attackerPlayer, attackerOwner, sourcePlayer, sourceOwner) == null) return false;
        last.put(victim, nowMs);
        return true;
    }

    public synchronized void clear(String victim) {
        last.remove(victim);
    }

    public synchronized void clearAll() {
        last.clear();
    }

    /** Whole seconds left (rounded up) before the chest may be opened, 0 when free or disabled (windowSeconds &lt;= 0). */
    public synchronized int remainingSeconds(String victim, long nowMs, int windowSeconds) {
        if (windowSeconds <= 0) return 0;
        Long t = last.get(victim);
        if (t == null) return 0;
        long left = windowSeconds * 1000L - (nowMs - t);
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }

    /** "You were in a fight. The civ chest opens again in M:SS." */
    public static String message(int seconds) {
        return "You were in a fight. The civ chest opens again in " + (seconds / 60) + ":" + String.format("%02d", seconds % 60) + ".";
    }

    public static final String SIEGE_MESSAGE = "Your civ is under siege. The civ chest opens again when the raid is over.";
}
