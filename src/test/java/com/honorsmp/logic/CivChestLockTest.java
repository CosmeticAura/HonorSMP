package com.honorsmp.logic;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 1.6.6: civ chest combat lock. */
class CivChestLockTest {
    private static final int WINDOW = 300;
    private static final long T0 = 1_000_000L;

    @Test
    void lockLastsTheConfiguredTimeAndCountsDownRoundedUp() {
        CivChestLock l = new CivChestLock();
        assertEquals(0, l.remainingSeconds("a", T0, WINDOW), "never hit");
        assertTrue(l.hit("a", T0, "b", null, null, null));
        assertEquals(300, l.remainingSeconds("a", T0, WINDOW));
        assertEquals(300, l.remainingSeconds("a", T0 + 1, WINDOW), "rounded up, still the full 5:00 a millisecond later");
        assertEquals(240, l.remainingSeconds("a", T0 + 60_000, WINDOW));
        assertEquals(1, l.remainingSeconds("a", T0 + 299_001, WINDOW));
        assertEquals(0, l.remainingSeconds("a", T0 + 300_000, WINDOW), "free exactly at 5 minutes");
        assertEquals(0, l.remainingSeconds("a", T0 + 400_000, WINDOW));
        assertEquals(0, l.remainingSeconds("someoneElse", T0, WINDOW), "only the victim is locked");
    }

    @Test
    void aNewHitResetsTheTimer() {
        CivChestLock l = new CivChestLock();
        l.hit("a", T0, "b", null, null, null);
        assertEquals(120, l.remainingSeconds("a", T0 + 180_000, WINDOW));
        l.hit("a", T0 + 180_000, "c", null, null, null);
        assertEquals(300, l.remainingSeconds("a", T0 + 180_000, WINDOW));
        assertEquals(60, l.remainingSeconds("a", T0 + 180_000 + 240_000, WINDOW));
    }

    @Test
    void zeroOrNegativeWindowTurnsTheLockOff() {
        CivChestLock l = new CivChestLock();
        l.hit("a", T0, "b", null, null, null);
        assertEquals(0, l.remainingSeconds("a", T0, 0));
        assertEquals(0, l.remainingSeconds("a", T0, -5));
    }

    @Test
    void mobDamageDoesNotLock() {
        CivChestLock l = new CivChestLock();
        // zombie, skeleton arrow from a skeleton, creeper, fall, fire, siege mob: no player anywhere in the damage source
        assertFalse(l.hit("a", T0, null, null, null, null));
        assertEquals(0, l.remainingSeconds("a", T0, WINDOW));
        assertNull(CivChestLock.culprit("a", null, null, null, null));
    }

    @Test
    void ownDamageDoesNotLock() {
        CivChestLock l = new CivChestLock();
        // own arrow, own pet, own TNT, own crystal
        assertFalse(l.hit("a", T0, "a", null, null, null));
        assertFalse(l.hit("a", T0, null, "a", "a", "a"));
        assertEquals(0, l.remainingSeconds("a", T0, WINDOW));
    }

    @Test
    void indirectPlayerDamageLocks() {
        // arrow / trident / potion / firework: the attacker is the shooter (a player)
        assertEquals("b", CivChestLock.culprit("a", "b", null, "b", null));
        // the attacker slot is empty but the direct source (projectile) is owned by a player
        assertEquals("b", CivChestLock.culprit("a", null, null, null, "b"));
        // TNT or end crystal set off by a player: the causing entity is the player's TNT/crystal, owner b
        assertEquals("b", CivChestLock.culprit("a", null, "b", null, null));
        // a player's pet (wolf) bites: attacker is the wolf, owner b
        assertEquals("b", CivChestLock.culprit("a", null, "b", null, "b"));
        // a pet owned by someone else in a mixed source: first other player wins, own never counts
        assertEquals("c", CivChestLock.culprit("a", "a", null, "c", null));

        for (String[] c : new String[][] {{"b", null, null, null}, {null, "b", null, null}, {null, null, "b", null}, {null, null, null, "b"}}) {
            CivChestLock l = new CivChestLock();
            assertTrue(l.hit("a", T0, c[0], c[1], c[2], c[3]));
            assertEquals(300, l.remainingSeconds("a", T0, WINDOW));
        }
    }

    @Test
    void clearForgetsTheLock() {
        CivChestLock l = new CivChestLock();
        l.hit("a", T0, "b", null, null, null);
        l.hit("c", T0, "b", null, null, null);
        l.clear("a");
        assertEquals(0, l.remainingSeconds("a", T0, WINDOW));
        assertEquals(300, l.remainingSeconds("c", T0, WINDOW));
        l.clearAll();
        assertEquals(0, l.remainingSeconds("c", T0, WINDOW));
    }

    @Test
    void messagesAreFormattedAsMSS() {
        assertEquals("You were in a fight. The civ chest opens again in 5:00.", CivChestLock.message(300));
        assertEquals("You were in a fight. The civ chest opens again in 4:07.", CivChestLock.message(247));
        assertEquals("You were in a fight. The civ chest opens again in 0:09.", CivChestLock.message(9));
        assertEquals("You were in a fight. The civ chest opens again in 0:01.", CivChestLock.message(1));
        assertEquals("You were in a fight. The civ chest opens again in 10:00.", CivChestLock.message(600));
    }

    /** Hit while the chest is open: the screen closes with the items as they were; nothing lost or duplicated. */
    @Test
    void chestClosesWhenHitWhileOpenAndKeepsExactlyTheScreenContents() {
        CivChestEscrow<String> escrow = new CivChestEscrow<>(new TestOps(), 54);
        escrow.load("c1", "diamond x3");
        escrow.load("c1", "bread x64");
        CivChestLock lock = new CivChestLock();

        var opened = escrow.open("c1", "a", true);
        assertEquals(CivChestEscrow.Status.OK, opened.status());
        List<String> screen = new ArrayList<>(opened.items());
        screen.remove("bread x64");          // the player took the bread out
        screen.add("iron x8");               // and put iron in
        assertEquals("a", escrow.viewerOf("c1"));

        // b hits a: lock starts and the screen closes with what is in it right now
        boolean counted = lock.hit("a", T0, null, "b", null, null);
        assertTrue(counted);
        if (counted && "a".equals(escrow.viewerOf("c1"))) assertTrue(escrow.close("c1", "a", screen));
        // the vanilla onClosed callback runs close again: harmless
        assertFalse(escrow.close("c1", "a", screen));

        assertNull(escrow.viewerOf("c1"));
        assertEquals(List.of("diamond x3", "iron x8"), escrow.stored("c1"));
        assertTrue(lock.remainingSeconds("a", T0 + 1000, WINDOW) > 0, "cannot reopen right away");
        // and another member can use it at once, no duplicate stacks
        var again = escrow.open("c1", "z", true);
        assertEquals(List.of("diamond x3", "iron x8"), again.items());
    }

    /** Minimal item type for the escrow test: "name xN". */
    private static final class TestOps implements BountyEscrow.Ops<String> {
        @Override public int count(String t) { return Integer.parseInt(t.substring(t.lastIndexOf('x') + 1)); }
        @Override public int maxStack(String t) { return 64; }
        @Override public boolean same(String a, String b) { return a.substring(0, a.lastIndexOf('x')).equals(b.substring(0, b.lastIndexOf('x'))); }
        @Override public String withCount(String t, int n) { return t.substring(0, t.lastIndexOf('x') + 1) + n; }
    }
}
