package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.honorsmp.logic.LeaderboardEmbed;
import com.honorsmp.logic.LeaderboardEmbed.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 1.6.3: the live Discord leaderboard message. */
class LeaderboardEmbedTest {
    static PlayerRow p(String name, int score) {
        return new PlayerRow("u-" + name, name, score < 0 ? "Outlaw" : "Honorable", score, score < 0);
    }

    static Input in(List<PlayerRow> players, List<CivRow> civs, DonorRow donor) {
        return new Input(players, civs, donor, 5, "2026-10-22T12:00:00Z");
    }

    static JsonObject embed(JsonObject body) {
        return body.getAsJsonArray("embeds").get(0).getAsJsonObject();
    }

    static String field(JsonObject embed, String name) {
        for (var e : embed.getAsJsonArray("fields")) if (e.getAsJsonObject().get("name").getAsString().equals(name)) return e.getAsJsonObject().get("value").getAsString();
        fail("no field " + name);
        return null;
    }

    @Test
    void emptyServerStillBuildsAReadableEmbed() {
        JsonObject e = embed(LeaderboardEmbed.build(in(List.of(), List.of(), null), true));
        assertEquals("Honor leaderboard", e.get("title").getAsString());
        assertEquals(LeaderboardEmbed.COLOR, e.get("color").getAsInt());
        assertEquals("Nobody yet", field(e, "Top 10 by honor"));
        assertEquals("Nobody yet", field(e, "Bottom 5 (most dishonored)"));
        assertEquals("No civilizations yet", field(e, "Top 5 civs"));
        assertEquals("Nobody yet", field(e, "Top donor this week"));
        assertEquals("Updates every 5 minutes", e.getAsJsonObject("footer").get("text").getAsString());
        assertEquals("2026-10-22T12:00:00Z", e.get("timestamp").getAsString());
    }

    @Test
    void fewerThanTenPlayersListsEveryoneInOrder() {
        List<PlayerRow> ps = List.of(p("a", 50), p("b", 300), p("c", -40));
        JsonObject e = embed(LeaderboardEmbed.build(in(ps, List.of(), null), true));
        assertEquals("1. b · Honorable · 300\n2. a · Honorable · 50\n3. c · Outlaw · -40", field(e, "Top 10 by honor"));
        // fewer than 15 players: c may show in both lists (1.6.4)
        assertEquals("1. c · Outlaw · -40", field(e, "Bottom 5 (most dishonored)"));
    }

    @Test
    void liveCaseTwoOutlawsOnASmallServerShowInBottom() {
        List<PlayerRow> ps = List.of(p("Im_not_Polish", -225), p("Mac_Walsky", -60), p("a", 400), p("b", 150), p("c", 0));
        JsonObject e = embed(LeaderboardEmbed.build(in(ps, List.of(), null), true));
        assertEquals("1. Im\\_not\\_Polish · Outlaw · -225\n2. Mac\\_Walsky · Outlaw · -60", field(e, "Bottom 5 (most dishonored)"));
    }

    @Test
    void bottomIsFiveLowestBelowZeroEvenWhenTheyAreInTopOnASmallServer() {
        List<PlayerRow> ps = new ArrayList<>();
        for (int i = 0; i < 7; i++) ps.add(p("bad" + i, -10 - i)); // 7 players, all below 0
        List<PlayerRow> b = LeaderboardEmbed.bottom(ps, LeaderboardEmbed.top(ps));
        assertEquals(5, b.size());
        assertEquals("bad6", b.get(0).name());
        assertEquals("bad2", b.get(4).name());
    }

    @Test
    void zeroIsNotBelowZero() {
        List<PlayerRow> ps = List.of(p("zero", 0), p("pos", 5));
        assertTrue(LeaderboardEmbed.bottom(ps, LeaderboardEmbed.top(ps)).isEmpty());
    }

    @Test
    void from15PlayersOnNobodyIsInBothLists() {
        List<PlayerRow> fourteen = new ArrayList<>();
        for (int i = 0; i < 14; i++) fourteen.add(p("x" + i, -1 - i)); // all below 0, top 10 = least negative
        List<PlayerRow> top14 = LeaderboardEmbed.top(fourteen);
        List<PlayerRow> b14 = LeaderboardEmbed.bottom(fourteen, top14);
        assertEquals("x13", b14.get(0).name()); // 14 players: overlap allowed, lowest 5 are x13..x9
        assertTrue(top14.stream().anyMatch(r -> r.name().equals("x9")));
        assertEquals("x9", b14.get(4).name());

        List<PlayerRow> fifteen = new ArrayList<>(fourteen);
        fifteen.add(p("x14", -15));
        List<PlayerRow> top15 = LeaderboardEmbed.top(fifteen);
        List<PlayerRow> b15 = LeaderboardEmbed.bottom(fifteen, top15);
        assertEquals(5, b15.size());
        for (PlayerRow r : b15) assertFalse(top15.stream().anyMatch(t -> t.uuid().equals(r.uuid())));
        assertEquals("x14", b15.get(0).name()); // the 5 lowest are x14..x10; none of them is in the top 10
    }

    @Test
    void topTenAndBottomFiveAreCut() {
        List<PlayerRow> ps = new ArrayList<>();
        for (int i = 0; i < 12; i++) ps.add(p("good" + i, 1000 - i)); // 12 honorable
        for (int i = 0; i < 8; i++) ps.add(p("bad" + i, -10 - i)); // 8 dishonored
        JsonObject e = embed(LeaderboardEmbed.build(in(ps, List.of(), null), true));
        String top = field(e, "Top 10 by honor");
        assertEquals(10, top.split("\n").length);
        assertTrue(top.startsWith("1. good0 · Honorable · 1000"));
        assertFalse(top.contains("good10"));
        String bottom = field(e, "Bottom 5 (most dishonored)");
        String[] lines = bottom.split("\n");
        assertEquals(5, lines.length);
        assertEquals("1. bad7 · Outlaw · -17", lines[0]); // lowest first
        assertEquals("5. bad3 · Outlaw · -13", lines[4]);
    }

    @Test
    void namesAreEscapedAndCannotPing() {
        JsonObject body = LeaderboardEmbed.build(in(List.of(p("__x__*bob*", 5), p("@everyone", 4)), List.of(new CivRow("[evil](http://x)", 9, 1)), new DonorRow("`d`", 12)), true);
        JsonObject e = embed(body);
        String top = field(e, "Top 10 by honor");
        assertTrue(top.contains("\\_\\_x\\_\\_\\*bob\\*"));
        assertTrue(top.contains("\\@everyone"));
        assertEquals("1. \\[evil\\]\\(http\\://x\\) · 9 honor · 1 member", field(e, "Top 5 civs"));
        assertEquals("\\`d\\` · 12 honor from donations", field(e, "Top donor this week"));
        assertEquals(0, body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
    }

    @Test
    void newlinesInNamesAreFlattened() {
        assertEquals("a b", LeaderboardEmbed.escape("a\nb"));
        assertEquals("", LeaderboardEmbed.escape(null));
    }

    @Test
    void civsAreCutToFiveAndKeepTheirOrder() {
        List<CivRow> civs = new ArrayList<>();
        for (int i = 0; i < 8; i++) civs.add(new CivRow("civ" + i, 100 - i, i + 1));
        String v = field(embed(LeaderboardEmbed.build(in(List.of(), civs, null), true)), "Top 5 civs");
        assertEquals(5, v.split("\n").length);
        assertTrue(v.startsWith("1. civ0 · 100 honor · 1 member\n2. civ1 · 99 honor · 2 members"));
    }

    @Test
    void fieldsStayUnderDiscordLimit() {
        List<PlayerRow> ps = new ArrayList<>();
        for (int i = 0; i < 20; i++) ps.add(p("n".repeat(16) + i, 100 - i));
        JsonObject e = embed(LeaderboardEmbed.build(in(ps, List.of(), null), true));
        for (var f : e.getAsJsonArray("fields")) assertTrue(f.getAsJsonObject().get("value").getAsString().length() <= 1024);
        assertEquals(4, e.getAsJsonArray("fields").size());
    }

    @Test
    void footerUsesTheConfigValue() {
        assertEquals("Updates every 10 minutes", LeaderboardEmbed.footerText(10));
        assertEquals("Updates every minute", LeaderboardEmbed.footerText(1));
        assertEquals("Updates every minute", LeaderboardEmbed.footerText(0));
    }

    @Test
    void fingerprintIgnoresTimeButSeesChanges() {
        List<PlayerRow> ps = List.of(p("a", 5));
        Input one = new Input(ps, List.of(), null, 5, "2026-10-22T12:00:00Z");
        Input later = new Input(ps, List.of(), null, 5, "2026-10-22T12:05:00Z");
        Input changed = new Input(List.of(p("a", 6)), List.of(), null, 5, "2026-10-22T12:00:00Z");
        assertEquals(LeaderboardEmbed.fingerprint(one), LeaderboardEmbed.fingerprint(later));
        assertNotEquals(LeaderboardEmbed.fingerprint(one), LeaderboardEmbed.fingerprint(changed));
        assertFalse(LeaderboardEmbed.build(one, false).toString().contains("timestamp"));
    }

    @Test
    void webhookUrls() {
        String u = "https://discord.com/api/webhooks/1/tok";
        assertEquals(u + "?wait=true", LeaderboardEmbed.postUrl(u));
        assertEquals(u + "?wait=true", LeaderboardEmbed.postUrl(u + "/"));
        assertEquals(u + "?thread_id=9&wait=true", LeaderboardEmbed.postUrl(u + "?thread_id=9"));
        assertEquals(u + "?thread_id=9&wait=true", LeaderboardEmbed.postUrl(u + "?wait=false&thread_id=9"));
        assertEquals(u + "/messages/55", LeaderboardEmbed.patchUrl(u, "55"));
        assertEquals(u + "/messages/55?thread_id=9", LeaderboardEmbed.patchUrl(u + "?thread_id=9", "55"));
    }

    @Test
    void messageIdAndRetryAfter() {
        assertEquals("123", LeaderboardEmbed.messageId("{\"id\":\"123\",\"type\":0}"));
        assertNull(LeaderboardEmbed.messageId("{}"));
        assertNull(LeaderboardEmbed.messageId("not json"));
        assertEquals(1500, LeaderboardEmbed.retryAfterMs("{\"retry_after\":1.5}", null));
        assertEquals(7000, LeaderboardEmbed.retryAfterMs("oops", "7"));
        assertEquals(5000, LeaderboardEmbed.retryAfterMs("", null));
        assertEquals(3_600_000L, LeaderboardEmbed.retryAfterMs("{\"retry_after\":999999}", null));
    }
}
