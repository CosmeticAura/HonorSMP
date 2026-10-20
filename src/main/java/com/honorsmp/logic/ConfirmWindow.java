package com.honorsmp.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** "Run it twice within N seconds" confirmation, tracked per caller. */
public final class ConfirmWindow {
    private final Map<UUID, Long> asked = new HashMap<>();

    /** Returns true (and forgets the ask) if the caller asked within the window; otherwise records this ask and returns false. */
    public synchronized boolean confirm(UUID who, long nowMs, long windowMs) {
        Long last = asked.get(who);
        if (last == null || nowMs - last > windowMs) {
            asked.put(who, nowMs);
            return false;
        }
        asked.remove(who);
        return true;
    }

    public synchronized void clear() {
        asked.clear();
    }
}
