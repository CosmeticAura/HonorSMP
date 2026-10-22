package com.honorsmp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.DonationRotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 1.6.1: civ chests were removed. On the first load every stack still stored in a civ's legacy
 * {@code chest} (written by 1.5.0 and 1.6.0, including a chest that was open at shutdown, because
 * saveData writes the live screen into it) is appended to the civ leader's {@code donateBoxes} entry,
 * the leader gets a login notice, and the chest data is dropped.
 *
 * <p>It works on the parsed JSON, before the DonationBox escrow reads {@code donateBoxes}, so it needs
 * no item registry and runs the same way in tests. The escrow merges and splits stacks as usual when it
 * loads. The chest is set to null in the same step the stacks are appended, and data.json is rewritten
 * afterwards in one atomic write, so a stack is never in both places in a saved file. A second run finds
 * nothing to move.
 */
public final class CivChestMigration {
    public static final String NOTICE = "Your civ chest was removed. Its items are in /donation.";

    private CivChestMigration() {}

    /** Result for logging and tests. */
    public record Result(int stacks, long items, int civs) {}

    public static Result run(HonorData d) {
        int stacks = 0;
        long items = 0;
        int civsWithItems = 0;
        if (d.civs != null) {
            for (Civ civ : d.civs.values()) {
                if (civ == null || civ.chest == null) continue;
                List<JsonElement> chest = civ.chest;
                List<JsonElement> usable = new ArrayList<>();
                for (JsonElement je : chest) if (je != null && !je.isJsonNull()) usable.add(je);
                if (usable.isEmpty()) {
                    civ.chest = null;
                    continue;
                }
                String owner = recipient(civ);
                if (owner == null) {
                    // a civ with nobody in it cannot happen; never lose the items if it does
                    owner = "orphaned-civ-chest-" + civ.id;
                    Honor.LOG.error("Civ {} has no leader, officer or member; its chest items are kept in donateBoxes under {}", civ.name, owner);
                }
                if (d.donateBoxes == null) d.donateBoxes = new java.util.LinkedHashMap<>();
                d.donateBoxes.computeIfAbsent(owner, k -> new ArrayList<>()).addAll(usable);
                if (d.donateNotices == null) d.donateNotices = new java.util.LinkedHashMap<>();
                DonationRotation.addNotice(d.donateNotices.computeIfAbsent(owner, k -> new ArrayList<>()), NOTICE);
                stacks += usable.size();
                for (JsonElement je : usable) items += countOf(je);
                civsWithItems++;
                civ.chest = null;
            }
        }
        // 1.5.0 notices for a leader whose civ was disbanded while they were offline: keep them, in the donation notice list
        if (d.civChestNotices != null) {
            for (Map.Entry<String, Long> e : d.civChestNotices.entrySet()) {
                if (e.getValue() == null || e.getValue() <= 0) continue;
                if (d.donateNotices == null) d.donateNotices = new java.util.LinkedHashMap<>();
                DonationRotation.addNotice(d.donateNotices.computeIfAbsent(e.getKey(), k -> new ArrayList<>()),
                        "The " + e.getValue() + " items from your disbanded civilization's chest were moved to your donation box. Use /donation to claim them.");
            }
            d.civChestNotices = null;
        }
        if (stacks > 0) Honor.LOG.info("Civ chests removed: moved {} stacks ({} items) from {} civs to their leaders' /donation boxes", stacks, items, civsWithItems);
        return new Result(stacks, items, civsWithItems);
    }

    private static String recipient(Civ civ) {
        if (civ.leader != null && !civ.leader.isBlank()) return civ.leader;
        if (civ.officers != null && !civ.officers.isEmpty()) return civ.officers.iterator().next();
        if (civ.members != null && !civ.members.isEmpty()) return civ.members.iterator().next();
        return null;
    }

    private static long countOf(JsonElement je) {
        if (je.isJsonObject()) {
            JsonObject o = je.getAsJsonObject();
            if (o.has("count") && o.get("count").isJsonPrimitive()) {
                try {
                    return o.get("count").getAsLong();
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 1;
    }
}
