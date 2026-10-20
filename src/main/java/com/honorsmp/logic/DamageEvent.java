package com.honorsmp.logic;

/** One player-vs-player damage event. */
public record DamageEvent(long timeMs, String attacker, String victim, float amount) {}
