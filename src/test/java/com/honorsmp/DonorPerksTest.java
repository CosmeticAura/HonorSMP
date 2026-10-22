package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.honorsmp.logic.*;
import com.honorsmp.logic.DonorPerks.DonorTier;
import com.honorsmp.logic.DonorPerks.LogLine;
import com.honorsmp.logic.WeeklyDonor.Award;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DonorPerksTest {
    private final HonorConfig c = new HonorConfig();
    private static final ZoneId Z = ZoneId.of("America/New_York");

    @Test
    void tierThresholds() {
        assertEquals(DonorTier.NONE, DonorPerks.tierOf(199, c));
        assertEquals(DonorTier.SUPPORTER, DonorPerks.tierOf(200, c));
        assertEquals(DonorTier.SUPPORTER, DonorPerks.tierOf(749, c));
        assertEquals(DonorTier.PATRON, DonorPerks.tierOf(750, c));
        assertEquals(DonorTier.PATRON, DonorPerks.tierOf(1999, c));
        assertEquals(DonorTier.BENEFACTOR, DonorPerks.tierOf(2000, c));
        assertEquals(DonorTier.NONE, DonorPerks.effectiveTier(5000, true, c)); // judges: nothing
    }

    @Test
    void honorToNext() {
        assertEquals(200, DonorPerks.honorToNext(0, c));
        assertEquals(1, DonorPerks.honorToNext(199, c));
        assertEquals(550, DonorPerks.honorToNext(200, c));
        assertEquals(1, DonorPerks.honorToNext(1999, c));
        assertEquals(-1, DonorPerks.honorToNext(2000, c));
    }

    @Test
    void cumulativePerksAndTags() {
        assertEquals(0, DonorPerks.hungerPercent(DonorTier.NONE, c));
        assertEquals(20, DonorPerks.hungerPercent(DonorTier.SUPPORTER, c));
        assertEquals(20, DonorPerks.hungerPercent(DonorTier.PATRON, c));
        assertEquals(20, DonorPerks.hungerPercent(DonorTier.BENEFACTOR, c));
        assertEquals(0, DonorPerks.luck(DonorTier.SUPPORTER, c));
        assertEquals(1.0, DonorPerks.luck(DonorTier.PATRON, c));
        assertEquals(1.0, DonorPerks.luck(DonorTier.BENEFACTOR, c));
        assertEquals(0, DonorPerks.respawnRegenSeconds(DonorTier.PATRON, c));
        assertEquals(30, DonorPerks.respawnRegenSeconds(DonorTier.BENEFACTOR, c));
        assertNull(DonorPerks.tagName(DonorTier.NONE));
        assertEquals("Supporter", DonorPerks.tagName(DonorTier.SUPPORTER));
        assertEquals("Supporter", DonorPerks.tagName(DonorTier.PATRON));
        assertEquals("Benefactor", DonorPerks.tagName(DonorTier.BENEFACTOR));
    }

    @Test
    void hungerStacksAndCaps() {
        assertEquals(35, DonorPerks.totalHungerPercent(15, DonorTier.SUPPORTER, c));
        assertEquals(45, DonorPerks.totalHungerPercent(25, DonorTier.SUPPORTER, c));
        assertEquals(50, DonorPerks.totalHungerPercent(35, DonorTier.PATRON, c)); // 55 capped
        assertEquals(20, DonorPerks.totalHungerPercent(0, DonorTier.BENEFACTOR, c));
        assertEquals(35, DonorPerks.totalHungerPercent(35, DonorTier.NONE, c));
        c.donorHungerCapPercent = 30;
        assertEquals(35, DonorPerks.totalHungerPercent(35, DonorTier.SUPPORTER, c)); // never below the rank perk
    }

    @Test
    void backfillOnlyDonationEntries() {
        List<LogLine> log = List.of(
                new LogLine("a", "Donated items", 30, 1),
                new LogLine("a", "Donated food", 10, 2),
                new LogLine("a", "Donated items", -5, 3), // negative ignored
                new LogLine("a", "Zombie villager cured", 10, 4),
                new LogLine("b", "Donated items", 0, 5),
                new LogLine("b", "Donated items", 7, 6),
                new LogLine("b", "Score set to 50", 50, 7));
        Map<String, Long> m = DonorPerks.backfill(log);
        assertEquals(40L, m.get("a"));
        assertEquals(7L, m.get("b"));
        assertEquals(2, m.size());
        assertEquals(2, DonorPerks.donationsSince(log, 2).size()); // a/food(2) and b/7(6)
    }

    @Test
    void oldConfigAndDataLoadWithDefaults() {
        HonorConfig old = new Gson().fromJson("{\"minScore\":-1000,\"maxScore\":1000,\"hungerSlowPercent\":[15,25,35]}", HonorConfig.class);
        assertEquals(200, old.donorSupporterMin);
        assertEquals(750, old.donorPatronMin);
        assertEquals(2000, old.donorBenefactorMin);
        assertEquals(20, old.donorHungerSlowPercent);
        assertEquals(50, old.donorHungerCapPercent);
        assertEquals(30, old.donorRespawnRegenSeconds);
        assertEquals(7, old.topDonorHeroDays);
        assertTrue(old.topDonorEnabled);
        HonorData d = HonorData.parse("{\"version\":2,\"players\":{\"u\":{\"uuid\":\"u\",\"name\":\"X\",\"score\":50}}}");
        assertEquals(0, d.players.get("u").donatedHonorTotal);
        assertFalse(d.donorBackfilled);
        assertEquals(0, d.lastWeeklyDonorRun);
        assertNotNull(d.players.get("u").donationAwards);
    }

    // ---- weekly top donor

    private static long at(int y, int mo, int d, int h, int mi) {
        return ZonedDateTime.of(y, mo, d, h, mi, 0, 0, Z).toInstant().toEpochMilli();
    }

    @Test
    void mondayBoundaries() {
        long mon = at(2026, 10, 19, 0, 0); // a Monday
        assertEquals(mon, WeeklyDonor.mondayStart(at(2026, 10, 19, 0, 0), Z));
        assertEquals(mon, WeeklyDonor.mondayStart(at(2026, 10, 21, 15, 30), Z));
        assertEquals(mon, WeeklyDonor.mondayStart(at(2026, 10, 25, 23, 59), Z));
        assertEquals(at(2026, 10, 26, 0, 0), WeeklyDonor.mondayStart(at(2026, 10, 26, 0, 0), Z));
        assertEquals(at(2026, 10, 12, 0, 0), WeeklyDonor.previousWeek(mon, Z));
        assertEquals(at(2026, 10, 26, 0, 0), WeeklyDonor.nextWeek(mon, Z));
        // daylight saving ends 2026-11-01: the week still starts at midnight
        assertEquals(at(2026, 11, 2, 0, 0), WeeklyDonor.mondayStart(at(2026, 11, 3, 12, 0), Z));
        assertEquals(at(2026, 10, 26, 0, 0), WeeklyDonor.previousWeek(at(2026, 11, 2, 0, 0), Z));
    }

    @Test
    void dueOnlyOncePerMonday() {
        long mon = at(2026, 10, 19, 0, 0);
        assertTrue(WeeklyDonor.due(0, at(2026, 10, 19, 0, 0), Z));
        assertTrue(WeeklyDonor.due(WeeklyDonor.previousWeek(mon, Z), at(2026, 10, 21, 9, 0), Z)); // server was down Monday
        assertFalse(WeeklyDonor.due(mon, at(2026, 10, 21, 9, 0), Z)); // already ran
        assertTrue(WeeklyDonor.due(mon, at(2026, 10, 26, 0, 0), Z));
        // down for two weeks: still one run, for the most recent Monday only
        assertEquals(at(2026, 11, 2, 0, 0), WeeklyDonor.mondayStart(at(2026, 11, 4, 0, 0), Z));
    }

    @Test
    void selectsHighestInWindowOnly() {
        long mon = at(2026, 10, 19, 0, 0), from = WeeklyDonor.previousWeek(mon, Z);
        List<Award> a = List.of(
                new Award("a", from - 1, 500),       // before the window
                new Award("a", from, 20),            // window start is inclusive
                new Award("b", mon - 1, 25),
                new Award("b", mon, 500),            // Monday 00:00 itself belongs to the next week
                new Award("c", at(2026, 10, 15, 12, 0), 10));
        var w = WeeklyDonor.select(a, id -> false, from, mon);
        assertEquals("b", w.player());
        assertEquals(25, w.total());
    }

    @Test
    void sumsAwardsAndTieGoesToFirstToReach() {
        long mon = at(2026, 10, 19, 0, 0), from = WeeklyDonor.previousWeek(mon, Z);
        List<Award> a = List.of(
                new Award("late", from + 1000, 30),
                new Award("early", from + 500, 50),
                new Award("late", from + 5000, 20),   // late reaches 50 at +5000
                new Award("early", from + 900, 0));   // zero ignored
        var w = WeeklyDonor.select(a, id -> false, from, mon);
        assertEquals("early", w.player());
        assertEquals(50, w.total());
        assertEquals(from + 500, w.reachedAt());
        // more donations beat the tie
        var w2 = WeeklyDonor.select(List.of(new Award("x", from + 1, 10), new Award("y", from + 2, 11)), id -> false, from, mon);
        assertEquals("y", w2.player());
    }

    @Test
    void noDonationsNoWinnerAndJudgesExcluded() {
        long mon = at(2026, 10, 19, 0, 0), from = WeeklyDonor.previousWeek(mon, Z);
        assertNull(WeeklyDonor.select(List.of(), id -> false, from, mon));
        assertNull(WeeklyDonor.select(List.of(new Award("a", from - 5, 100)), id -> false, from, mon));
        List<Award> a = List.of(new Award("judge", from + 1, 900), new Award("p", from + 2, 5));
        assertEquals("p", WeeklyDonor.select(a, id -> id.equals("judge"), from, mon).player());
        assertNull(WeeklyDonor.select(List.of(new Award("judge", from + 1, 900)), id -> id.equals("judge"), from, mon));
    }
}
