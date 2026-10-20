package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.MenuClicks;
import com.honorsmp.logic.MenuClicks.Kind;
import com.honorsmp.logic.SignInput;
import org.junit.jupiter.api.Test;

class MenuLogicTest {
    @Test
    void parsesNumbers() {
        assertEquals(25, SignInput.parseAmount("25", "", "", "").getAsInt());
        assertEquals(-40, SignInput.parseAmount(" -40 ", "", "", "").getAsInt());
        assertEquals(15, SignInput.parseAmount("+15", "", "", "").getAsInt());
        assertEquals(123, SignInput.parseAmount("1", "2", "3", "").getAsInt());
        assertEquals(0, SignInput.parseAmount("-0", "", "", "").getAsInt());
    }

    @Test
    void rejectsBadInput() {
        assertTrue(SignInput.parseAmount("", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("   ", null, "", "").isEmpty());
        assertTrue(SignInput.parseAmount("abc", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("12", "x", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("+", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("-", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("1.5", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("--5", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("99999999999", "", "", "").isEmpty());
        assertTrue(SignInput.parseAmount("2147483648", "", "", "").isEmpty());
        assertEquals(Integer.MAX_VALUE, SignInput.parseAmount("2147483647").getAsInt());
        assertTrue(SignInput.parseAmount((String[]) null).isEmpty());
    }

    @Test
    void vanillaNeverProcessesAnyClick() {
        for (Kind k : Kind.values()) assertFalse(MenuClicks.vanillaMayProcess(k), k.name());
    }

    @Test
    void onlyPlainAndShiftClicksOnMenuSlotsActivate() {
        assertTrue(MenuClicks.activates(Kind.PICKUP, 10, 27, 0));
        assertTrue(MenuClicks.activates(Kind.PICKUP, 10, 27, 1));
        assertTrue(MenuClicks.activates(Kind.QUICK_MOVE, 10, 27, 0));
        assertFalse(MenuClicks.activates(Kind.SWAP, 10, 27, 0)); // number keys
        assertFalse(MenuClicks.activates(Kind.SWAP, 10, 27, 40)); // offhand swap
        assertFalse(MenuClicks.activates(Kind.THROW, 10, 27, 0));
        assertFalse(MenuClicks.activates(Kind.CLONE, 10, 27, 2));
        assertFalse(MenuClicks.activates(Kind.QUICK_CRAFT, 10, 27, 0));
        assertFalse(MenuClicks.activates(Kind.PICKUP_ALL, 10, 27, 0));
        assertFalse(MenuClicks.activates(Kind.PICKUP, 27, 27, 0)); // player inventory slot
        assertFalse(MenuClicks.activates(Kind.PICKUP, -999, 27, 0));
        assertFalse(MenuClicks.activates(Kind.PICKUP, 5, 27, 2)); // middle button
        assertFalse(MenuClicks.activates(null, 5, 27, 0));
    }

    @Test
    void ageText() {
        assertEquals("just now", MenuClicks.age(30_000));
        assertEquals("5m ago", MenuClicks.age(5 * 60_000L));
        assertEquals("2h 10m ago", MenuClicks.age(130 * 60_000L));
        assertEquals("3d 4h ago", MenuClicks.age((3 * 24 + 4) * 3600_000L));
    }
}
