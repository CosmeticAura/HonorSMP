package com.honorsmp.logic;

/** Everything the kill rules need to know, free of Minecraft types. */
public record KillContext(
        Tier victimTier,
        boolean killerIsGuardOnOwnLand,
        boolean victimHitFirst,
        boolean victimUnarmedAndUnarmored,
        boolean sameIp,
        boolean pairOnCooldown,
        boolean victimUnder5MinPlaytime) {}
