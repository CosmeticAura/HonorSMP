package com.honorsmp.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Remembers which players damaged each boss entity during the fight. Pure; keyed by the boss's entity UUID. */
public class BossFights {
    private static final int MAX_FIGHTS = 64;
    private final Map<UUID, Set<UUID>> fights = new HashMap<>();

    public void record(UUID boss, UUID player) {
        if (fights.size() >= MAX_FIGHTS && !fights.containsKey(boss)) fights.clear(); // leaked fights of despawned bosses
        fights.computeIfAbsent(boss, k -> new HashSet<>()).add(player);
    }

    /** Ends the fight and returns everyone who damaged the boss (empty if nobody did). */
    public Set<UUID> end(UUID boss) {
        Set<UUID> s = fights.remove(boss);
        return s == null ? Set.of() : s;
    }

    public void clear() {
        fights.clear();
    }
}
