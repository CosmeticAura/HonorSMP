package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.PageNav;
import org.junit.jupiter.api.Test;

class NavTest {
    @Test
    void sliceMaths() {
        PageNav p0 = PageNav.of(100, 0);
        assertEquals(0, p0.from());
        assertEquals(45, p0.to());
        assertEquals(3, p0.pages());
        PageNav p2 = PageNav.of(100, 2);
        assertEquals(90, p2.from());
        assertEquals(100, p2.to());
        assertTrue(p2.hasPrev());
        assertFalse(p2.hasNext());
        assertEquals(1, p2.prev());
        assertEquals(2, p2.next());
    }

    @Test
    void exactMultipleHasNoEmptyPage() {
        assertEquals(2, PageNav.of(90, 0).pages());
        assertEquals(1, PageNav.of(45, 0).pages());
        assertEquals(2, PageNav.of(46, 0).pages());
        assertFalse(PageNav.of(45, 0).hasNext());
    }

    @Test
    void emptyListIsOneEmptyPage() {
        PageNav n = PageNav.of(0, 5);
        assertEquals(0, n.page());
        assertEquals(1, n.pages());
        assertEquals(0, n.from());
        assertEquals(0, n.to());
        assertFalse(n.hasPrev());
        assertFalse(n.hasNext());
    }

    @Test
    void clampsWhenEntriesShrinkOrPageIsNegative() {
        assertEquals(1, PageNav.of(50, 7).page());
        assertEquals(0, PageNav.of(10, 3).page());
        assertEquals(0, PageNav.of(10, -2).page());
        PageNav n = PageNav.of(46, 1); // one entry on page 1
        assertEquals(45, n.from());
        assertEquals(46, n.to());
        PageNav shrunk = PageNav.of(45, 1); // that entry was ruled on
        assertEquals(0, shrunk.page());
        assertEquals(45, shrunk.to());
    }

    @Test
    void backReturnsToStoredPage() {
        // list on page 2 opens a case: the case menu remembers 2; Back reopens page 2
        int stored = PageNav.of(120, 2).page();
        assertEquals(2, PageNav.of(120, stored).page());
        // a case was ruled meanwhile and the list shrank to 90: page 2 no longer exists, Back lands on the last page
        assertEquals(1, PageNav.of(90, stored).page());
        // paging inside a sub view does not change the page Back returns to
        PageNav loot = PageNav.of(100, 0);
        assertEquals(1, loot.next());
        assertEquals(2, stored);
    }

    @Test
    void customPerPage() {
        PageNav n = PageNav.of(10, 4, 2);
        assertEquals(8, n.from());
        assertEquals(10, n.to());
        assertEquals(3, n.pages());
    }
}
