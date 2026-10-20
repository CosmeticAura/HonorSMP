package com.honorsmp.logic;

/** Pure helpers for case names: sanitising what a judge typed and formatting the display title. */
public final class CaseName {
    private CaseName() {}

    public static final int MAX_LEN = 40;

    /** Joins sign lines with a space, then sanitises. Empty result means "clear the name". */
    public static String fromSign(String... lines) {
        if (lines == null) return "";
        StringBuilder sb = new StringBuilder();
        for (String l : lines) {
            if (l == null || l.isBlank()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(l.trim());
        }
        return sanitize(sb.toString());
    }

    /** Strips section-sign formatting codes and control characters, collapses spaces, trims, caps at MAX_LEN. */
    public static String sanitize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\u00a7') {
                i++; // drop the code character too
                continue;
            }
            if (Character.isISOControl(ch)) ch = ' ';
            sb.append(ch);
        }
        String t = sb.toString().replaceAll("\\s+", " ").trim();
        if (t.length() > MAX_LEN) t = t.substring(0, MAX_LEN).trim();
        return t;
    }

    /** "Case #7" or "Case #7 \"Name\"" - the id is always visible. */
    public static String title(int id, String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? "Case #" + id : "Case #" + id + " \"" + n + "\"";
    }
}
