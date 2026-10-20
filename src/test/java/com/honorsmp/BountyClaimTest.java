package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.BountyEscrow;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.KillContext;
import com.honorsmp.logic.KillOutcome;
import com.honorsmp.logic.KillRules;
import com.honorsmp.logic.Tier;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Reproduces "I killed the bountied dishonored player and /bounty claim gave nothing". */
class BountyClaimTest {
    record Item(String id, int count) {}

    static final BountyEscrow.Ops<Item> OPS = new BountyEscrow.Ops<>() {
        public boolean same(Item a, Item b) { return a.id().equals(b.id()); }
        public int count(Item t) { return t.count(); }
        public Item withCount(Item t, int n) { return new Item(t.id(), n); }
        public int maxStack(Item t) { return 64; }
    };

    /** Runs the same decision Honor.onPlayerKill makes and pays out if it says so. */
    static long killWithBounty(KillContext ctx, boolean newbie) {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("victim").commit(List.of(new Item("diamond", 5)), true);
        KillOutcome o = KillRules.evaluate(ctx, new HonorConfig());
        if (KillRules.bountyPays(o, newbie)) e.payout("victim", "killer");
        return e.boxItems("killer");
    }

    @Test
    void normalKillOfDishonoredPaysBounty() {
        assertEquals(5, killWithBounty(new KillContext(Tier.OUTLAW, false, false, false, false, false, false), false));
    }

    @Test
    void victimWithUnder5MinPlaytimeStillPaysBounty() {
        // a test victim set to dishonored a minute after joining: honor is withheld but the bounty is owed
        assertEquals(5, killWithBounty(new KillContext(Tier.OUTLAW, false, false, false, false, false, true), true));
    }

    @Test
    void sameIpKillDoesNotPayBountyAndItStays() {
        assertEquals(0, killWithBounty(new KillContext(Tier.MARAUDER, false, false, false, true, false, false), false));
    }

    @Test
    void sameIpBountyStaysOnTarget() {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("victim").commit(List.of(new Item("diamond", 5)), true);
        KillOutcome o = KillRules.evaluate(new KillContext(Tier.NEUTRAL, false, false, false, true, false, false), new HonorConfig());
        assertTrue(o.ipFlag());
        if (KillRules.bountyPays(o, false)) e.payout("victim", "killer");
        assertEquals(5, e.itemsOn("victim"));
        assertEquals(0, e.boxItems("killer"));
    }

    @Test
    void bountyPaysRule() {
        assertTrue(KillRules.bountyPays(false));
        assertFalse(KillRules.bountyPays(true)); // judge killer on same IP
        for (Tier t : Tier.values()) {
            KillOutcome o = KillRules.evaluate(new KillContext(t, false, false, false, false, false, false), new HonorConfig());
            assertTrue(KillRules.bountyPays(o, false));
            KillOutcome ip = KillRules.evaluate(new KillContext(t, false, false, false, true, false, false), new HonorConfig());
            assertFalse(KillRules.bountyPays(ip, false));
            assertFalse(KillRules.bountyPays(ip, true));
        }
    }

    @Test
    void repeatKillInsideAntiFarmWindowStillPaysBounty() {
        assertEquals(5, killWithBounty(new KillContext(Tier.WARLORD, false, false, false, false, true, false), false));
    }

    @Test
    void claimBoxSurvivesCloseAndReload() {
        var e = new BountyEscrow<Item>(OPS);
        e.startSet("victim").commit(List.of(new Item("diamond", 5)), true);
        e.payout("victim", "killer");
        // save: what sync() writes, then load into a fresh escrow
        var saved = e.boxes().get("killer");
        var fresh = new BountyEscrow<Item>(OPS);
        for (Item i : saved) fresh.loadBox("killer", i);
        assertEquals(5, fresh.boxItems("killer"));
        assertEquals(0, fresh.itemsOn("victim"));
    }
}
