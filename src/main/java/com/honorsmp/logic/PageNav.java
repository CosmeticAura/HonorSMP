package com.honorsmp.logic;

/**
 * Page maths for paged chest lists. Immutable: {@link #of} clamps the requested page into range, so a
 * Back button that remembers a page index always reopens a valid page, even when entries were removed.
 * Entries live in the first five rows (45 slots); the last row is for Back/Close and Previous/Next.
 */
public final class PageNav {
    public static final int PER_PAGE = 45;

    private final int total;
    private final int perPage;
    private final int page;

    private PageNav(int total, int perPage, int page) {
        this.total = total;
        this.perPage = perPage;
        this.page = page;
    }

    public static PageNav of(int total, int requestedPage) {
        return of(total, PER_PAGE, requestedPage);
    }

    public static PageNav of(int total, int perPage, int requestedPage) {
        int t = Math.max(0, total);
        int pp = Math.max(1, perPage);
        int pages = Math.max(1, (t + pp - 1) / pp);
        return new PageNav(t, pp, Math.max(0, Math.min(requestedPage, pages - 1)));
    }

    public int page() { return page; }

    public int pages() { return Math.max(1, (total + perPage - 1) / perPage); }

    /** First entry index on this page (inclusive). */
    public int from() { return Math.min(total, page * perPage); }

    /** Last entry index on this page (exclusive). */
    public int to() { return Math.min(total, from() + perPage); }

    public boolean hasPrev() { return page > 0; }

    public boolean hasNext() { return page < pages() - 1; }

    public int prev() { return hasPrev() ? page - 1 : page; }

    public int next() { return hasNext() ? page + 1 : page; }
}
