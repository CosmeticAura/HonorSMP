package com.honorsmp.logic;

/**
 * Decision logic for read-only chest menus. Vanilla never gets to process ANY click on such a menu
 * (nothing may be moved in, out or around); the only question is whether a click presses a button.
 */
public final class MenuClicks {
    private MenuClicks() {}

    /** Mirror of the vanilla SlotActionType values. */
    public enum Kind { PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL }

    /** Always false: no click type is ever handed to vanilla on a menu. */
    public static boolean vanillaMayProcess(Kind k) {
        return false;
    }

    /** True when the click presses the button in {@code slot}: a left/right click or shift-click on a menu slot. */
    public static boolean activates(Kind k, int slot, int menuSlots, int button) {
        if (k == null || slot < 0 || slot >= menuSlots) return false;
        return (k == Kind.PICKUP || k == Kind.QUICK_MOVE) && (button == 0 || button == 1);
    }

    /** "5m ago", "2h 10m ago", "3d 4h ago". */
    public static String age(long ms) {
        long m = Math.max(0, ms) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h " + (m % 60) + "m ago";
        return (h / 24) + "d " + (h % 24) + "h ago";
    }
}
