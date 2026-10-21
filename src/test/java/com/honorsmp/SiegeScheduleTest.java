package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.honorsmp.logic.SiegeSchedule;
import java.util.Random;

class SiegeScheduleTest {
    static final long DAY = 86_400_000L;

    @Test
    void rollWithinRange() {
        Random r = new Random(42);
        for (int i = 0; i < 2000; i++) {
            long n = SiegeSchedule.rollNext(1000, r, 2, 4);
            assertTrue(n >= 1000 + 2 * DAY, "never before min");
            assertTrue(n <= 1000 + 4 * DAY);
        }
    }

    @Test
    void rollSpreadsAcrossRange() {
        Random r = new Random(1);
        long lo = Long.MAX_VALUE, hi = 0;
        for (int i = 0; i < 2000; i++) {
            long n = SiegeSchedule.rollNext(0, r, 2, 4);
            lo = Math.min(lo, n);
            hi = Math.max(hi, n);
        }
        assertTrue(lo < 2.2 * DAY);
        assertTrue(hi > 3.8 * DAY);
    }

    @Test
    void equalOrBackwardsRange() {
        assertEquals(3 * DAY, SiegeSchedule.rollNext(0, new Random(1), 3, 3));
        assertEquals(3 * DAY, SiegeSchedule.rollNext(0, new Random(1), 3, 1));
    }

    @Test
    void dueAndNotDue() {
        assertFalse(SiegeSchedule.due(999, 1000));
        assertTrue(SiegeSchedule.due(1000, 1000));
        assertTrue(SiegeSchedule.due(5000, 1000));
    }

    @Test
    void zeroNextRollsFromNowAndIsNotDue() {
        assertFalse(SiegeSchedule.due(123456789L, 0));
        long now = 5_000_000_000L;
        long n = SiegeSchedule.ensureScheduled(now, 0, new Random(3), 2, 4);
        assertTrue(n >= now + 2 * DAY && n <= now + 4 * DAY);
        assertEquals(777, SiegeSchedule.ensureScheduled(now, 777, new Random(3), 2, 4));
        assertFalse(SiegeSchedule.due(now, n));
    }

    @Test
    void waitsUntilMembersArrive() {
        long next = 1000;
        assertFalse(SiegeSchedule.shouldStart(500, next, true, 5, 2), "not yet due");
        assertFalse(SiegeSchedule.shouldStart(2000, next, true, 1, 2), "due but only 1 near");
        assertFalse(SiegeSchedule.shouldStart(3000, next, true, 0, 2), "still waiting");
        assertFalse(SiegeSchedule.shouldStart(4000, next, false, 5, 2), "no home");
        assertTrue(SiegeSchedule.shouldStart(5000, next, true, 2, 2), "members arrived");
        assertFalse(SiegeSchedule.shouldStart(5000, 0, true, 2, 2), "unscheduled");
    }

    @Test
    void warningWindow() {
        long next = 100_000;
        assertFalse(SiegeSchedule.inWarning(39_999, next, 60));
        assertTrue(SiegeSchedule.inWarning(40_000, next, 60));
        assertTrue(SiegeSchedule.inWarning(99_999, next, 60));
        assertFalse(SiegeSchedule.inWarning(100_000, next, 60));
        assertFalse(SiegeSchedule.inWarning(50_000, 0, 60));
    }

    @Test
    void rerollFromEnd() {
        long end = 10 * DAY;
        long n = SiegeSchedule.rollAfterEnd(end, new Random(9), 2, 4);
        assertTrue(n >= end + 2 * DAY && n <= end + 4 * DAY);
    }
}
