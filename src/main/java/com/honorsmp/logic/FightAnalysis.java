package com.honorsmp.logic;

import java.util.List;

/**
 * Item 9: decides, from the damage timeline, who started a fight, so only truly unclear kills reach a judge.
 *
 * Rules (victim is honorable or neutral; dishonored victims and unarmed+unarmored victims are decided elsewhere):
 * <ol>
 *   <li>VICTIM: the fight (unbroken chain of hits ending at the death, gaps within the self defense window)
 *       starts with a hit by the victim. Self defense, 0 honor, no case.</li>
 *   <li>KILLER: the chain starts with a hit by the killer AND the victim never hit the killer earlier inside
 *       the whole recorded history. The killer clearly started it: unprovoked kill, decided automatically.</li>
 *   <li>UNCLEAR (goes to a judge): no hits between them recorded at all (death by fire, lava, an explosion or a
 *       missed event), or the victim hit the killer earlier than the chain (an older feud, so the first hit is
 *       not the cause), or the two are in the same civilization (could be a duel or sparring).</li>
 * </ol>
 */
public final class FightAnalysis {
    private FightAnalysis() {}

    public enum Who { VICTIM, KILLER, UNCLEAR }

    public static Who firstAggressor(List<DamageEvent> history, String killer, String victim, long nowMs,
                                     long windowMs, boolean sameCiv) {
        List<DamageEvent> chain = FirstHitRule.fight(history, killer, victim, nowMs, windowMs);
        if (chain.isEmpty()) return Who.UNCLEAR;
        DamageEvent first = chain.get(0);
        if (first.attacker().equals(victim)) return Who.VICTIM;
        if (sameCiv) return Who.UNCLEAR;
        for (DamageEvent e : history) {
            if (e.attacker().equals(victim) && e.victim().equals(killer) && e.timeMs() < first.timeMs()) return Who.UNCLEAR;
        }
        return Who.KILLER;
    }
}
