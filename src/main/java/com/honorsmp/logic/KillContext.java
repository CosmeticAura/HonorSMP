package com.honorsmp.logic;

/**
 * Everything the kill rules need to know, free of Minecraft types.
 * {@code fightUnclear}: the mod could not work out who started the fight (see FightAnalysis), so an
 * honorable/neutral kill where the victim did not hit first must go to a judge.
 */
public record KillContext(
        Tier victimTier,
        boolean killerIsGuard,
        boolean victimHitFirst,
        boolean victimUnarmedAndUnarmored,
        boolean sameIp,
        boolean pairOnCooldown,
        boolean victimUnder5MinPlaytime,
        boolean fightUnclear) {

    /** Old shape: when the victim did not hit first the fight counts as unclear (goes to review). */
    public KillContext(Tier victimTier, boolean killerIsGuard, boolean victimHitFirst, boolean victimUnarmedAndUnarmored,
                       boolean sameIp, boolean pairOnCooldown, boolean victimUnder5MinPlaytime) {
        this(victimTier, killerIsGuard, victimHitFirst, victimUnarmedAndUnarmored, sameIp, pairOnCooldown,
                victimUnder5MinPlaytime, true);
    }
}
