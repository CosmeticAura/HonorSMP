package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.DonateMath;
import com.honorsmp.logic.DonationRules;
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
        assertEquals(40, cfg.donateExtraItems.get("minecraft:golden_apple"));
        assertEquals(300, cfg.donateExtraItems.get("minecraft:enchanted_golden_apple"));
        assertEquals(160, cfg.donateExtraItems.get("minecraft:totem_of_undying"));
        assertEquals(5, cfg.donateExtraItems.get("minecraft:ender_pearl"));
    }

    @Test void breadStack() {
        long p = DonationRules.points("minecraft:bread", 5, true, 64, cfg);
        assertEquals(320, p);
        var r = new HonorData.PlayerRecord();
        assertEquals(16, DonateMath.quote(p, r, d1, cfg, false).honor());
    }

    @Test void pointsPerItem() {
        assertEquals(5, DonationRules.pointsPerItem("minecraft:bread", 5, true, cfg));
        assertEquals(40, DonationRules.pointsPerItem("minecraft:golden_apple", 4, true, cfg));
        assertEquals(300, DonationRules.pointsPerItem("minecraft:enchanted_golden_apple", 4, true, cfg));
        assertEquals(160, DonationRules.pointsPerItem("minecraft:totem_of_undying", -1, true, cfg));
        assertEquals(5, DonationRules.pointsPerItem("minecraft:ender_pearl", -1, true, cfg));
        assertEquals(14, DonationRules.pointsPerItem("minecraft:cake", -1, true, cfg));
        // 15 honor for one enchanted apple, 8 for a totem, 4 pearls = 1 honor
        var r = new HonorData.PlayerRecord();
        assertEquals(15, DonateMath.quote(300, r, d1, cfg, false).honor());
        assertEquals(8, DonateMath.quote(160, r, d1, cfg, false).honor());
        assertEquals(1, DonateMath.quote(DonationRules.points("minecraft:ender_pearl", -1, true, 4, cfg), r, d1, cfg, false).honor());
    }

    @Test void refusedItems() {
        for (String id : cfg.donateZeroFoods) assertFalse(DonationRules.donatable(id, 2, true, cfg), id);
        assertFalse(DonationRules.donatable("minecraft:stone", -1, true, cfg));
        // anything with changed components (name, enchants) is refused, even bread or a totem
        assertFalse(DonationRules.donatable("minecraft:bread", 5, false, cfg));
        assertFalse(DonationRules.donatable("minecraft:totem_of_undying", -1, false, cfg));
        // extra items can be added by config
        cfg.donateExtraItems.put("minecraft:diamond", 1);
        assertTrue(DonationRules.donatable("minecraft:diamond", -1, true, cfg));
    }

    @Test void oldConfigStillLoads() {
        var c = new com.google.gson.Gson().fromJson("{\"donateDailyCap\":10,\"donateZeroFoods\":[\"minecraft:rotten_flesh\"]}", HonorConfig.class);
        assertEquals(10, c.donateDailyCap);
        assertEquals(40, c.donateExtraItems.get("minecraft:golden_apple"));
        assertFalse(DonationRules.donatable("minecraft:rotten_flesh", 4, true, c));
    }

    @Test void carryOver() {
        var r = new HonorData.PlayerRecord();
        var q = DonateMath.quote(19, r, d1, cfg, false);
        assertEquals(0, q.honor());
        assertEquals(19, q.newCarry());
        DonateMath.apply(q, r, d1);
        var q2 = DonateMath.quote(1, r, d1, cfg, false);
        assertEquals(1, q2.honor());
        assertEquals(0, q2.newCarry());
    }

    @Test void dailyCapStillAllowsDonatingButNoHonor() {
        var r = new HonorData.PlayerRecord();
        var q = DonateMath.quote(815, r, d1, cfg, false);
        assertEquals(25, q.honor());
        assertTrue(q.capReached());
        DonateMath.apply(q, r, d1);
        var q2 = DonateMath.quote(100, r, d1, cfg, false);
        assertEquals(0, q2.honor());
        assertTrue(q2.capReached());
        assertEquals(25, DonateMath.remainingToday(r, d2, cfg));
        DonateMath.rollDay(r, d2);
        assertEquals(0, r.donatedHonorToday);
    }

    @Test void judgesEarnNothing() {
        var r = new HonorData.PlayerRecord();
        r.donateCarry = 7;
        var q = DonateMath.quote(500, r, d1, cfg, true);
        assertEquals(0, q.honor());
        assertEquals(7, q.newCarry());
    }

    @Test void quoteIsPure() {
        var r = new HonorData.PlayerRecord();
        r.donateCarry = 5;
        DonateMath.quote(100, r, d1, cfg, false);
        assertEquals(5, r.donateCarry);
        assertEquals("", r.donateDayKey);
    }
}
