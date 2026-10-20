package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonPrimitive;
import com.honorsmp.logic.AntiFarm;
import com.honorsmp.logic.ConfirmWindow;
import com.honorsmp.logic.CombatTimer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResetAllTest {
    @Test
    void wipeEmptiesEverythingExceptJudges() {
        HonorData d = new HonorData();
        AntiFarm af = new AntiFarm();
        d.antiFarm = af.raw();
        d.players.put("a", new HonorData.PlayerRecord());
        d.civs.put("c", new HonorData.Civ());
        d.bounties.put("a", 5);
        d.bountyItems.put("a", List.of(new JsonPrimitive("x")));
        d.bountyBoxes.put("a", List.of(new JsonPrimitive("x")));
        d.bountyPending.put("a", List.of(new JsonPrimitive("x")));
        d.logs.add(new HonorData.LogEntry());
        d.cases.add(new HonorData.Case());
        d.fines.add(new HonorData.Fine());
        d.treasury.add(new JsonPrimitive("x"));
        d.treasuryEmeralds = 9;
        d.pendingBooks.put("j", List.of(1));
        af.tryCount("a", "b", 1000, 5000);
        d.judges.add("j");
        d.nextLogId = 50;
        d.nextCaseId = 7;
        d.nextFineId = 4;

        d.wipeAllExceptJudges();

        assertTrue(d.players.isEmpty());
        assertTrue(d.civs.isEmpty());
        assertTrue(d.bounties.isEmpty());
        assertTrue(d.bountyItems.isEmpty());
        assertTrue(d.bountyBoxes.isEmpty());
        assertTrue(d.bountyPending.isEmpty());
        assertTrue(d.logs.isEmpty());
        assertTrue(d.cases.isEmpty());
        assertTrue(d.fines.isEmpty());
        assertTrue(d.treasury.isEmpty());
        assertEquals(0, d.treasuryEmeralds);
        assertTrue(d.pendingBooks.isEmpty());
        assertTrue(d.antiFarm.isEmpty());
        assertTrue(af.raw().isEmpty(), "live anti-farm map is shared and cleared in place");
        assertTrue(af.wouldCount("a", "b", 1001, 5000));
        assertEquals(1, d.nextLogId);
        assertEquals(1, d.nextCaseId);
        assertEquals(1, d.nextFineId);
        assertEquals(java.util.Set.of("j"), d.judges);
    }

    @Test
    void combatTimerClearsAll() {
        CombatTimer t = new CombatTimer();
        t.record("a", 1000);
        t.clearAll();
        assertEquals(0, t.remainingSeconds("a", 1000, 30));
    }

    @Test
    void confirmWindow() {
        ConfirmWindow w = new ConfirmWindow();
        UUID u = UUID.randomUUID();
        assertFalse(w.confirm(u, 0, 10_000));
        assertTrue(w.confirm(u, 10_000, 10_000));
        assertFalse(w.confirm(u, 11_000, 10_000), "confirmation is used up");
        assertFalse(w.confirm(u, 22_000, 10_000), "too late: counts as a new first run");
        assertTrue(w.confirm(u, 23_000, 10_000));
        UUID other = UUID.randomUUID();
        assertFalse(w.confirm(other, 23_500, 10_000), "other callers do not share the window");
    }
}
