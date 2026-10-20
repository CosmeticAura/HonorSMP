package com.honorsmp.logic;

import java.util.HashMap;
import java.util.Map;

/** Remembers when each player last took damage from another player. */
public final class CombatTimer {
    private final Map<String, Long> last = new HashMap<>();

    public synchronized void record(String player, long nowMs) {
        last.put(player, nowMs);
    }

    public synchronized void clearAll() {
        last.clear();
    }

    public synchronized void clear(String player) {
        last.remove(player);
    }

    /** Whole seconds left (rounded up) in the lock, 0 when free or disabled (windowSeconds &lt;= 0). */
    public synchronized int remainingSeconds(String player, long nowMs, int windowSeconds) {
        if (windowSeconds <= 0) return 0;
        Long t = last.get(player);
        if (t == null) return 0;
        long left = windowSeconds * 1000L - (nowMs - t);
        return left <= 0 ? 0 : (int) ((left + 999) / 1000);
    }
}
