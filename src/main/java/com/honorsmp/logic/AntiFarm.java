package com.honorsmp.logic;

import java.util.HashMap;
import java.util.Map;

/** Anti farming: the same killer/victim pair only counts once per cooldown. */
public final class AntiFarm {
    private final Map<String, Long> lastCounted = new HashMap<>();

    private static String key(String killer, String victim) { return killer + ">" + victim; }

    public boolean wouldCount(String killer, String victim, long nowMs, long cooldownMs) {
        Long last = lastCounted.get(key(killer, victim));
        return last == null || nowMs - last >= cooldownMs;
    }

    /** Returns true and records the kill if it counts. */
    public boolean tryCount(String killer, String victim, long nowMs, long cooldownMs) {
        if (!wouldCount(killer, victim, nowMs, cooldownMs)) return false;
        lastCounted.put(key(killer, victim), nowMs);
        return true;
    }

    public Map<String, Long> raw() { return lastCounted; }

    public void prune(long nowMs, long cooldownMs) {
        lastCounted.values().removeIf(t -> nowMs - t >= cooldownMs);
    }
}
