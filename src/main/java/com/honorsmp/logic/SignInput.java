package com.honorsmp.logic;

import java.util.OptionalInt;

/** Pure parsing of what a judge typed into the fake sign editor. */
public final class SignInput {
    private SignInput() {}

    /**
     * Joins the sign lines (each trimmed, no separator), then accepts an optional leading + or - followed by
     * ASCII digits only. Empty, non-numeric or int-overflowing input gives empty.
     */
    public static OptionalInt parseAmount(String... lines) {
        if (lines == null) return OptionalInt.empty();
        StringBuilder sb = new StringBuilder();
        for (String l : lines) if (l != null) sb.append(l.trim());
        String s = sb.toString().trim();
        if (s.isEmpty()) return OptionalInt.empty();
        int i = 0;
        if (s.charAt(0) == '+' || s.charAt(0) == '-') i = 1;
        if (i >= s.length()) return OptionalInt.empty();
        for (int k = i; k < s.length(); k++) {
            char ch = s.charAt(k);
            if (ch < '0' || ch > '9') return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(s.charAt(0) == '+' ? s.substring(1) : s));
        } catch (NumberFormatException e) {
            return OptionalInt.empty();
        }
    }
}
