package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.CivChestEscrowTest.Item;
import com.honorsmp.logic.CivChestEscrow;
import com.honorsmp.logic.CivChestEscrow.Status;
import com.honorsmp.logic.CivChestMinMembers;
import com.honorsmp.logic.HonorConfig;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 1.6.7: /civ chest needs civChestMinMembers members (leader included). */
class CivChestMinMembersTest {
    private static final int MIN = 3;

    private static int count(Set<String> members) {
        return CivChestMinMembers.count("L", Set.of(), members, Set.of());
    }

    /** Mirrors CivChest.open: the member rule first, then the escrow. */
    private static CivChestEscrow.Opened<Item> tryOpen(CivChestEscrow<Item> e, Set<String> members, int min, String user) {
        if (!CivChestMinMembers.unlocked(count(members), min)) return null;
        return e.open("civ", user, CivChestEscrow.mayUse(user, "L", Set.of(), members, Set.of()));
    }

    @Test
    void defaultIsThree() {
        assertEquals(3, new HonorConfig().civChestMinMembers);
    }

    @Test
    void twoMembersBlockedWithTheRightMessage() {
        Set<String> m = new LinkedHashSet<>(List.of("L", "A"));
        assertEquals(2, count(m));
        assertFalse(CivChestMinMembers.unlocked(2, MIN));
        assertEquals("Your civ needs 3 members to unlock the civ chest (you have 2).", CivChestMinMembers.message(MIN, 2));
        assertEquals("Civ chest: locked, needs 3 members", CivChestMinMembers.infoLine(2, MIN));
        CivChestEscrow<Item> e = new CivChestEscrow<>(CivChestEscrowTest.OPS, 54);
        assertNull(tryOpen(e, m, MIN, "A"), "blocked before the escrow is touched");
        assertFalse(e.isOpen("civ"));
    }

    @Test
    void threeMembersAllowed() {
        Set<String> m = new LinkedHashSet<>(List.of("L", "A", "B"));
        assertEquals(3, count(m));
        assertTrue(CivChestMinMembers.unlocked(3, MIN));
        assertEquals("Civ chest: unlocked", CivChestMinMembers.infoLine(3, MIN));
        CivChestEscrow<Item> e = new CivChestEscrow<>(CivChestEscrowTest.OPS, 54);
        var res = tryOpen(e, m, MIN, "B");
        assertNotNull(res);
        assertEquals(Status.OK, res.status());
    }

    @Test
    void countIsDistinctAndIncludesTheLeader() {
        // leader also in members, an officer and a guard who are also members: counted once each
        assertEquals(3, CivChestMinMembers.count("L", Set.of("O"), Set.of("L", "O", "G"), Set.of("G")));
        // leader missing from the members set still counts
        assertEquals(2, CivChestMinMembers.count("L", Set.of(), Set.of("A"), Set.of()));
    }

    @Test
    void droppingBelowLocksAndKeepsItemsThenBackToThreeReturnsThem() {
        Set<String> m = new LinkedHashSet<>(List.of("L", "A", "B"));
        CivChestEscrow<Item> e = new CivChestEscrow<>(CivChestEscrowTest.OPS, 54);
        // B opens, puts in stacks, someone leaves while it is open
        var res = tryOpen(e, m, MIN, "B");
        assertEquals(Status.OK, res.status());
        List<Item> screen = new ArrayList<>(res.items());
        screen.add(new Item("diamond", 40));
        screen.add(new Item("iron", 64));
        m.remove("A"); // civ is now 2: CivChest.recheck closes the viewer, stacks go back once
        assertFalse(CivChestMinMembers.unlocked(count(m), MIN));
        assertTrue(e.close("civ", "B", screen));
        assertFalse(e.close("civ", "B", screen), "a second close (disconnect after the forced close) adds nothing");
        assertEquals(104, CivChestEscrowTest.sum(e.stored("civ")), "nothing lost, nothing duplicated");
        // locked: nobody opens, the items stay
        assertNull(tryOpen(e, m, MIN, "B"));
        assertNull(tryOpen(e, m, MIN, "L"));
        assertEquals(104, CivChestEscrowTest.sum(e.stored("civ")));
        // back to 3: the items are still there
        m.add("C");
        var again = tryOpen(e, m, MIN, "C");
        assertNotNull(again);
        assertEquals(Status.OK, again.status());
        assertEquals(104, CivChestEscrowTest.sum(again.items()));
        assertEquals(0, CivChestEscrowTest.sum(e.stored("civ")), "moved to the viewer, not copied");
    }

    @Test
    void configValueIsRespected() {
        Set<String> two = new LinkedHashSet<>(List.of("L", "A"));
        Set<String> four = new LinkedHashSet<>(List.of("L", "A", "B", "C"));
        // lower minimum: 2 members are enough
        assertTrue(CivChestMinMembers.unlocked(count(two), 2));
        assertEquals("Civ chest: unlocked", CivChestMinMembers.infoLine(count(two), 2));
        // higher minimum: 4 members are not enough for 5
        assertFalse(CivChestMinMembers.unlocked(count(four), 5));
        assertEquals("Your civ needs 5 members to unlock the civ chest (you have 4).", CivChestMinMembers.message(5, 4));
        assertEquals("Civ chest: locked, needs 5 members", CivChestMinMembers.infoLine(4, 5));
        // 1 (or less) never blocks, a civ always has its leader
        assertTrue(CivChestMinMembers.unlocked(count(Set.of("L")), 1));
        assertTrue(CivChestMinMembers.unlocked(1, 0));
        assertEquals("Your civ dropped below 5 members, so the civ chest was closed. Its items are kept.", CivChestMinMembers.closedMessage(5));
    }
}
