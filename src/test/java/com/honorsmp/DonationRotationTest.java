package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.DonationRotation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DonationRotationTest {
    static List<String> players(int n) {
        List<String> l = new ArrayList<>();
        for (int i = 1; i <= n; i++) l.add(String.format("p%02d", i));
        return l;
    }

    static long got(Map<String, Map<String, Long>> shares, String who, String item) {
        Map<String, Long> m = shares.get(who);
        return m == null ? 0 : m.getOrDefault(item, 0L);
    }

    @Test void nineItemsToTenPlayersThenThree() {
        Map<String, Integer> rot = new LinkedHashMap<>();
        var first = DonationRotation.share(rot, Map.of("apple", 9L), players(10));
        for (int i = 1; i <= 9; i++) assertEquals(1, got(first, String.format("p%02d", i), "apple"));
        assertEquals(0, got(first, "p10", "apple"));
        assertEquals(9, rot.get("apple"));
        var second = DonationRotation.share(rot, Map.of("apple", 3L), players(10));
        assertEquals(1, got(second, "p10", "apple"));
        assertEquals(1, got(second, "p01", "apple"));
        assertEquals(1, got(second, "p02", "apple"));
        assertEquals(0, got(second, "p03", "apple"));
        assertEquals(2, rot.get("apple"));
    }

    @Test void evenSplitLeavesIndexAlone() {
        Map<String, Integer> rot = new LinkedHashMap<>(Map.of("x", 4));
        var r = DonationRotation.share(rot, Map.of("x", 20L), players(10));
        for (String p : players(10)) assertEquals(2, got(r, p, "x"));
        assertEquals(4, rot.get("x"));
    }

    @Test void rotationIsPerItemType() {
        Map<String, Integer> rot = new LinkedHashMap<>();
        DonationRotation.share(rot, Map.of("bread", 3L), players(4));
        var r = DonationRotation.share(rot, Map.of("pearl", 1L), players(4));
        assertEquals(1, got(r, "p01", "pearl"));
        assertEquals(3, rot.get("bread"));
        assertEquals(1, rot.get("pearl"));
    }

    @Test void oneRecipientGetsEverything() {
        Map<String, Integer> rot = new LinkedHashMap<>();
        var r = DonationRotation.share(rot, Map.of("bread", 64L), List.of("only"));
        assertEquals(64, got(r, "only", "bread"));
        assertEquals(0, rot.get("bread"));
    }

    @Test void zeroRecipientsTouchesNothing() {
        Map<String, Integer> rot = new LinkedHashMap<>(Map.of("bread", 2));
        assertNull(DonationRotation.share(rot, Map.of("bread", 10L), List.of()));
        assertEquals(Map.of("bread", 2), rot);
        assertThrows(IllegalArgumentException.class, () -> DonationRotation.distribute(List.of(), 1, 0));
    }

    @Test void recipientsChangeBetweenDonations() {
        Map<String, Integer> rot = new LinkedHashMap<>();
        DonationRotation.share(rot, Map.of("x", 7L), players(10)); // index 7
        // list shrinks to 5: start = 7 % 5 = 2, remainder 2 -> p03, p04
        var r = DonationRotation.share(rot, Map.of("x", 2L), players(5));
        assertEquals(1, got(r, "p03", "x"));
        assertEquals(1, got(r, "p04", "x"));
        assertEquals(2, r.size());
        assertEquals(4, rot.get("x"));
        // a new player sorts into the middle: still deterministic by sorted position
        List<String> grown = new ArrayList<>(players(5));
        grown.add("p025");
        var g = DonationRotation.distribute(grown, 1, 4);
        // sorted: p01 p02 p025 p03 p04 p05 -> index 4 = p04
        assertEquals(1, g.perRecipient().get("p04"));
    }

    @Test void inputOrderDoesNotMatter() {
        var a = DonationRotation.distribute(List.of("c", "a", "b"), 1, 1);
        var b = DonationRotation.distribute(List.of("b", "c", "a"), 1, 1);
        assertEquals(a.perRecipient(), b.perRecipient());
        assertEquals(1, a.perRecipient().get("b"));
    }

    @Test void indexWrapsAndNegativeIndexIsSafe() {
        var s = DonationRotation.distribute(players(3), 2, 2); // p03, p01
        assertEquals(1, s.perRecipient().get("p03"));
        assertEquals(1, s.perRecipient().get("p01"));
        assertEquals(1, s.nextIndex());
        assertEquals(1, DonationRotation.distribute(players(3), 1, -1).perRecipient().get("p03"));
        assertEquals(1, DonationRotation.distribute(players(3), 1, 300).perRecipient().get("p01"));
    }

    @Test void overTimeEveryoneGetsTheSame() {
        Map<String, Integer> rot = new LinkedHashMap<>();
        Map<String, Long> total = new LinkedHashMap<>();
        long donated = 0;
        for (long n : new long[] {9, 3, 1, 5, 13, 2, 7, 6, 4, 10}) {
            donated += n;
            var r = DonationRotation.share(rot, Map.of("x", n), players(10));
            for (var e : r.entrySet()) total.merge(e.getKey(), e.getValue().get("x"), Long::sum);
        }
        assertEquals(60, donated);
        assertEquals(10, total.size());
        for (long v : total.values()) assertEquals(6, v);
    }

    @Test void nothingIsLostOrCreated() {
        Map<String, Integer> rot = new LinkedHashMap<>();
        long donated = 0, given = 0;
        for (long n : new long[] {5, 13, 1, 64, 100, 3}) {
            donated += n;
            var r = DonationRotation.share(rot, Map.of("x", n), players(7));
            for (var e : r.values()) given += e.get("x");
        }
        assertEquals(donated, given);
    }

    @Test void oldPotMigrationHandsOutEverythingAndIsIdempotent() {
        // the live data: 9 apples, 3 golden apples, 9 golden carrots in the pot, 10 players
        Map<String, Long> pots = new LinkedHashMap<>();
        pots.put("apple", 9L);
        pots.put("golden_apple", 3L);
        pots.put("golden_carrot", 9L);
        Map<String, Integer> rot = new LinkedHashMap<>();
        var r = DonationRotation.share(rot, pots, players(10));
        long given = 0;
        for (var e : r.values()) for (long v : e.values()) given += v;
        assertEquals(21, given);
        assertEquals(1, got(r, "p01", "golden_apple"));
        assertEquals(0, got(r, "p04", "golden_apple"));
        pots.clear(); // what the mod does after a successful hand-out
        assertTrue(DonationRotation.share(rot, pots, players(10)).isEmpty()); // running again gives nothing
        assertEquals(3, rot.get("golden_apple"));
    }

    @Test void noticeText() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("Golden Apple", 3L);
        m.put("Apple", 1L);
        assertEquals("3 Golden Apple, 1 Apple", DonationRotation.describe(m));
        assertEquals("You got 1 Apple from an earlier donation. Use /donation to claim it.",
                DonationRotation.notice("1 Apple", "an earlier donation"));
    }

    @Test void noticesMergeIdenticalAndAreBounded() {
        List<String> l = new ArrayList<>();
        DonationRotation.addNotice(l, "a");
        DonationRotation.addNotice(l, "b");
        DonationRotation.addNotice(l, "a");
        assertEquals(List.of("b", "a"), l);
        for (int i = 0; i < 50; i++) DonationRotation.addNotice(l, "n" + i);
        assertEquals(DonationRotation.MAX_NOTICES, l.size());
        assertEquals("n49", l.get(l.size() - 1));
    }
}
