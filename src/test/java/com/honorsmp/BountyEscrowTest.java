package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.BountyEscrow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BountyEscrowTest {
    record Item(String id, int count) {}

    static final BountyEscrow.Ops<Item> OPS = new BountyEscrow.Ops<>() {
        public boolean same(Item a, Item b) { return a.id().equals(b.id()); }
        public int count(Item t) { return t.count(); }
        public Item withCount(Item t, int n) { return new Item(t.id(), n); }
        public int maxStack(Item t) { return 64; }
    };

    static long count(List<Item> l) {
        return l.stream().mapToLong(Item::count).sum();
    }

    @Test
    void setAcceptedConserves() {
        var e = new BountyEscrow<Item>(OPS);
        var s = e.startSet("T");
        var back = s.commit(List.of(new Item("gold", 10), new Item("gold", 60), new Item("dia", 3)), true);
        assertTrue(back.isEmpty());
        assertEquals(73, e.total());
        assertEquals(73, e.itemsOn("T"));
        assertEquals(3, e.stacksOn("T")); // 64 + 6 gold, 3 diamonds
    }

    @Test
    void emptyCancels() {
        var e = new BountyEscrow<Item>(OPS);
        assertTrue(e.startSet("T").commit(List.of(), true).isEmpty());
        assertEquals(0, e.total());
        assertFalse(e.hasBounty("T"));
    }

    @Test
    void invalidTargetReturnsEverything() {
        var e = new BountyEscrow<Item>(OPS);
        var back = e.startSet("T").commit(List.of(new Item("gold", 5), new Item("dia", 2)), false);
        assertEquals(7, count(back));
        assertEquals(0, e.total());
    }

    @Test
    void closeTwiceCommitsOnce() {
        var e = new BountyEscrow<Item>(OPS);
        var s = e.startSet("T");
        List<Item> c = List.of(new Item("gold", 5));
        assertTrue(s.commit(c, true).isEmpty());
        assertTrue(s.commit(c, true).isEmpty());
        assertEquals(5, e.total());
        // invalid path twice returns once
        var s2 = e.startSet("T");
        assertEquals(5, count(s2.commit(c, false)));
        assertTrue(s2.commit(c, false).isEmpty());
        assertEquals(5, e.total());
    }

    @Test
    void bountiesStackFromManySetters() {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("T").commit(List.of(new Item("gold", 40)), true);
        e.startSet("T").commit(List.of(new Item("gold", 40)), true);
        e.startSet("U").commit(List.of(new Item("gold", 1)), true);
        assertEquals(80, e.itemsOn("T"));
        assertEquals(2, e.stacksOn("T"));
        assertEquals(81, e.total());
    }

    @Test
    void payoutMovesAllAndClears() {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("T").commit(List.of(new Item("gold", 40), new Item("dia", 2)), true);
        e.startSet("T").commit(List.of(new Item("gold", 30)), true);
        long before = e.total();
        assertEquals(72, e.payout("T", "K"));
        assertEquals(before, e.total());
        assertFalse(e.hasBounty("T"));
        assertEquals(72, e.boxItems("K"));
        assertEquals(0, e.payout("T", "K")); // nothing to pay twice
        assertEquals(72, e.total());
    }

    @Test
    void claimPartialTakeConserves() {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("T").commit(List.of(new Item("gold", 64), new Item("dia", 5), new Item("iron", 9)), true);
        e.payout("T", "K");
        List<Item> ui = e.openBox("K", 54);
        assertEquals(3, ui.size());
        assertEquals(0, e.total() - e.boxItems("K")); // box is empty while open
        assertEquals(0, e.total());
        // player takes gold and 2 diamonds
        List<Item> left = new ArrayList<>(List.of(new Item("dia", 3), new Item("iron", 9)));
        long taken = 64 + 2;
        assertTrue(e.closeBox("K", left));
        assertEquals(78 - taken, e.total());
        assertEquals(12, e.boxItems("K"));
    }

    @Test
    void closeBoxTwiceAndOpenTwice() {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("T").commit(List.of(new Item("gold", 10)), true);
        e.payout("T", "K");
        List<Item> ui = e.openBox("K", 54);
        assertNull(e.openBox("K", 54)); // second UI refused, nothing duplicated
        assertTrue(e.closeBox("K", ui));
        assertFalse(e.closeBox("K", ui)); // double close does nothing
        assertEquals(10, e.total());
    }

    @Test
    void overflowStaysOutsideUi() {
        var e = new BountyEscrow<Item>(OPS);
        List<Item> many = new ArrayList<>();
        for (int i = 0; i < 60; i++) many.add(new Item("item" + i, 1));
        e.startSet("T").commit(many, true);
        e.payout("T", "K");
        List<Item> ui = e.openBox("K", 54);
        assertEquals(54, ui.size());
        assertEquals(6, e.total()); // overflow remains stored
        // a payout while open lands in the box, not the UI
        e.startSet("U").commit(List.of(new Item("gold", 4)), true);
        e.payout("U", "K");
        assertEquals(10, e.total());
        e.closeBox("K", ui);
        assertEquals(64, e.total());
        assertEquals(64, e.boxItems("K"));
    }

    @Test
    void setterReturnAfterTargetKilledOnlyIfInvalid() {
        var e = new BountyEscrow<Item>(OPS);
        var s = e.startSet("T");
        // target killed (payout of nothing) while UI open: items still commit when valid
        assertEquals(0, e.payout("T", "K"));
        assertTrue(s.commit(List.of(new Item("gold", 3)), true).isEmpty());
        assertEquals(3, e.itemsOn("T"));
    }

    @Test
    void emeraldMigration() {
        var e = new BountyEscrow<Item>(OPS);
        long n = e.migrateEmeralds(Map.of("T", 150, "U", 0, "V", -4), c -> new Item("emerald", c));
        assertEquals(150, n);
        assertEquals(150, e.itemsOn("T"));
        assertEquals(3, e.stacksOn("T")); // 64 + 64 + 22
        assertFalse(e.hasBounty("U"));
        assertFalse(e.hasBounty("V"));
        assertEquals(150, e.total());
    }

    @Test
    void oldDataWithEmeraldBountiesStillLoads() {
        HonorData d = HonorData.parse("{\"version\":1,\"bounties\":{\"u1\":25}}");
        assertEquals(25, d.bounties.get("u1"));
        assertNotNull(d.bountyItems);
        assertNotNull(d.bountyBoxes);
    }

    @Test
    void randomizedConservation() {
        var e = new BountyEscrow<Item>(OPS);
        java.util.Random r = new java.util.Random(7);
        long held = 0; // items in the world outside the escrow (returned/taken)
        long created = 0;
        String[] ids = {"a", "b", "c", "d"};
        for (int i = 0; i < 2000; i++) {
            String t = "T" + r.nextInt(4), k = "K" + r.nextInt(3);
            switch (r.nextInt(5)) {
                case 0, 1 -> {
                    List<Item> c = new ArrayList<>();
                    for (int j = r.nextInt(4); j > 0; j--) c.add(new Item(ids[r.nextInt(4)], 1 + r.nextInt(64)));
                    long n = count(c);
                    created += n;
                    boolean valid = r.nextBoolean();
                    var s = e.startSet(t);
                    held += count(s.commit(c, valid));
                    held += count(s.commit(c, valid)); // double close
                }
                case 2 -> e.payout(t, k);
                case 3 -> {
                    List<Item> ui = e.openBox(k, 5);
                    if (ui != null) {
                        List<Item> left = new ArrayList<>();
                        for (Item it : ui) {
                            if (r.nextBoolean()) held += it.count();
                            else left.add(it);
                        }
                        e.closeBox(k, left);
                        e.closeBox(k, left);
                    }
                }
                default -> { }
            }
            // boxes open at this moment: none (always closed in the loop)
            assertEquals(created, e.total() + held, "step " + i);
        }
    }
}
