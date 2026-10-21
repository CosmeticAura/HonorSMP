package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.BountyEscrow;
import java.util.List;
import org.junit.jupiter.api.Test;

class DonationClaimBoxTest {
    record Item(String id, int count) {}

    static final BountyEscrow.Ops<Item> OPS = new BountyEscrow.Ops<>() {
        public boolean same(Item a, Item b) { return a.id().equals(b.id()); }
        public int count(Item t) { return t.count(); }
        public Item withCount(Item t, int n) { return new Item(t.id(), n); }
        public int maxStack(Item t) { return 64; }
    };

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
