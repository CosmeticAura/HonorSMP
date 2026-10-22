package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure rules for donor tiers (lifetime honor earned from donating) and what each tier gives. Tiers are cumulative. */
public final class DonorPerks {
    private DonorPerks() {}

    public enum DonorTier {
        NONE("None"), SUPPORTER("Supporter"), PATRON("Patron"), BENEFACTOR("Benefactor");

        public final String display;

        DonorTier(String display) { this.display = display; }

        public boolean atLeast(DonorTier o) { return ordinal() >= o.ordinal(); }
    }

    public static DonorTier tierOf(long lifetime, HonorConfig c) {
        if (lifetime >= c.donorBenefactorMin) return DonorTier.BENEFACTOR;
        if (lifetime >= c.donorPatronMin) return DonorTier.PATRON;
        if (lifetime >= c.donorSupporterMin) return DonorTier.SUPPORTER;
        return DonorTier.NONE;
    }

    /** Judges get no donor perks at all. */
    public static DonorTier effectiveTier(long lifetime, boolean judge, HonorConfig c) {
        return judge ? DonorTier.NONE : tierOf(lifetime, c);
    }

    /** The tier after this one, or null at the top. */
    public static DonorTier next(DonorTier t) {
        return t == DonorTier.BENEFACTOR ? null : DonorTier.values()[t.ordinal() + 1];
    }

    public static int threshold(DonorTier t, HonorConfig c) {
        return switch (t) {
            case NONE -> 0;
            case SUPPORTER -> c.donorSupporterMin;
            case PATRON -> c.donorPatronMin;
            case BENEFACTOR -> c.donorBenefactorMin;
        };
    }

    /** Honor still needed for the next tier, or -1 at the top tier. */
    public static long honorToNext(long lifetime, HonorConfig c) {
        DonorTier n = next(tierOf(lifetime, c));
        return n == null ? -1 : Math.max(0, threshold(n, c) - lifetime);
    }

    /** Chat tag text without brackets, or null for no tag. Benefactor replaces the Supporter tag; Patron keeps Supporter's. */
    public static String tagName(DonorTier t) {
        return switch (t) {
            case NONE -> null;
            case SUPPORTER, PATRON -> "Supporter";
            case BENEFACTOR -> "Benefactor";
        };
    }

    public static int hungerPercent(DonorTier t, HonorConfig c) {
        return t.atLeast(DonorTier.SUPPORTER) ? Math.max(0, c.donorHungerSlowPercent) : 0;
    }

    /** Total hunger slowdown in percent: rank + donor, capped (the cap never lowers the rank perk itself). */
    public static int totalHungerPercent(int rankPercent, DonorTier t, HonorConfig c) {
        int sum = Math.max(0, rankPercent) + hungerPercent(t, c);
        return Math.min(sum, Math.max(c.donorHungerCapPercent, rankPercent));
    }

    /** Extra Luck attribute (0 below Patron). */
    public static double luck(DonorTier t, HonorConfig c) {
        return t.atLeast(DonorTier.PATRON) ? c.donorLuckBonus : 0;
    }

    /** Regeneration I seconds on respawn (0 below Benefactor). */
    public static int respawnRegenSeconds(DonorTier t, HonorConfig c) {
        return t.atLeast(DonorTier.BENEFACTOR) ? Math.max(0, c.donorRespawnRegenSeconds) : 0;
    }

    // ------------------------------------------------------------ backfill

    public record LogLine(String player, String reason, int delta, long time) {}

    public static boolean isDonationReason(String reason) {
        return "Donated items".equals(reason) || "Donated food".equals(reason);
    }

    /**
     * Lifetime donation honor per player from the honor log: only positive entries with a donation reason.
     * The log is capped (maxLogEntries), so very old donations may already be gone; those are not counted.
     */
    public static Map<String, Long> backfill(List<LogLine> log) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (LogLine e : log) {
            if (e.player() == null || e.delta() <= 0 || !isDonationReason(e.reason())) continue;
            m.merge(e.player(), (long) e.delta(), Long::sum);
        }
        return m;
    }

    /** The donation log lines newer than the cutoff, to seed the weekly window. */
    public static List<LogLine> donationsSince(List<LogLine> log, long cutoff) {
        List<LogLine> out = new ArrayList<>();
        for (LogLine e : log) {
            if (e.player() != null && e.delta() > 0 && e.time() >= cutoff && isDonationReason(e.reason())) out.add(e);
        }
        return out;
    }
}
