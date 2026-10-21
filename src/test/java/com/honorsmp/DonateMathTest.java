package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.DonateMath;
import com.honorsmp.logic.HonorConfig;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DonateMathTest {
    HonorConfig cfg = new HonorConfig();
    String d1 = DonateMath.todayKey(LocalDate.of(2026, 10, 20));
    String d2 = DonateMath.todayKey(LocalDate.of(2026, 10, 21));

    @Test void defaults() {
        assertEquals(0, cfg.onlineBonusHonor);
        assertEquals(3, cfg.onlineBonusHours);
        assertEquals(20, cfg.honorPerHungerPoints);
        assertEquals(25, cfg.donateDailyCap);
        assertEquals(14, cfg.donateCakeHunger);
        assertEquals(5, cfg.donateZeroFoods.size());
    }

    @Test void breadStack() {
        int h = DonateMath.hungerValue("minecraft:bread", 5, 64, cfg);
        assertEquals(320, h);
        var r = new HonorData.PlayerRecord();
        assertEquals(16, DonateMath.quote(h, r, d1, cfg).honor());
    }

    @Test void carryOver() {
        var r = new HonorData.PlayerRecord();
        var q = DonateMath.quote(19, r, d1, cfg);
        assertEquals(0, q.honor());
        assertEquals(19, q.newCarry());
        DonateMath.apply(q, r, d1);
        var q2 = DonateMath.quote(1, r, d1, cfg);
        assertEquals(1, q2.honor());
        assertEquals(0, q2.newCarry());
    }

    @Test void dailyCapAndReset() {
        var r = new HonorData.PlayerRecord();
        var q = DonateMath.quote(815, r, d1, cfg);
        assertEquals(25, q.honor());
        assertTrue(q.capReached());
        DonateMath.apply(q, r, d1);
        var q2 = DonateMath.quote(100, r, d1, cfg);
        assertEquals(0, q2.honor());
        assertTrue(q2.capReached());
        assertTrue(DonateMath.chestFull(0, r, d1, cfg));
        assertEquals(25, DonateMath.remainingToday(r, d2, cfg));
        assertFalse(DonateMath.chestFull(0, r, d2, cfg));
        DonateMath.rollDay(r, d2);
        assertEquals(0, r.donatedHonorToday);
        assertEquals(15, r.donateCarry);
    }

    @Test void quoteIsPure() {
        var r = new HonorData.PlayerRecord();
        r.donateCarry = 5;
        DonateMath.quote(100, r, d1, cfg);
        assertEquals(5, r.donateCarry);
        assertEquals("", r.donateDayKey);
    }

    @Test void foodKinds() {
        assertEquals(0, DonateMath.hungerValue("minecraft:rotten_flesh", 4, 10, cfg));
        assertTrue(DonateMath.donatable("minecraft:rotten_flesh", 4, cfg));
        assertFalse(DonateMath.donatable("minecraft:stone", -1, cfg));
        assertEquals(-1, DonateMath.hungerValue("minecraft:stone", -1, 5, cfg));
        assertTrue(DonateMath.donatable("minecraft:cake", -1, cfg));
        assertEquals(14, DonateMath.hungerValue("minecraft:cake", -1, 1, cfg));
    }
}
