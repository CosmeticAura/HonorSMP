package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/** /civ top: civs ranked by the total honor of their (non-judge) members. */
public final class CivLeaderboard {
    private CivLeaderboard() {}

    public record Row(String name, int totalHonor, int memberCount) {}

    /** One input civ: name and member uuids. */
    public record CivIn(String name, Collection<String> members) {}

    public static List<Row> top(Collection<CivIn> civs, ToIntFunction<String> honorOf, Predicate<String> isJudge, int limit) {
        List<Row> rows = new ArrayList<>();
        for (CivIn c : civs) {
            int total = 0;
            for (String m : c.members()) {
                if (!isJudge.test(m)) total += honorOf.applyAsInt(m);
            }
            rows.add(new Row(c.name(), total, c.members().size()));
        }
        rows.sort(Comparator.comparingInt(Row::totalHonor).reversed()
                .thenComparing(Comparator.comparingInt(Row::memberCount).reversed())
                .thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, Math.max(0, limit))) : rows;
    }
}
