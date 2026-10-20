package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/** /honor top lists: top only has players who are honorable right now, bottom only dishonored ones. */
public final class Leaderboard {
    private Leaderboard() {}

    public static <T> List<T> top(Collection<T> all, ToIntFunction<T> score, HonorConfig c, int n) {
        return pick(all, score, c, n, true);
    }

    public static <T> List<T> bottom(Collection<T> all, ToIntFunction<T> score, HonorConfig c, int n) {
        return pick(all, score, c, n, false);
    }

    private static <T> List<T> pick(Collection<T> all, ToIntFunction<T> score, HonorConfig c, int n, boolean top) {
        List<T> out = new ArrayList<>();
        for (T t : all) {
            Tier tier = Tier.of(score.applyAsInt(t), c);
            if (top ? tier.isHonorable() : tier.isDishonored()) out.add(t);
        }
        Comparator<T> cmp = Comparator.comparingInt(score);
        out.sort(top ? cmp.reversed() : cmp);
        return out.size() > n ? new ArrayList<>(out.subList(0, n)) : out;
    }
}
