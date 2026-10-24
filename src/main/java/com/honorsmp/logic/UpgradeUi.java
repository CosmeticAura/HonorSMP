package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 1.7.0: the pure rules behind the single /civ upgrades menu (no Minecraft types, so it is unit tested):
 * what status an upgrade icon shows, which buttons its page offers, the permission text and the
 * "click again to confirm" tracker. The game rules (prices, upkeep, timers) live elsewhere and are untouched.
 */
public final class UpgradeUi {
    private UpgradeUi() {}

    public static final String ONLY_MANAGERS = "Only the leader or officers can do that";
    public static final long CONFIRM_MS = 5_000L;

    public enum Status {
        NOT_BOUGHT("Not bought"), ON("On"), PAUSED("Paused"), UNPAID("Unpaid (off)");

        public final String label;

        Status(String label) { this.label = label; }
    }

    /** Buttons on an upgrade's page. */
    public enum Action { BUY, PAY, RESUME, PAUSE }

    public static Status status(int level, boolean paused, boolean late) {
        if (level <= 0) return Status.NOT_BOUGHT;
        if (paused) return Status.PAUSED;
        if (late) return Status.UNPAID;
        return Status.ON;
    }

    /** "Paid until" line: time left like "1d 4h", or why there is none. {@code due} is epoch ms. */
    public static String paidUntil(Status s, long due, long now) {
        return switch (s) {
            case NOT_BOUGHT -> "-";
            case PAUSED -> "paused, no cost";
            case UNPAID -> "ran out " + CivEffects.duration(now - due) + " ago";
            case ON -> due <= 0 ? "not started" : CivEffects.duration(due - now) + " left";
        };
    }

    /**
     * The buttons an upgrade page shows, in order. Back is always there and not listed.
     * Buying the next level is not offered while the upgrade is paused or unpaid (same rule as before:
     * resume or pay first). Pay/Resume needs room under the pay-ahead cap.
     *
     * @param enabled   civ upgrades switched on in the config
     * @param payRoom   whole days that can still be paid now (0 = paid the maximum ahead)
     */
    public static List<Action> actions(boolean enabled, int level, int maxLevel, boolean paused, boolean late, int payRoom) {
        List<Action> out = new ArrayList<>();
        if (!enabled) return out;
        if (level < maxLevel && !paused && !late) out.add(Action.BUY);
        if (level > 0) {
            if (paused) {
                if (payRoom > 0) out.add(Action.RESUME);
            } else {
                if (payRoom > 0) out.add(Action.PAY);
                out.add(Action.PAUSE);
            }
        }
        return out;
    }

    /** Last lore line of the main-page icon: what a click does. */
    public static String clickHint(Status s, boolean manage, boolean maxLevel) {
        if (!manage) return "Click to look. Only the leader or officers can change it.";
        if (s == Status.NOT_BOUGHT) return "Click to buy level 1";
        if (maxLevel) return "Click to pay or pause";
        return "Click to upgrade, pay or pause";
    }

    // ------------------------------------------------------------ confirm clicks

    /**
     * "Click again to confirm": the first click on an action arms it for {@link #CONFIRM_MS}; a second click on the
     * SAME action by the same player inside that time confirms it. Anything else (other action, too late) re-arms.
     */
    public static final class Confirm {
        private final Map<UUID, String> key = new HashMap<>();
        private final Map<UUID, Long> until = new HashMap<>();

        /** True when this click confirms (and clears the arm); false when it only armed the action. */
        public boolean click(UUID who, String action, long now) {
            String k = key.get(who);
            Long t = until.get(who);
            if (k != null && t != null && k.equals(action) && now <= t) {
                clear(who);
                return true;
            }
            key.put(who, action);
            until.put(who, now + CONFIRM_MS);
            return false;
        }

        /** True while the action is armed for this player (the button then reads "Click again to confirm"). */
        public boolean armed(UUID who, String action, long now) {
            Long t = until.get(who);
            return t != null && action.equals(key.get(who)) && now <= t;
        }

        public void clear(UUID who) {
            key.remove(who);
            until.remove(who);
        }
    }
}
