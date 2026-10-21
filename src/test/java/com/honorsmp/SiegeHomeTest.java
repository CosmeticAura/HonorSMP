package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import com.honorsmp.logic.SiegeHome;
import com.honorsmp.logic.SiegeHome.Point;
import java.util.List;

class SiegeHomeTest {
    static final String OW = "minecraft:overworld";

    static Point p(String u, double x, double z) {
        return new Point(u, OW, x, 64, z);
    }

    @Test
    void twoCloseBedsGiveHome() {
        SiegeHome.Home h = SiegeHome.find(List.of(p("a", 0, 0), p("b", 40, 20)), 100);
        assertNotNull(h);
        assertEquals(20, h.x(), 1e-9);
        assertEquals(10, h.z(), 1e-9);
        assertEquals(64, h.y(), 1e-9);
        assertEquals(2, h.size());
    }

    @Test
    void twoFarBedsNoHome() {
        assertNull(SiegeHome.find(List.of(p("a", 0, 0), p("b", 500, 0)), 100));
    }

    @Test
    void exactlyRadiusCounts() {
        assertNotNull(SiegeHome.find(List.of(p("a", 0, 0), p("b", 100, 0)), 100));
        assertNull(SiegeHome.find(List.of(p("a", 0, 0), p("b", 101, 0)), 100));
    }

    @Test
    void pairPlusFarOneUsesPair() {
        SiegeHome.Home h = SiegeHome.find(List.of(p("a", 0, 0), p("b", 10, 0), p("c", 1000, 1000)), 100);
        assertEquals(5, h.x(), 1e-9);
        assertEquals(0, h.z(), 1e-9);
        assertEquals(2, h.size());
    }

    @Test
    void threeSeparatePlacesNoHome() {
        assertNull(SiegeHome.find(List.of(p("a", 0, 0), p("b", 500, 0), p("c", 0, 500)), 100));
    }

    @Test
    void largestGroupWins() {
        SiegeHome.Home h = SiegeHome.find(List.of(
                p("a", 0, 0), p("b", 10, 0), p("c", 0, 20),
                p("d", 1000, 0), p("e", 1010, 0)), 100);
        assertEquals(3, h.size());
        assertEquals(10.0 / 3, h.x(), 1e-9);
        assertEquals(20.0 / 3, h.z(), 1e-9);
    }

    @Test
    void nonOverworldIgnored() {
        assertNull(SiegeHome.find(List.of(p("a", 0, 0), new Point("b", "minecraft:the_nether", 5, 64, 5)), 100));
        SiegeHome.Home h = SiegeHome.find(List.of(p("a", 0, 0), p("b", 10, 0),
                new Point("c", "minecraft:the_nether", 5, 64, 5)), 100);
        assertEquals(2, h.size());
    }

    @Test
    void nullAndDuplicatePlayerCountOnce() {
        List<Point> pts = new java.util.ArrayList<>();
        pts.add(null);
        pts.add(p("a", 0, 0));
        pts.add(p("a", 0, 0));
        assertNull(SiegeHome.find(pts, 100));
        assertNull(SiegeHome.find(null, 100));
        assertNull(SiegeHome.find(List.of(), 100));
        pts.add(p("b", 20, 0));
        assertEquals(2, SiegeHome.find(pts, 100).size());
    }

    @Test
    void heightIgnored() {
        assertNotNull(SiegeHome.find(List.of(new Point("a", OW, 0, 0, 0), new Point("b", OW, 10, 250, 0)), 100));
    }

    @Test
    void tiesAreDeterministic() {
        // two separate pairs of equal size: lowest uuid's group wins, whatever the input order
        List<Point> pts = List.of(p("d", 1000, 0), p("c", 1010, 0), p("b", 0, 0), p("a", 10, 0));
        SiegeHome.Home h1 = SiegeHome.find(pts, 100);
        SiegeHome.Home h2 = SiegeHome.find(List.of(pts.get(3), pts.get(2), pts.get(1), pts.get(0)), 100);
        assertEquals(h1, h2);
        assertEquals(5, h1.x(), 1e-9);
    }
}
