package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class JudgesTest {
    private final HonorConfig c = new HonorConfig();
    private static final long NOW = 1_000_000L;
    private static final long WIN = 30_000L;

    // ---- judge exemption ----

    @Test
    void judgesSkipAutomaticChangesOnly() {
        assertFalse(JudgeRules.autoChangeAllowed(true, "auto"));
        assertTrue(JudgeRules.autoChangeAllowed(false, "auto"));
        assertTrue(JudgeRules.autoChangeAllowed(true, "admin:Steve"));
        assertTrue(JudgeRules.autoChangeAllowed(true, "SomeJudge"));
    }

    @Test
    void judgeTierIsTopHonorableRank() {
        assertTrue(Tier.JUDGE.isHonorable());
        assertTrue(Tier.JUDGE.isNobleOrHigher());
        assertFalse(Tier.JUDGE.isDishonored());
        assertEquals(2, Tier.JUDGE.perkIndex());
        assertEquals("Judge", Tier.JUDGE.display);
        assertEquals(Tier.PARAGON, Tier.of(1000, c)); // Tier.of never yields Judge
    }

    // ---- judge kill rules ----

    private KillContext ctx(boolean hitFirst, boolean unclear, boolean newbie) {
        return new KillContext(Tier.JUDGE, false, hitFirst, false, false, false, newbie, unclear);
    }

    @Test
    void dishonoredKillerOfJudgeGetsLootAndAnnouncement() {
        var r = JudgeKillRules.evaluate(ctx(false, false, false), true, true, c);
        assertTrue(r.judgeLoot());
        assertTrue(r.announce());
        assertEquals(c.killNoFirstHit, r.outcome().delta());
        assertFalse(r.review()); // killer clearly started it, decided automatically
    }

    @Test
    void honorableKillerOfJudgeIsNormalUnprovokedKillNoLoot() {
        var r = JudgeKillRules.evaluate(ctx(false, false, false), false, true, c);
        assertFalse(r.judgeLoot());
        assertFalse(r.announce());
        assertEquals(c.killNoFirstHit, r.outcome().delta());
    }

    @Test
    void selfDefenseAgainstJudgeCostsNothing() {
        var r = JudgeKillRules.evaluate(ctx(true, false, false), false, true, c);
        assertEquals(0, r.outcome().delta());
        assertFalse(r.judgeLoot() && false);
    }

    @Test
    void noCaseWhenNoOtherJudgeCanRule() {
        var r = JudgeKillRules.evaluate(ctx(false, true, false), false, false, c);
        assertFalse(r.review());
        assertFalse(r.outcome().review());
        var r2 = JudgeKillRules.evaluate(ctx(false, true, false), false, true, c);
        assertTrue(r2.review());
    }

    @Test
    void defaultJudgeLootPool() {
        var t = HonorConfig.defaultJudgeLoot();
        assertTrue(t.rolls > 0);
        assertEquals(5, t.entries.size());
    }

    // ---- appeals ----

    private static final List<AppealRules.LogView> LOGS = List.of(
            new AppealRules.LogView(1, "a", -30),
            new AppealRules.LogView(2, "b", -5),
            new AppealRules.LogView(3, "a", 0),
            new AppealRules.LogView(4, "a", 25));

    @Test
    void appealDefaultsToMostRecentRealChange() {
        var p = AppealRules.pick("a", null, LOGS, List.of());
        assertEquals(4L, p.logId());
        assertNull(p.error());
    }

    @Test
    void appealById() {
        assertEquals(1L, AppealRules.pick("a", 1L, LOGS, List.of()).logId());
        assertNotNull(AppealRules.pick("a", 2L, LOGS, List.of()).error()); // someone else's
        assertNotNull(AppealRules.pick("a", 3L, LOGS, List.of()).error()); // zero change
    }

    @Test
    void onlyOneOpenAppealPerPlayer() {
        var open = List.of(new AppealRules.AppealView("a", 1, true));
        assertNotNull(AppealRules.pick("a", null, LOGS, open).error());
        assertNull(AppealRules.pick("b", null, LOGS, open).error());
        var closed = List.of(new AppealRules.AppealView("a", 4, false));
        assertNull(AppealRules.pick("a", 1L, LOGS, closed).error());
        // the same change cannot be appealed twice
        assertNotNull(AppealRules.pick("a", 4L, LOGS, closed).error());
    }

    @Test
    void appealParsing() {
        var p = AppealRules.parse("4 it was a trap", id -> id == 4);
        assertEquals(4L, p.logId());
        assertEquals("it was a trap", p.reason());
        var q = AppealRules.parse("7 days of play lost", id -> false);
        assertNull(q.logId());
        assertEquals("7 days of play lost", q.reason());
    }

    // ---- auto decision ----

    private DamageEvent hit(long ago, String from, String to) {
        return new DamageEvent(NOW - ago, from, to, 4f);
    }

    @Test
    void killerHitFirstIsDecidedAutomatically() {
        var h = List.of(hit(5000, "k", "v"), hit(3000, "v", "k"), hit(1000, "k", "v"));
        assertEquals(FightAnalysis.Who.KILLER, FightAnalysis.firstAggressor(h, "k", "v", NOW, WIN, false));
    }

    @Test
    void victimHitFirstIsSelfDefense() {
        var h = List.of(hit(5000, "v", "k"), hit(1000, "k", "v"));
        assertEquals(FightAnalysis.Who.VICTIM, FightAnalysis.firstAggressor(h, "k", "v", NOW, WIN, false));
    }

    @Test
    void noRecordedHitsIsUnclear() {
        assertEquals(FightAnalysis.Who.UNCLEAR, FightAnalysis.firstAggressor(List.of(), "k", "v", NOW, WIN, false));
    }

    @Test
    void sameCivIsUnclear() {
        var h = List.of(hit(5000, "k", "v"));
        assertEquals(FightAnalysis.Who.UNCLEAR, FightAnalysis.firstAggressor(h, "k", "v", NOW, WIN, true));
    }

    @Test
    void olderVictimHitOutsideWindowIsUnclear() {
        var h = List.of(hit(200_000, "v", "k"), hit(5000, "k", "v"));
        assertEquals(FightAnalysis.Who.UNCLEAR, FightAnalysis.firstAggressor(h, "k", "v", NOW, WIN, false));
    }

    @Test
    void unclearUnprovokedKillGoesToJudgeButClearOneDoesNot() {
        var clear = new KillContext(Tier.HONORABLE, false, false, false, false, false, false, false);
        var o = KillRules.evaluate(clear, c);
        assertFalse(o.review());
        assertEquals(c.killNoFirstHit, o.delta());
        var unclear = new KillContext(Tier.HONORABLE, false, false, false, false, false, false, true);
        assertTrue(KillRules.evaluate(unclear, c).review());
    }

    // ---- book ----

    @Test
    void bookPagesRespectLimits() {
        var lines = new java.util.ArrayList<String>();
        for (int i = 0; i < 400; i++) lines.add("line number " + i + " with some filler words to wrap around");
        var pages = BookPager.paginate(lines, BookPager.PAGE_CHARS);
        assertTrue(pages.size() <= BookPager.MAX_PAGES);
        for (String p : pages) assertTrue(p.length() <= BookPager.PAGE_CHARS);
        assertEquals(1, BookPager.paginate(List.of("x"), BookPager.PAGE_CHARS).size());
        assertEquals(32, BookPager.title("x".repeat(80)).length());
    }
}
