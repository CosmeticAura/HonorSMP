package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Decides who started a fight. The fight is the unbroken chain of hits between the two players
 * ending at the moment of death, where no gap between hits is longer than the self defense window.
 */
public final class FirstHitRule {
    private FirstHitRule() {}

    /** Hits between the two players, oldest first, that make up the fight leading to the kill. */
    public static List<DamageEvent> fight(List<DamageEvent> history, String a, String b, long nowMs, long windowMs) {
        List<DamageEvent> pair = new ArrayList<>();
        for (DamageEvent e : history) {
            boolean ab = e.attacker().equals(a) && e.victim().equals(b);
            boolean ba = e.attacker().equals(b) && e.victim().equals(a);
            if ((ab || ba) && e.timeMs() <= nowMs) pair.add(e);
        }
        pair.sort(Comparator.comparingLong(DamageEvent::timeMs).reversed());
        List<DamageEvent> chain = new ArrayList<>();
        long previous = nowMs;
        for (DamageEvent e : pair) {
            if (previous - e.timeMs() > windowMs) break;
            chain.add(e);
            previous = e.timeMs();
        }
        chain.sort(Comparator.comparingLong(DamageEvent::timeMs));
        return chain;
    }

    /** The first hit of the fight, or null if the victim and killer never exchanged damage in the window. */
    public static DamageEvent firstHit(List<DamageEvent> history, String killer, String victim, long nowMs, long windowMs) {
        List<DamageEvent> chain = fight(history, killer, victim, nowMs, windowMs);
        return chain.isEmpty() ? null : chain.get(0);
    }

    /** True when the victim landed the first hit (self defense by the killer). */
    public static boolean victimHitFirst(List<DamageEvent> history, String killer, String victim, long nowMs, long windowMs) {
        DamageEvent first = firstHit(history, killer, victim, nowMs, windowMs);
        return first != null && first.attacker().equals(victim);
    }
}
