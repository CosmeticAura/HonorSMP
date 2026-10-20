package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class HonorLogicTest {
    private final HonorConfig c = new HonorConfig();

    // ---- tier lookup ----

    @Test
    void tierBoundaries() {
        assertEquals(Tier.PARAGON, Tier.of(1000, c));
        assertEquals(Tier.PARAGON, Tier.of(800, c));
        assertEquals(Tier.NOBLE, Tier.of(799, c));
        assertEquals(Tier.NOBLE, Tier.of(400, c));
        assertEquals(Tier.HONORABLE, Tier.of(399, c));
        assertEquals(Tier.HONORABLE, Tier.of(100, c));
        assertEquals(Tier.NEUTRAL, Tier.of(99, c));
        assertEquals(Tier.NEUTRAL, Tier.of(0, c));
        assertEquals(Tier.NEUTRAL, Tier.of(-99, c));
        assertEquals(Tier.OUTLAW, Tier.of(-100, c));
        assertEquals(Tier.OUTLAW, Tier.of(-399, c));
        assertEquals(Tier.MARAUDER, Tier.of(-400, c));
        assertEquals(Tier.MARAUDER, Tier.of(-799, c));
        assertEquals(Tier.WARLORD, Tier.of(-800, c));
        assertEquals(Tier.WARLORD, Tier.of(-1000, c));
    }

    @Test
    void sidesAndPerkIndex() {
        assertEquals(Side.HONORABLE, Tier.NOBLE.side);
        assertEquals(Side.DISHONORED, Tier.MARAUDER.side);
        assertEquals(Side.NEUTRAL, Tier.NEUTRAL.side);
        assertEquals(0, Tier.HONORABLE.perkIndex());
        assertEquals(2, Tier.PARAGON.perkIndex());
        assertEquals(0, Tier.OUTLAW.perkIndex());
        assertEquals(2, Tier.WARLORD.perkIndex());
        assertEquals(-1, Tier.NEUTRAL.perkIndex());
        assertTrue(Tier.NOBLE.isNobleOrHigher());
        assertFalse(Tier.HONORABLE.isNobleOrHigher());
    }

    @Test
    void tiersFollowConfig() {
        c.nobleMin = 300;
        c.outlawMax = -50;
        assertEquals(Tier.NOBLE, Tier.of(300, c));
        assertEquals(Tier.OUTLAW, Tier.of(-50, c));
        assertEquals(Tier.NEUTRAL, Tier.of(-49, c));
    }

    // ---- score math ----

    @Test
    void scoreClampsAtBothEnds() {
        assertEquals(1000, ScoreMath.apply(990, 25, c));
        assertEquals(-1000, ScoreMath.apply(-990, -60, c));
        assertEquals(25, ScoreMath.apply(0, 25, c));
        assertEquals(-30, ScoreMath.apply(0, -30, c));
        assertEquals(1000, ScoreMath.clamp(5000L, c));
        assertEquals(-1000, ScoreMath.clamp(-5000L, c));
    }

    @Test
    void dailyCapStopsAtFifty() {
        int used = 0;
        for (int i = 0; i < 80; i++) {
            int d = ScoreMath.dailyCapped(used, 50, -1);
            used += Math.abs(d);
        }
        assertEquals(50, used);
        assertEquals(0, ScoreMath.dailyCapped(50, 50, -1));
        // container: -5 each, cap 50 -> ten opens count, 11th is free
        used = 0;
        int total = 0;
        for (int i = 0; i < 11; i++) {
            int d = ScoreMath.dailyCapped(used, 50, -5);
            used += Math.abs(d);
            total += d;
        }
        assertEquals(-50, total);
        // partial last step
        assertEquals(-3, ScoreMath.dailyCapped(47, 50, -5));
    }

    // ---- first hit rule ----

    private static final long S = 1000;

    @Test
    void victimHitFirstIsSelfDefense() {
        long now = 100_000;
        List<DamageEvent> h = List.of(
                new DamageEvent(now - 10 * S, "victim", "killer", 4),
                new DamageEvent(now - 8 * S, "killer", "victim", 6),
                new DamageEvent(now - 1 * S, "killer", "victim", 10));
        assertTrue(FirstHitRule.victimHitFirst(h, "killer", "victim", now, 15 * S));
    }

    @Test
    void killerHitFirstIsNotSelfDefense() {
        long now = 100_000;
        List<DamageEvent> h = List.of(
                new DamageEvent(now - 10 * S, "killer", "victim", 4),
                new DamageEvent(now - 8 * S, "victim", "killer", 6),
                new DamageEvent(now - 1 * S, "killer", "victim", 10));
        assertFalse(FirstHitRule.victimHitFirst(h, "killer", "victim", now, 15 * S));
    }

    @Test
    void noExchangeIsNotSelfDefense() {
        assertFalse(FirstHitRule.victimHitFirst(List.of(), "killer", "victim", 100_000, 15 * S));
    }

    @Test
    void oldHitOutsideWindowDoesNotCount() {
        long now = 1_000_000;
        // victim hit the killer 60s ago, then nothing until the killer attacked at 5s ago
        List<DamageEvent> h = List.of(
                new DamageEvent(now - 60 * S, "victim", "killer", 4),
                new DamageEvent(now - 5 * S, "killer", "victim", 10));
        assertFalse(FirstHitRule.victimHitFirst(h, "killer", "victim", now, 15 * S));
    }

    @Test
    void waitingOutTheWindowCannotLaunderAFight() {
        long now = 1_000_000;
        // killer starts, hits keep coming less than 15s apart, victim answers later
        List<DamageEvent> h = List.of(
                new DamageEvent(now - 40 * S, "killer", "victim", 2),
                new DamageEvent(now - 28 * S, "killer", "victim", 2),
                new DamageEvent(now - 16 * S, "victim", "killer", 2),
                new DamageEvent(now - 3 * S, "killer", "victim", 8));
        assertFalse(FirstHitRule.victimHitFirst(h, "killer", "victim", now, 15 * S));
        assertEquals("killer", FirstHitRule.firstHit(h, "killer", "victim", now, 15 * S).attacker());
    }

    @Test
    void thirdPartiesAreIgnored() {
        long now = 100_000;
        List<DamageEvent> h = List.of(
                new DamageEvent(now - 12 * S, "other", "killer", 4),
                new DamageEvent(now - 5 * S, "killer", "victim", 10));
        assertFalse(FirstHitRule.victimHitFirst(h, "killer", "victim", now, 15 * S));
        assertEquals(1, FirstHitRule.fight(h, "killer", "victim", now, 15 * S).size());
    }

    // ---- anti farming ----

    @Test
    void sameVictimOnlyCountsOncePer30Minutes() {
        AntiFarm a = new AntiFarm();
        long min = 60_000, cd = 30 * min;
        assertTrue(a.tryCount("k", "v", 0, cd));
        assertFalse(a.tryCount("k", "v", 10 * min, cd));
        assertFalse(a.tryCount("k", "v", 29 * min, cd));
        assertTrue(a.tryCount("k", "v", 30 * min, cd));
        // different pair and reverse direction are independent
        assertTrue(a.tryCount("k", "w", 5 * min, cd));
        assertTrue(a.tryCount("v", "k", 5 * min, cd));
    }

    @Test
    void failedAttemptDoesNotResetCooldown() {
        AntiFarm a = new AntiFarm();
        long min = 60_000, cd = 30 * min;
        assertTrue(a.tryCount("k", "v", 0, cd));
        assertFalse(a.tryCount("k", "v", 20 * min, cd));
        assertTrue(a.tryCount("k", "v", 31 * min, cd));
    }

    // ---- kill table ----

    private KillContext ctx(Tier victim, boolean guard, boolean hitFirst, boolean unarmed, boolean sameIp,
                            boolean cooldown, boolean newbie) {
        return new KillContext(victim, guard, hitFirst, unarmed, sameIp, cooldown, newbie);
    }

    @Test
    void killDishonored() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.OUTLAW, false, false, false, false, false, false), c);
        assertEquals(25, o.delta());
        assertFalse(o.review());
    }

    @Test
    void guardGetsBonusAnywhere() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.WARLORD, true, false, false, false, false, false), c);
        assertEquals(35, o.delta());
    }

    @Test
    void selfDefenseIsFreeAndNotReviewed() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.NOBLE, false, true, false, false, false, false), c);
        assertEquals(0, o.delta());
        assertFalse(o.review());
        // self defense wins even against a neutral unarmed victim
        o = KillRules.evaluate(ctx(Tier.NEUTRAL, false, true, true, false, false, false), c);
        assertEquals(0, o.delta());
    }

    @Test
    void unarmedUnarmoredIsMinus60() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.HONORABLE, false, false, true, false, false, false), c);
        assertEquals(-60, o.delta());
        assertFalse(o.review());
    }

    @Test
    void noFirstHitIsProvisionalMinus30WithReview() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.NEUTRAL, false, false, false, false, false, false), c);
        assertEquals(-30, o.delta());
        assertTrue(o.review());
    }

    @Test
    void sameIpNeverCountsAndFlags() {
        for (Tier t : Tier.values()) {
            KillOutcome o = KillRules.evaluate(ctx(t, false, false, false, true, false, false), c);
            assertEquals(0, o.delta());
            assertTrue(o.ipFlag());
            assertFalse(o.counted());
        }
    }

    @Test
    void cooldownKillGivesNothing() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.OUTLAW, false, false, false, false, true, false), c);
        assertEquals(0, o.delta());
        assertFalse(o.counted());
        o = KillRules.evaluate(ctx(Tier.NEUTRAL, false, false, true, false, true, false), c);
        assertEquals(0, o.delta());
    }

    @Test
    void freshlyRespawnedVictimNeverGivesPositiveHonor() {
        KillOutcome o = KillRules.evaluate(ctx(Tier.WARLORD, true, false, false, false, false, true), c);
        assertEquals(0, o.delta());
        // but it does not erase penalties for killing an honorable player
        o = KillRules.evaluate(ctx(Tier.NOBLE, false, false, true, false, false, true), c);
        assertEquals(-60, o.delta());
    }
}
