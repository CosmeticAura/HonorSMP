package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.List;

/** Pure helpers for the treasury. */
public final class TreasuryMath {
    private TreasuryMath() {}

    /** Splits an emerald count into stack sizes of at most maxStack. */
    public static List<Integer> splitStacks(long total, int maxStack) {
        List<Integer> out = new ArrayList<>();
        long left = total;
        while (left > 0) {
            int n = (int) Math.min(left, maxStack);
            out.add(n);
            left -= n;
        }
        return out;
    }
}
