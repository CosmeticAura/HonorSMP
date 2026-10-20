package com.honorsmp.logic;

/** The honor table for player kills. */
public final class KillRules {
    private KillRules() {}

    public static KillOutcome evaluate(KillContext k, HonorConfig c) {
        if (k.sameIp()) {
            return new KillOutcome(0, false, true, false, "Kill between players on the same IP (not counted, flagged)");
        }
        if (k.pairOnCooldown()) {
            return new KillOutcome(0, false, false, false, "Repeat kill of the same victim inside the anti farming window");
        }
        if (k.victimTier().isDishonored()) {
            int d = k.killerIsGuard() ? c.killDishonoredGuard : c.killDishonored;
            if (k.victimUnder5MinPlaytime()) {
                return new KillOutcome(0, false, false, true, "Victim had under " + c.minVictimPlaytimeMinutes + " minutes of playtime since last death (no honor)");
            }
            return new KillOutcome(d, false, false, true,
                    k.killerIsGuard() ? "Guard killed a dishonored player" : "Killed a dishonored player");
        }
        // honorable or neutral victim
        if (k.victimHitFirst()) {
            return new KillOutcome(0, false, false, true, "Self defense: victim hit first");
        }
        if (k.victimUnarmedAndUnarmored()) {
            return new KillOutcome(c.killUnarmed, false, false, true, "Killed an unarmed, unarmored player");
        }
        return new KillOutcome(c.killNoFirstHit, true, false, true, "Killed a player who did not hit first (provisional, judge review)");
    }
}
