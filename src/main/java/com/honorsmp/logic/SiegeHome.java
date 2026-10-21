package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds a civ's home from its members' bed respawn points.
 * Group rule: for each candidate point, the group is every point within radius of it (horizontal x/z distance,
 * so bed height does not matter). The largest group wins, ties go to the lowest uuid; the home is the average of
 * the group's points. A home needs at least 2 distinct players. Only the overworld counts.
 */
public final class SiegeHome {
    public static final String OVERWORLD = "minecraft:overworld";

    private SiegeHome() {
    }

    public record Point(String uuid, String dim, double x, double y, double z) {
    }

    public record Home(double x, double y, double z, int size) {
    }

    /** @return the home, or null if no two players have overworld beds within radius of each other. */
    public static Home find(List<Point> points, double radius) {
        if (points == null) {
            return null;
        }
        // one overworld point per player (the lowest-sorted if a caller passes duplicates)
        Map<String, Point> byPlayer = new HashMap<>();
        for (Point p : points) {
            if (p == null || p.uuid() == null || !OVERWORLD.equals(p.dim())) {
                continue;
            }
            byPlayer.merge(p.uuid(), p, (a, b) -> key(a).compareTo(key(b)) <= 0 ? a : b);
        }
        List<Point> pts = new ArrayList<>(byPlayer.values());
        pts.sort((a, b) -> a.uuid().compareTo(b.uuid()));
        List<Point> best = null;
        for (Point c : pts) {
            List<Point> group = new ArrayList<>();
            for (Point q : pts) {
                if (horizontal(c, q) <= radius) {
                    group.add(q);
                }
            }
            if (best == null || group.size() > best.size()) { // strict: earlier (lower uuid) wins ties
                best = group;
            }
        }
        if (best == null || best.size() < 2) {
            return null;
        }
        double sx = 0, sy = 0, sz = 0;
        for (Point q : best) {
            sx += q.x();
            sy += q.y();
            sz += q.z();
        }
        int n = best.size();
        return new Home(sx / n, sy / n, sz / n, n);
    }

    private static String key(Point p) {
        return p.x() + "," + p.y() + "," + p.z();
    }

    public static double horizontal(Point a, Point b) {
        return Math.hypot(a.x() - b.x(), a.z() - b.z());
    }

    /** Horizontal distance from a position to the home. */
    public static double horizontalTo(Home h, double x, double z) {
        return Math.hypot(h.x() - x, h.z() - z);
    }
}
