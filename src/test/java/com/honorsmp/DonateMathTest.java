package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.honorsmp.logic.DonateMath;
import com.honorsmp.logic.DonationRules;
import com.honorsmp.logic.HonorConfig;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DonateMathTest {
    HonorConfig cfg = new HonorConfig();
    String d1 = DonateMath.todayKey(LocalDate.of(2026, 10, 20));
    String d2 = DonateMath.todayKey(LocalDate.of(2026, 10, 21));

    static Map<String, Long> pts(Object... kv) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return m;
    }

    int honor(long points, HonorData.PlayerRecord r) {
        return DonateMath.quote(pts("food", points), r, d1, cfg, false).honor();
    }

    @Test void defaults() {
        assertEquals(0, cfg.onlineBonusHonor);
        assertEquals(3, cfg.onlineBonusHours);
        assertEquals(10, cfg.honorPerHungerPoints);
        assertEquals(100, cfg.donateDailyCap);
        assertEquals(14, cfg.donateCakeHunger);
        assertEquals(5, cfg.donateZeroFoods.size());
        assertEquals(40, cfg.donateExtraItems.get("minecraft:golden_apple"));
        assertEquals(300, cfg.donateExtraItems.get("minecraft:enchanted_golden_apple"));
        assertEquals(160, cfg.donateExtraItems.get("minecraft:totem_of_undying"));
        assertEquals(5, cfg.donateExtraItems.get("minecraft:ender_pearl"));
        assertEquals(Map.of("food", 60, "golden_apple", 20, "enchanted_golden_apple", 30,
                "totem_of_undying", 32, "ender_pearl", 16), cfg.donateItemDailyCaps);
    }

    @Test void breadStack() {
        // a stack of bread: 5 * 64 = 320 points = 32 honor at 10 points per honor
        long p = DonationRules.points("minecraft:bread", 5, true, 64, cfg);
        assertEquals(320, p);
        assertEquals(32, honor(p, new HonorData.PlayerRecord()));
    }

    @Test void pointsPerItem() {
        assertEquals(5, DonationRules.pointsPerItem("minecraft:bread", 5, true, cfg));
        assertEquals(40, DonationRules.pointsPerItem("minecraft:golden_apple", 4, true, cfg));
        assertEquals(300, DonationRules.pointsPerItem("minecraft:enchanted_golden_apple", 4, true, cfg));
        assertEquals(160, DonationRules.pointsPerItem("minecraft:totem_of_undying", -1, true, cfg));
        assertEquals(5, DonationRules.pointsPerItem("minecraft:ender_pearl", -1, true, cfg));
        assertEquals(14, DonationRules.pointsPerItem("minecraft:cake", -1, true, cfg));
        // 4 honor for a golden apple, 30 for an enchanted one, 16 for a totem, 2 pearls = 1 honor
        var r = new HonorData.PlayerRecord();
        assertEquals(4, honor(40, r));
        assertEquals(30, honor(300, r));
        assertEquals(16, honor(160, r));
        assertEquals(1, honor(DonationRules.points("minecraft:ender_pearl", -1, true, 2, cfg), r));
    }

    @Test void refusedItems() {
        for (String id : cfg.donateZeroFoods) assertFalse(DonationRules.donatable(id, 2, true, cfg), id);
        assertFalse(DonationRules.donatable("minecraft:stone", -1, true, cfg));
        assertFalse(DonationRules.donatable("minecraft:bread", 5, false, cfg));
        assertFalse(DonationRules.donatable("minecraft:totem_of_undying", -1, false, cfg));
        cfg.donateExtraItems.put("minecraft:diamond", 1);
        assertTrue(DonationRules.donatable("minecraft:diamond", -1, true, cfg));
    }

    @Test void oldConfigStillLoads() {
        var c = new Gson().fromJson("{\"donateDailyCap\":10,\"donateZeroFoods\":[\"minecraft:rotten_flesh\"]}", HonorConfig.class);
        assertEquals(10, c.donateDailyCap);
        assertEquals(40, c.donateExtraItems.get("minecraft:golden_apple"));
        assertFalse(DonationRules.donatable("minecraft:rotten_flesh", 4, true, c));
    }

    @Test void carryOver() {
        var r = new HonorData.PlayerRecord();
        var q = DonateMath.quote(pts("food", 9), r, d1, cfg, false);
        assertEquals(0, q.honor());
        assertEquals(9, q.newCarry().get("food"));
        DonateMath.apply(q, r, d1);
        var q2 = DonateMath.quote(pts("food", 1), r, d1, cfg, false);
        assertEquals(1, q2.honor());
        assertEquals(0, q2.newCarry().get("food"));
    }

    @Test void oldSingleCarryCountsAsFood() {
        var r = new HonorData.PlayerRecord();
        r.donateCarry = 7;
        var q = DonateMath.quote(pts("food", 3), r, d1, cfg, false);
        assertEquals(1, q.honor());
        DonateMath.apply(q, r, d1);
        assertEquals(0, r.donateCarry);
        assertEquals(0, DonateMath.carryOf(r, "food"));
    }

    @Test void judgesEarnNothing() {
        var r = new HonorData.PlayerRecord();
        r.donateCarryTypes.put("food", 7);
        var q = DonateMath.quote(pts("food", 500), r, d1, cfg, true);
        assertEquals(0, q.honor());
        assertEquals(7, q.newCarry().get("food"));
    }

    @Test void quoteIsPure() {
        var r = new HonorData.PlayerRecord();
        r.donateCarryTypes.put("food", 5);
        DonateMath.quote(pts("food", 100), r, d1, cfg, false);
        assertEquals(5, r.donateCarryTypes.get("food"));
        assertEquals("", r.donateDayKey);
        assertTrue(r.donatedTypeToday.isEmpty());
    }
}
