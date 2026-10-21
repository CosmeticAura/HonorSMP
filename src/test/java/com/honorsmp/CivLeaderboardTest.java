package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.CivLeaderboard;
import com.honorsmp.logic.CivLeaderboard.CivIn;
import com.honorsmp.logic.CivLeaderboard.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CivLeaderboardTest {
    final Map<String, Integer> honor = Map.of("a", 100, "b", 50, "c", -30, "d", 500, "e", 70, "f", 70);

    List<Row> run(List<CivIn> civs, Set<String> judges, int limit) {
        return CivLeaderboard.top(civs, u -> honor.getOrDefault(u, 0), judges::contains, limit);
    }

    @Test
    void sortsByTotalDescending() {
        var r = run(List.of(new CivIn("Low", List.of("c")), new CivIn("High", List.of("a", "b"))), Set.of(), 10);
        assertEquals("High", r.get(0).name());
        assertEquals(150, r.get(0).totalHonor());
        assertEquals(2, r.get(0).memberCount());
        assertEquals(-30, r.get(1).totalHonor());
    }

    @Test
    void judgesDoNotCountButStillCountAsMembers() {
        var r = run(List.of(new CivIn("X", List.of("a", "d"))), Set.of("d"), 10);
        assertEquals(100, r.get(0).totalHonor());
        assertEquals(2, r.get(0).memberCount());
    }

    @Test
    void tiesBrokenByMembersThenName() {
        var r = run(List.of(
                new CivIn("Zed", List.of("e")),
                new CivIn("Bob", List.of("f")),
                new CivIn("Big", List.of("a", "c", "e", "f", "x")), // 100-30+70+70 = 210? not a tie
                new CivIn("Duo", List.of("a", "c", "e")),            // 140
                new CivIn("Pair", List.of("e", "x"))),               // 70, 2 members
                Set.of(), 10);
        assertEquals(List.of("Big", "Duo", "Pair", "Bob", "Zed"), r.stream().map(Row::name).toList());
    }

    @Test
    void limitTen() {
        List<CivIn> civs = new ArrayList<>();
        for (int i = 0; i < 15; i++) civs.add(new CivIn("C" + i, List.of("a")));
        assertEquals(10, run(civs, Set.of(), 10).size());
    }

    @Test
    void empty() {
        assertTrue(run(List.of(), Set.of(), 10).isEmpty());
        assertEquals(0, run(List.of(new CivIn("E", List.of())), Set.of(), 10).get(0).totalHonor());
    }
}
