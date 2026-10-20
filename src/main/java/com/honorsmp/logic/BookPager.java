package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.List;

/** Item 11: splits lines of text into written book pages that fit on screen. */
public final class BookPager {
    private BookPager() {}

    public static final int MAX_TITLE = 32;
    /** About 14 lines of 19 characters; stay under it so nothing is cut off. */
    public static final int PAGE_CHARS = 230;
    public static final int MAX_PAGES = 100;

    public static String title(String t) {
        return t.length() > MAX_TITLE ? t.substring(0, MAX_TITLE) : t;
    }

    /** Fills pages line by line. A line longer than a page is cut at word boundaries. At most MAX_PAGES pages. */
    public static List<String> paginate(List<String> lines, int pageChars) {
        List<String> pages = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : lines) {
            for (String line : cut(raw, pageChars)) {
                if (cur.length() > 0 && cur.length() + 1 + line.length() > pageChars) {
                    pages.add(cur.toString());
                    cur.setLength(0);
                }
                if (cur.length() > 0) cur.append('\n');
                cur.append(line);
            }
        }
        if (cur.length() > 0) pages.add(cur.toString());
        if (pages.isEmpty()) pages.add("");
        return pages.size() > MAX_PAGES ? new ArrayList<>(pages.subList(0, MAX_PAGES)) : pages;
    }

    static List<String> cut(String s, int max) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (rest.length() > max) {
            int at = rest.lastIndexOf(' ', max);
            if (at <= 0) at = max;
            out.add(rest.substring(0, at));
            rest = rest.substring(at).stripLeading();
        }
        out.add(rest);
        return out;
    }
}
