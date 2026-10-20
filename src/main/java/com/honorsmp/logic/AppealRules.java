package com.honorsmp.logic;

import java.util.Collection;
import java.util.List;

/** Item 10: which honor change an appeal is about and whether the player may appeal. */
public final class AppealRules {
    private AppealRules() {}

    public record LogView(long id, String player, int delta) {}

    public record AppealView(String player, long logId, boolean open) {}

    /** Either an error message or the log entry id the appeal is about. */
    public record Pick(Long logId, String error) {}

    /** Parsed "/appeal" text: an optional log id and the reason. */
    public record Parsed(Long logId, String reason) {}

    /** "12 text" means log id 12 only if 12 is one of the player's own log ids; otherwise all of it is the reason. */
    public static Parsed parse(String input, java.util.function.LongPredicate isOwnLogId) {
        String t = input == null ? "" : input.trim();
        int sp = t.indexOf(' ');
        if (sp > 0) {
            try {
                long id = Long.parseLong(t.substring(0, sp));
                if (isOwnLogId.test(id)) return new Parsed(id, t.substring(sp + 1).trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return new Parsed(null, t);
    }

    /** True when this player already has an open appeal (only one at a time). */
    public static boolean hasOpenAppeal(String player, Collection<AppealView> appeals) {
        for (AppealView a : appeals) if (a.open() && a.player().equals(player)) return true;
        return false;
    }

    /** logs: all log entries, oldest first. requested: a specific log id, or null for the most recent change. */
    public static Pick pick(String player, Long requested, List<LogView> logs, Collection<AppealView> appeals) {
        if (hasOpenAppeal(player, appeals)) {
            return new Pick(null, "You already have an open appeal. Wait for a judge to rule on it.");
        }
        LogView chosen = null;
        for (LogView l : logs) {
            if (!l.player().equals(player) || l.delta() == 0) continue;
            if (requested == null) chosen = l;
            else if (l.id() == requested) {
                chosen = l;
                break;
            }
        }
        if (chosen == null) {
            return new Pick(null, requested == null ? "You have no honor changes to appeal."
                    : "No honor change #" + requested + " of yours was found.");
        }
        for (AppealView a : appeals) {
            if (a.logId() == chosen.id()) return new Pick(null, "Honor change #" + chosen.id() + " was already appealed.");
        }
        return new Pick(chosen.id(), null);
    }
}
