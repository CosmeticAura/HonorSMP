package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.FriendlyFire;
import org.junit.jupiter.api.Test;

class FriendlyFireTest {
    @Test
    void sameCivBlocked() {
        assertTrue(FriendlyFire.blocked("a", "a"));
    }

    @Test
    void differentCivsAllowed() {
        assertFalse(FriendlyFire.blocked("a", "b"));
    }

    @Test
    void noCivAllowed() {
        assertFalse(FriendlyFire.blocked(null, null));
        assertFalse(FriendlyFire.blocked("a", null));
        assertFalse(FriendlyFire.blocked(null, "a"));
        assertFalse(FriendlyFire.blocked("", ""));
    }
}
