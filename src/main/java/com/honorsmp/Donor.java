package com.honorsmp;

import com.honorsmp.HonorData.DonationAward;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.DonorPerks;
import com.honorsmp.logic.LeaderboardEmbed;
import com.honorsmp.logic.DonorPerks.DonorTier;
import com.honorsmp.logic.WeeklyDonor;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Donor perks (1.6.0): lifetime donation honor, tiers, Luck, respawn regeneration, weekly top donor. Judges get nothing. */
public final class Donor {
    private Donor() {}

    private static final Identifier LUCK_ID = Identifier.of("honorsmp", "donor_luck");
    /** Donation awards older than this are dropped (the weekly window needs 7 days; some slack for a late run). */
    private static final long KEEP_MS = 14L * 24 * 3600 * 1000;
    private static final long TICK_MS = 50;

    public static DonorTier tier(Honor h, PlayerRecord r) {
        return DonorPerks.effectiveTier(r.donatedHonorTotal, h.isJudge(r.uuid), h.config);
    }

    public static DonorTier tier(Honor h, ServerPlayerEntity p) {
        return tier(h, h.record(p));
    }

    private static ZoneId zone() { return ZoneId.systemDefault(); }

    // ------------------------------------------------------------ data

    /**
     * Runs once on the first load of 1.6.0 data: donatedHonorTotal = positive "Donated items"/"Donated food" log entries
     * (the log is capped by maxLogEntries, so donations that already fell off the end are not counted). Also seeds the
     * weekly window from the last 14 days of those entries.
     */
    public static void backfillOnce(Honor h) {
        HonorData d = h.data;
        if (d.donorBackfilled) return;
        List<DonorPerks.LogLine> lines = new ArrayList<>();
        for (HonorData.LogEntry e : d.logs) lines.add(new DonorPerks.LogLine(e.player, e.reason, e.delta, e.time));
        var totals = DonorPerks.backfill(lines);
        for (var e : totals.entrySet()) {
            PlayerRecord r = d.players.get(e.getKey());
            if (r != null) r.donatedHonorTotal = e.getValue();
        }
        long cutoff = System.currentTimeMillis() - KEEP_MS;
        for (DonorPerks.LogLine l : DonorPerks.donationsSince(lines, cutoff)) {
            PlayerRecord r = d.players.get(l.player());
            if (r == null) continue;
            if (r.donationAwards == null) r.donationAwards = new ArrayList<>();
            r.donationAwards.add(new DonationAward(l.time(), l.delta()));
        }
        d.donorBackfilled = true;
        h.markDirty();
        Honor.LOG.info("Donor backfill: {} players have donation honor from the log", totals.size());
    }

    /** Call right after donation honor was granted (amount after daily caps). Judges earn none. */
    public static void recordAward(Honor h, ServerPlayerEntity p, int amount) {
        if (amount <= 0 || h.isJudge(p)) return;
        PlayerRecord r = h.record(p);
        DonorTier before = tier(h, r);
        long now = System.currentTimeMillis();
        r.donatedHonorTotal += amount;
        if (r.donationAwards == null) r.donationAwards = new ArrayList<>();
        r.donationAwards.add(new DonationAward(now, amount));
        r.donationAwards.removeIf(a -> a.time < now - KEEP_MS);
        h.markDirty();
        DonorTier after = tier(h, r);
        h.refreshPlayer(p);
        if (after != before) {
            p.sendMessage(Text.literal("You are now a donor tier: " + after.display + "! (" + r.donatedHonorTotal + " lifetime donation honor)")
                    .formatted(Formatting.GOLD));
        }
    }

    // ------------------------------------------------------------ perks

    /** Luck and the top donor's Hero of the Village. Cheap; called from the attribute sync. remove = player is leaving. */
    public static void apply(Honor h, ServerPlayerEntity p, boolean remove) {
        DonorTier t = remove ? DonorTier.NONE : tier(h, p);
        EntityAttributeInstance luck = p.getAttributeInstance(EntityAttributes.LUCK);
        if (luck != null) {
            double v = DonorPerks.luck(t, h.config);
            EntityAttributeModifier cur = luck.getModifier(LUCK_ID);
            if (v == 0) {
                if (cur != null) luck.removeModifier(LUCK_ID);
            } else if (cur == null || cur.value() != v) {
                if (cur != null) luck.removeModifier(LUCK_ID);
                luck.addTemporaryModifier(new EntityAttributeModifier(LUCK_ID, v, EntityAttributeModifier.Operation.ADD_VALUE));
            }
        }
        if (!remove) hero(h, p);
    }

    /** Our Hero of the Village is ambient, no particles, icon shown; the raid reward is never ambient. */
    private static boolean ours(StatusEffectInstance e) {
        return e.isAmbient() && !e.shouldShowParticles() && e.shouldShowIcon();
    }

    private static void hero(Honor h, ServerPlayerEntity p) {
        HonorData d = h.data;
        String id = p.getUuid().toString();
        var type = StatusEffects.HERO_OF_THE_VILLAGE;
        StatusEffectInstance cur = p.getStatusEffect(type);
        long now = System.currentTimeMillis();
        boolean winner = id.equals(d.topDonorUuid) && now < d.topDonorExpiresAt && !h.isJudge(id);
        if (!winner) {
            if (d.topDonorClear.remove(id)) h.markDirty();
            if (cur != null && ours(cur)) p.removeStatusEffect(type);
            return;
        }
        int want = (int) Math.min(Integer.MAX_VALUE / 2, (d.topDonorExpiresAt - now) / TICK_MS);
        // re-add when it is missing or has run lower than it should (milk, death, a shorter effect that replaced it)
        if (cur == null || cur.getAmplifier() < 0 || cur.getDuration() < want - 400) {
            p.addStatusEffect(new StatusEffectInstance(type, want, 0, true, false, true));
        }
    }

    /** After a death respawn (not the end-portal return): Benefactors get Regeneration I. */
    public static void onRespawn(Honor h, ServerPlayerEntity p, boolean alive) {
        if (alive) return;
        int secs = DonorPerks.respawnRegenSeconds(tier(h, p), h.config);
        if (secs > 0) p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, secs * 20, 0, false, true, true));
    }

    /** Hunger factor part: total percent slower for this player given the rank perk percent. */
    public static int hungerPercent(Honor h, ServerPlayerEntity p, int rankPercent) {
        return DonorPerks.totalHungerPercent(rankPercent, tier(h, p), h.config);
    }

    /** Chat tag text without brackets for this record, or null. */
    public static String tag(Honor h, PlayerRecord r) {
        return DonorPerks.tagName(tier(h, r));
    }

    public static Formatting tagColor(String tag) {
        return "Benefactor".equals(tag) ? Formatting.GOLD : Formatting.AQUA;
    }

    // ------------------------------------------------------------ weekly top donor

    /** Every 10 s from the main second tick: runs the Monday job when due (also catches up after downtime) and ends an expired effect. */
    public static void second(Honor h, int seconds) {
        if (seconds % 10 != 0) return;
        HonorData d = h.data;
        long now = System.currentTimeMillis();
        if (!d.topDonorUuid.isEmpty() && now >= d.topDonorExpiresAt) endWinner(h);
        if (h.config.topDonorEnabled && WeeklyDonor.due(d.lastWeeklyDonorRun, now, zone())) runWeekly(h, now);
    }

    /** Who is ahead in donation honor so far this week (since Monday 00:00), for the Discord leaderboard. Null = nobody. Judges never count. */
    public static LeaderboardEmbed.DonorRow topThisWeek(Honor h, long now) {
        long monday = WeeklyDonor.mondayStart(now, zone());
        List<WeeklyDonor.Award> awards = new ArrayList<>();
        for (PlayerRecord r : h.data.players.values()) {
            if (r.donationAwards == null) continue;
            for (DonationAward a : r.donationAwards) awards.add(new WeeklyDonor.Award(r.uuid, a.time, a.amount));
        }
        WeeklyDonor.Winner w = WeeklyDonor.select(awards, h::isJudge, monday, WeeklyDonor.nextWeek(monday, zone()));
        if (w == null) return null;
        PlayerRecord r = h.data.players.get(w.player());
        return new LeaderboardEmbed.DonorRow(r == null || r.name == null ? w.player() : r.name, w.total());
    }

    private static void endWinner(Honor h) {
        HonorData d = h.data;
        if (d.topDonorUuid.isEmpty()) return;
        String old = d.topDonorUuid;
        d.topDonorUuid = "";
        d.topDonorName = "";
        d.topDonorAmount = 0;
        d.topDonorExpiresAt = 0;
        ServerPlayerEntity live = h.online(old);
        if (live != null) h.refreshPlayer(live); // hero() sees they are no longer the winner and removes ours
        else d.topDonorClear.add(old);
        h.markDirty();
    }

    static void runWeekly(Honor h, long now) {
        HonorData d = h.data;
        long monday = WeeklyDonor.mondayStart(now, zone());
        d.lastWeeklyDonorRun = monday;
        endWinner(h);
        List<WeeklyDonor.Award> awards = new ArrayList<>();
        for (PlayerRecord r : d.players.values()) {
            if (r.donationAwards == null) continue;
            r.donationAwards.removeIf(a -> a.time < now - KEEP_MS);
            for (DonationAward a : r.donationAwards) awards.add(new WeeklyDonor.Award(r.uuid, a.time, a.amount));
        }
        WeeklyDonor.Winner w = WeeklyDonor.select(awards, h::isJudge, WeeklyDonor.previousWeek(monday, zone()), monday);
        h.markDirty();
        if (w == null) return;
        PlayerRecord r = d.players.get(w.player());
        d.topDonorUuid = w.player();
        d.topDonorName = r == null || r.name == null ? w.player() : r.name;
        d.topDonorAmount = (int) Math.min(Integer.MAX_VALUE, w.total());
        d.topDonorExpiresAt = monday + (long) Math.max(1, h.config.topDonorHeroDays) * 24 * 3600 * 1000;
        d.topDonorClear.remove(w.player());
        h.server.getPlayerManager().broadcast(Text.literal("Top donor of the week: " + d.topDonorName + " earned " + w.total()
                + " honor from donations! They get Hero of the Village for " + h.config.topDonorHeroDays + " days.")
                .formatted(Formatting.GOLD), false);
        ServerPlayerEntity live = h.online(w.player());
        if (live != null) h.refreshPlayer(live);
    }

    // ------------------------------------------------------------ display

    /** Lines for /honor and the /donate info item. Empty for judges. */
    public static List<String> summary(Honor h, PlayerRecord r) {
        List<String> out = new ArrayList<>();
        if (h.isJudge(r.uuid)) return out;
        DonorTier t = tier(h, r);
        long total = r.donatedHonorTotal;
        DonorTier next = DonorPerks.next(t);
        out.add("Donation honor: " + total + " lifetime. Donor tier: " + (t == DonorTier.NONE ? "none" : t.display));
        if (next == null) out.add("Top donor tier reached.");
        else out.add(DonorPerks.honorToNext(total, h.config) + " more donation honor for " + next.display + " (" + DonorPerks.threshold(next, h.config) + ").");
        return out;
    }
}
