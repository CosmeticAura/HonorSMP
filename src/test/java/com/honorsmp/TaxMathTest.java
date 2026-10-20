package com.honorsmp;

import static com.honorsmp.logic.TaxMath.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaxMathTest {
    static final long NOW = 1_000_000_000_000L;

    @Test
    void amountIsPerMember() {
        assertEquals(40, amountDue(5, 8));
        assertEquals(0, amountDue(0, 8));
    }

    @Test
    void oldCivsStartAfterLoad() {
        assertEquals(NOW + 7 * DAY_MS, initialDue(NOW - 90 * DAY_MS, NOW, 7));
        assertEquals(NOW + 10 * DAY_MS, initialDue(NOW + 3 * DAY_MS, NOW, 7));
    }

    @Test
    void daysLateCountsStartedDays() {
        assertEquals(0, daysLate(NOW, NOW));
        assertEquals(1, daysLate(NOW + 1, NOW));
        assertEquals(1, daysLate(NOW + DAY_MS, NOW));
        assertEquals(2, daysLate(NOW + DAY_MS + 1, NOW));
        assertTrue(isLate(NOW + 1, NOW, false));
        assertFalse(isLate(NOW + 1, NOW, true));
        assertFalse(isLate(NOW, NOW, false));
    }

    @Test
    void penaltyOnlyForUnappliedDays() {
        assertEquals(3, penaltyDaysOwed(NOW + 2 * DAY_MS + 5, NOW, 0));
        assertEquals(1, penaltyDaysOwed(NOW + 2 * DAY_MS + 5, NOW, 2));
        assertEquals(0, penaltyDaysOwed(NOW + 2 * DAY_MS + 5, NOW, 3));
        assertEquals(0, penaltyDaysOwed(NOW - 1, NOW, 0));
    }

    @Test
    void warningWindowOncePerCycle() {
        long due = NOW + 23 * HOUR_MS;
        assertTrue(shouldWarn(NOW, due, false, false, 24));
        assertFalse(shouldWarn(NOW, due, false, true, 24));
        assertFalse(shouldWarn(NOW, due, true, false, 24));
        assertFalse(shouldWarn(NOW, NOW + 25 * HOUR_MS, false, false, 24));
        assertFalse(shouldWarn(NOW, NOW - 1, false, false, 24));
        assertTrue(needsReminder(NOW, NOW - 1, false, 24));
        assertFalse(needsReminder(NOW, NOW + 3 * DAY_MS, false, 24));
    }

    @Test
    void dueDatesAdvance() {
        assertEquals(NOW + 7 * DAY_MS, nextDueAfterLatePay(NOW, NOW + DAY_MS, 7));
        assertEquals(NOW + 30 * DAY_MS + 7 * DAY_MS, nextDueAfterLatePay(NOW, NOW + 30 * DAY_MS, 7));
        assertEquals(NOW + 7 * DAY_MS, rollForward(NOW, NOW + 1, 7));
        assertEquals(NOW + 21 * DAY_MS, rollForward(NOW, NOW + 15 * DAY_MS, 7));
    }

    @Test
    void oldCivJsonLoadsWithoutTaxFields() {
        HonorData d = HonorData.parse("{\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"created\":5}}}");
        assertEquals(0, d.civs.get("c1").taxDue);
        assertFalse(d.civs.get("c1").taxPaid);
        assertEquals(0, d.treasuryEmeralds);
    }
}
