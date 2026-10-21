package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.BountyEscrow;
import com.honorsmp.logic.DonationSplit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DonationSplitTest {
    record Item(String id, int count) {}

    static final BountyEscrow.Ops<Item> OPS = new BountyEscrow.Ops<>() {
        public boolean same(Item a, Item b) { return a.id().equals(b.id()); }
        public int count(Item t) { return t.count(); }
        public Item withCount(Item t, int n) { return new Item(t.id(), n); }
        public int maxStack(Item t) { return 64; }
    };

    @Test void evenSplit() {
        var r = DonationSplit.split(0, 20, 10);
        assertEquals(2, r.each());
        assertEquals(0, r.newPot());
    }

    @Test void unevenSplitKeepsRemainder() {
        var r = DonationSplit.split(0, 23, 10);
        assertEquals(2, r.each());
        assertEquals(3, r.newPot());
    }

    @Test void specExamplePotCarriesOver() {
        Map<String, Long> pots = new LinkedHashMap<>();
        var first = DonationSplit.apply(pots, Map.of("minecraft:golden_apple", 9L), 10);
        assertEquals(0L, first.get("minecraft:golden_apple"));
        assertEquals(9L, pots.get("minecraft:golden_apple"));
        var second = DonationSplit.apply(pots, Map.of("minecraft:golden_apple", 5L), 10);
        assertEquals(1L, second.get("minecraft:golden_apple"));
        assertEquals(4L, pots.get("minecraft:golden_apple"));
    }

    @Test void potsAreKeptPerItemType() {
        Map<String, Long> pots = new LinkedHashMap<>();
        DonationSplit.apply(pots, Map.of("minecraft:bread", 3L), 4);
        var r = DonationSplit.apply(pots, Map.of("minecraft:ender_pearl", 5L), 4);
        assertEquals(1L, r.get("minecraft:ender_pearl"));
        assertEquals(3L, pots.get("minecraft:bread"));
        assertEquals(1L, pots.get("minecraft:ender_pearl"));
    }

    @Test void oneRecipientGetsEverything() {
        Map<String, Long> pots = new LinkedHashMap<>();
        var r = DonationSplit.apply(pots, Map.of("minecraft:bread", 64L), 1);
        assertEquals(64L, r.get("minecraft:bread"));
        assertTrue(pots.isEmpty());
    }

    @Test void zeroRecipientsTouchesNothing() {
        Map<String, Long> pots = new LinkedHashMap<>(Map.of("minecraft:bread", 3L));
        assertNull(DonationSplit.apply(pots, Map.of("minecraft:bread", 10L), 0));
        assertEquals(Map.of("minecraft:bread", 3L), pots);
        assertThrows(IllegalArgumentException.class, () -> DonationSplit.split(0, 1, 0));
    }

    @Test void nothingIsLostOrCreated() {
        Map<String, Long> pots = new LinkedHashMap<>();
        long given = 0, recipients = 7, donatedTotal = 0;
        for (long n : new long[] {5, 13, 1, 64, 100, 3}) {
            donatedTotal += n;
            var r = DonationSplit.apply(pots, Map.of("x", n), (int) recipients);
            given += r.get("x") * recipients;
            assertTrue(pots.getOrDefault("x", 0L) < recipients);
        }
        assertEquals(donatedTotal, given + pots.getOrDefault("x", 0L));
    }

    @Test void claimBoxEscrowOpenCloseConserves() {
        var e = new BountyEscrow<Item>(OPS);
        e.addToBox("A", new Item("bread", 100));
        assertEquals(100, e.boxItems("A"));
        var shown = e.openBox("A", 45);
        assertNotNull(shown);
        assertNull(e.openBox("A", 45)); // can't open twice
        assertEquals(0, e.boxItems("A"));
        // player took 64, 36 remain on the screen
        assertTrue(e.closeBox("A", List.of(new Item("bread", 36))));
        assertFalse(e.closeBox("A", List.of(new Item("bread", 36)))); // closing twice returns nothing twice
        assertEquals(36, e.boxItems("A"));
    }

    @Test void claimBoxPagesKeepTheRest() {
        var e = new BountyEscrow<Item>(OPS);
        for (int i = 0; i < 50; i++) e.addToBox("A", new Item("item" + i, 1));
        var page = e.openBox("A", 45);
        assertEquals(45, page.size());
        assertEquals(5, e.boxItems("A"));
        e.closeBox("A", page);
        assertEquals(50, e.boxItems("A"));
    }
}
