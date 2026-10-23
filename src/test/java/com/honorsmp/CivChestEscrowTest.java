package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.BountyEscrow;
import com.honorsmp.logic.CivChestEscrow;
import com.honorsmp.logic.CivChestEscrow.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CivChestEscrowTest {
    record Item(String id, int count) {}

    static final BountyEscrow.Ops<Item> OPS = new BountyEscrow.Ops<>() {
        public boolean same(Item a, Item b) { return a.id().equals(b.id()); }
        public int count(Item t) { return t.count(); }
        public Item withCount(Item t, int n) { return new Item(t.id(), n); }
        public int maxStack(Item t) { return 64; }
    };

    static long sum(List<Item> l) {
        return l.stream().mapToLong(Item::count).sum();
    }

    CivChestEscrow<Item> chest() {
        return new CivChestEscrow<>(OPS, 54);
    }

    @Test
    void whoMayUse() {
        assertTrue(CivChestEscrow.mayUse("L", "L", Set.of(), Set.of("L"), Set.of()));
        assertTrue(CivChestEscrow.mayUse("O", "L", Set.of("O"), Set.of(), Set.of()));
        assertTrue(CivChestEscrow.mayUse("M", "L", Set.of(), Set.of("M"), Set.of()));
        assertTrue(CivChestEscrow.mayUse("G", "L", Set.of(), Set.of(), Set.of("G")));
        assertFalse(CivChestEscrow.mayUse("X", "L", Set.of("O"), Set.of("M"), Set.of("G")));
        assertFalse(CivChestEscrow.mayUse(null, "L", Set.of(), Set.of(), Set.of()));
    }

    @Test
    void nonMemberRefusedAndLocksNothing() {
        var c = chest();
        assertEquals(Status.NOT_MEMBER, c.open("civ", "X", false).status());
        assertFalse(c.isOpen("civ"));
        assertEquals(Status.OK, c.open("civ", "A", true).status());
    }

    @Test
    void secondOpenerRefused() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        assertEquals(Status.OK, a.status());
        assertEquals(10, sum(a.items()));
        var b = c.open("civ", "B", true);
        assertEquals(Status.BUSY, b.status());
        assertTrue(b.items().isEmpty()); // B gets no stacks: nothing is held twice
        assertEquals("A", c.viewerOf("civ"));
        assertEquals("Someone in your civ is using the chest", CivChestEscrow.BUSY_MESSAGE);
        // another civ is independent
        assertEquals(Status.OK, c.open("other", "C", true).status());
    }

    @Test
    void lockReleasedOnCloseAndContentsComeBack() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        // A takes the gold out and puts diamonds in
        assertTrue(c.close("civ", "A", List.of(new Item("dia", 3))));
        assertFalse(c.isOpen("civ"));
        assertEquals(3, c.storedItems("civ"));
        var b = c.open("civ", "B", true);
        assertEquals(Status.OK, b.status());
        assertEquals(List.of(new Item("dia", 3)), b.items());
    }

    @Test
    void disconnectAfterCloseWritesNothingTwice() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        assertTrue(c.close("civ", "A", a.items()));
        // the disconnect hook closes again with whatever the dead inventory still shows
        assertFalse(c.close("civ", "A", a.items()));
        assertEquals(10, c.storedItems("civ"));
    }

    @Test
    void closeTwiceNoDupes() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        assertTrue(c.close("civ", "A", a.items()));
        assertFalse(c.close("civ", "A", a.items()));
        assertFalse(c.close("civ", "A", a.items()));
        assertEquals(10, c.storedItems("civ"));
    }

    @Test
    void staleViewerCannotCloseAfterNewOpener() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        assertTrue(c.close("civ", "A", a.items()));
        var b = c.open("civ", "B", true);
        assertEquals(10, sum(b.items()));
        // a late second close by A must not add its stale list on top of B's stacks
        assertFalse(c.close("civ", "A", a.items()));
        assertEquals(0, c.storedItems("civ"));
        assertTrue(c.close("civ", "B", b.items()));
        assertEquals(10, c.storedItems("civ"));
    }

    @Test
    void kickedViewerSessionClosedReleasesLock() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        // kicking A: the manager sees A is the viewer and closes the session with the live contents
        assertEquals("A", c.viewerOf("civ"));
        assertTrue(c.close("civ", "A", a.items()));
        assertNull(c.viewerOf("civ"));
        // A is no longer a member, so cannot reopen; a remaining member can
        assertEquals(Status.NOT_MEMBER, c.open("civ", "A", CivChestEscrow.mayUse("A", "L", Set.of(), Set.of("L"), Set.of())).status());
        assertEquals(Status.OK, c.open("civ", "L", true).status());
    }

    @Test
    void disbandMovesEverythingOutExactlyOnce() {
        var c = chest();
        c.load("civ", new Item("gold", 64));
        c.load("civ", new Item("gold", 6));
        c.load("civ", new Item("dia", 3));
        var out = c.disband("civ");
        assertEquals(73, sum(out));
        assertEquals(0, c.storedItems("civ"));
        assertTrue(c.disband("civ").isEmpty()); // second call hands out nothing
    }

    @Test
    void disbandRefusedWhileOpenThenWorksAfterClose() {
        var c = chest();
        c.load("civ", new Item("gold", 10));
        var a = c.open("civ", "A", true);
        assertNull(c.disband("civ")); // must close the viewer first
        // viewer takes 4 gold into inventory and closes with 6 left
        assertTrue(c.close("civ", "A", List.of(new Item("gold", 6))));
        var out = c.disband("civ");
        assertEquals(6, sum(out));
        assertTrue(c.disband("civ").isEmpty());
    }

    @Test
    void disbandEmptyChest() {
        var c = chest();
        assertTrue(c.disband("civ").isEmpty());
        var a = c.open("civ", "A", true);
        assertTrue(a.items().isEmpty());
        assertTrue(c.close("civ", "A", List.of()));
        assertTrue(c.disband("civ").isEmpty());
    }

    @Test
    void conservationAcrossManyOpensAndCloses() {
        var c = chest();
        c.load("civ", new Item("gold", 100));
        long inUi = 0;
        for (int i = 0; i < 10; i++) {
            var o = c.open("civ", "U" + i, true);
            assertEquals(Status.OK, o.status());
            assertEquals(Status.BUSY, c.open("civ", "V", true).status());
            inUi = sum(o.items());
            assertEquals(100, inUi);
            List<Item> keep = new ArrayList<>(o.items());
            assertTrue(c.close("civ", "U" + i, keep));
            assertFalse(c.close("civ", "U" + i, keep));
            assertEquals(100, c.storedItems("civ"));
        }
    }

    @Test
    void overflowBeyondScreenStaysStored() {
        var c = new CivChestEscrow<>(OPS, 2);
        c.load("civ", new Item("a", 1));
        c.load("civ", new Item("b", 1));
        c.load("civ", new Item("c", 1));
        var o = c.open("civ", "A", true);
        assertEquals(2, o.items().size());
        assertEquals(1, c.storedItems("civ"));
        assertTrue(c.close("civ", "A", o.items()));
        assertEquals(3, c.storedItems("civ"));
    }
}
