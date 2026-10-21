package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.honorsmp.logic.AutoRules;
import com.honorsmp.logic.BossFights;
import com.honorsmp.logic.HonorConfig;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AutoRulesTest {
    HonorConfig cfg = new HonorConfig();

    @Test void configDefaults() {
        assertEquals(10, cfg.zombieCureHonor);
        assertEquals(30, cfg.zombieCureDailyCap);
        assertEquals(64, cfg.harvestPerCrops);
        assertEquals(1, cfg.harvestHonor);
        assertEquals(10, cfg.harvestDailyCap);
        assertEquals(5, cfg.breedPerBreeds);
        assertEquals(1, cfg.breedHonor);
        assertEquals(5, cfg.breedDailyCap);
        assertEquals(10, cfg.tradePerTrades);
        assertEquals(1, cfg.tradeHonor);
        assertEquals(5, cfg.tradeDailyCap);
        assertEquals(15, cfg.raidWinHonor);
        assertEquals(15, cfg.bossKillHonor);
        assertEquals(15, cfg.villagerKillPenalty);
        assertEquals(20, cfg.petKillPenalty);
    }

    @Test void oldConfigAndDataStillLoad() {
        HonorConfig c = new Gson().fromJson("{\"minScore\":-500,\"donateDailyCap\":25}", HonorConfig.class);
        assertEquals(-500, c.minScore);
        assertEquals(30, c.zombieCureDailyCap);
        assertEquals(20, c.petKillPenalty);
        HonorData d = HonorData.parse("{\"players\":{\"a\":{\"uuid\":\"a\",\"name\":\"A\",\"score\":50,\"donateCarry\":3}}}");
        var r = d.players.get("a");
        assertEquals(50, r.score);
        assertEquals(3, r.donateCarry);
        assertEquals(0, r.harvestCount);
        assertEquals("", r.autoDayKey);
        assertNotNull(r.bossesToday);
    }

    @Test void cureCapsAtThirtyPerDay() {
        int used = 0, total = 0;
        for (int i = 0; i < 5; i++) {
            int g = AutoRules.capped(cfg.zombieCureHonor, used, cfg.zombieCureDailyCap);
            used += g;
            total += g;
        }
        assertEquals(30, total);
        assertEquals(0, AutoRules.capped(10, 30, 30));
        assertEquals(5, AutoRules.capped(10, 25, 30)); // partial when only part fits
    }

    @Test void harvestOnePer64CappedAtTen() {
        int count = 0, used = 0, total = 0;
        for (int i = 1; i <= 64 * 12; i++) {
            var s = AutoRules.counter(count, cfg.harvestPerCrops, cfg.harvestHonor, used, cfg.harvestDailyCap);
            count = s.count();
            used += s.honor();
            total += s.honor();
            if (i == 63) assertEquals(0, total);
            if (i == 64) assertEquals(1, total);
        }
        assertEquals(10, total);
        assertEquals(0, count); // counter restarted even when capped
    }

    @Test void breedAndTradeCounters() {
        int count = 0, used = 0, total = 0;
        for (int i = 0; i < 40; i++) {
            var s = AutoRules.counter(count, cfg.breedPerBreeds, cfg.breedHonor, used, cfg.breedDailyCap);
            count = s.count(); used += s.honor(); total += s.honor();
        }
        assertEquals(5, total);
        count = 0; used = 0; total = 0;
        for (int i = 0; i < 25; i++) {
            var s = AutoRules.counter(count, cfg.tradePerTrades, cfg.tradeHonor, used, cfg.tradeDailyCap);
            count = s.count(); used += s.honor(); total += s.honor();
        }
        assertEquals(2, total);
        assertEquals(5, count);
    }

    @Test void counterCarriesOverDays() {
        var r = new HonorData.PlayerRecord();
        r.autoDayKey = "2026-10-20";
        r.harvestCount = 40;
        r.harvestHonorToday = 10;
        r.cureHonorToday = 30;
        AutoRules.rollDay(r, "2026-10-21");
        assertEquals(40, r.harvestCount);
        assertEquals(0, r.harvestHonorToday);
        assertEquals(0, r.cureHonorToday);
        r.harvestHonorToday = 4;
        AutoRules.rollDay(r, "2026-10-21");
        assertEquals(4, r.harvestHonorToday); // same day: untouched
    }

    @Test void matureCropsOnly() {
        assertTrue(AutoRules.matureCrop("minecraft:wheat", 7));
        assertFalse(AutoRules.matureCrop("minecraft:wheat", 6));
        assertTrue(AutoRules.matureCrop("minecraft:carrots", 7));
        assertTrue(AutoRules.matureCrop("minecraft:potatoes", 7));
        assertFalse(AutoRules.matureCrop("minecraft:potatoes", 3));
        assertTrue(AutoRules.matureCrop("minecraft:beetroots", 3));
        assertFalse(AutoRules.matureCrop("minecraft:beetroots", 2));
        assertTrue(AutoRules.matureCrop("minecraft:nether_wart", 3));
        assertFalse(AutoRules.matureCrop("minecraft:nether_wart", 2));
        assertFalse(AutoRules.matureCrop("minecraft:torchflower_crop", 7));
        assertFalse(AutoRules.matureCrop("minecraft:wheat", -1));
    }

    @Test void bossOncePerTypePerDay() {
        var r = new HonorData.PlayerRecord();
        AutoRules.rollDay(r, "d1");
        assertTrue(AutoRules.claimBoss(r, "wither"));
        assertFalse(AutoRules.claimBoss(r, "wither"));
        assertTrue(AutoRules.claimBoss(r, "warden"));
        assertTrue(AutoRules.claimBoss(r, "ender_dragon"));
        AutoRules.rollDay(r, "d2");
        assertTrue(AutoRules.claimBoss(r, "wither"));
    }

    @Test void bossFightTracksEveryDamager() {
        var f = new BossFights();
        UUID boss = UUID.randomUUID(), a = UUID.randomUUID(), b = UUID.randomUUID();
        f.record(boss, a);
        f.record(boss, a);
        f.record(boss, b);
        assertEquals(Set.of(a, b), f.end(boss));
        assertTrue(f.end(boss).isEmpty()); // consumed
        assertTrue(f.end(UUID.randomUUID()).isEmpty());
    }

    @Test void petOwnerCheck() {
        UUID me = UUID.randomUUID(), other = UUID.randomUUID();
        assertTrue(AutoRules.petPenalty(me, other));
        assertFalse(AutoRules.petPenalty(me, me)); // own pet
        assertFalse(AutoRules.petPenalty(me, null)); // wild / unowned
    }

    @Test void villagerKillerMustBePlayer() {
        assertTrue(AutoRules.villagerPenalty(true, true));
        assertFalse(AutoRules.villagerPenalty(true, false));
        assertFalse(AutoRules.villagerPenalty(false, true)); // e.g. zombie villager
    }

    @Test void judgesExemptThroughChange() {
        assertFalse(com.honorsmp.logic.JudgeRules.autoChangeAllowed(true, "auto"));
        assertTrue(com.honorsmp.logic.JudgeRules.autoChangeAllowed(false, "auto"));
    }
}
