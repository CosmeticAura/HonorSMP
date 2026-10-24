package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivEffects;
import com.honorsmp.logic.CivUpkeep;
import com.honorsmp.logic.CivEffects.Values;
import com.honorsmp.logic.CivUpgrades.Upgrade;
import com.honorsmp.logic.RankPerks;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Applies civ upgrade effects to online members and handles the upkeep warnings. Effects only run while upgrades are active. */
public final class CivPerks {
    private CivPerks() {}

    private static final Identifier STR_ID = Identifier.of("honorsmp", "civ_strength");
    private static final Identifier SPD_ID = Identifier.of("honorsmp", "civ_speed");
    private static final Identifier HP_ID = Identifier.of("honorsmp", "civ_fortitude");
    private static final int HASTE_TICKS = 200; // refreshed every 5 s by refreshPlayer, so it never lapses

    /** civ id to a bit mask of the upgrades that were working last second (to notice one switching off or on). */
    private static final Map<String, Integer> LAST_ACTIVE = new HashMap<>();

    private static int activeMask(Civ civ, Honor h, long now) {
        int mask = 0;
        for (Upgrade u : Upgrade.values()) if (CivUpgradeApi.upgradeActive(civ, u, h.config, now)) mask |= 1 << u.ordinal();
        return mask;
    }

    public static Values valuesFor(Honor h, ServerPlayerEntity p) {
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null) return Values.NONE;
        long now = System.currentTimeMillis();
        // 1.6.9: each upgrade is switched on or off on its own (late or paused upgrades give nothing, the others keep working)
        return CivEffects.values(h.config, true, CivUpgradeApi.activeLevel(civ, Upgrade.RESISTANCE, h.config, now),
                CivUpgradeApi.activeLevel(civ, Upgrade.STRENGTH, h.config, now), CivUpgradeApi.activeLevel(civ, Upgrade.SPEED, h.config, now),
                CivUpgradeApi.activeLevel(civ, Upgrade.HASTE, h.config, now), CivUpgradeApi.activeLevel(civ, Upgrade.FORTITUDE, h.config, now));
    }

    /** Damage multiplier for incoming damage on this player (1 = unchanged). Read live, so it is off the moment upkeep is late. */
    public static float damageMultiplier(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return 1f;
        return CivEffects.damageMultiplier(valuesFor(h, p).resistancePercent());
    }

    /** Sets or removes all attribute and effect parts for this player. Cheap; safe to call often. */
    public static void apply(Honor h, ServerPlayerEntity p, boolean remove) {
        Values v = remove ? Values.NONE : valuesFor(h, p);
        set(p.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE), STR_ID, v.strengthBonus(), EntityAttributeModifier.Operation.ADD_VALUE);
        set(p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED), SPD_ID, v.speedPercent() / 100.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        EntityAttributeInstance hp = p.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        set(hp, HP_ID, v.fortitudeHp(), EntityAttributeModifier.Operation.ADD_VALUE);
        float max = p.getMaxHealth();
        if (p.getHealth() > max) p.setHealth(RankPerks.clampHealth(p.getHealth(), max));
        haste(p, v.hasteLevel());
    }

    private static void set(EntityAttributeInstance inst, Identifier id, double value, EntityAttributeModifier.Operation op) {
        if (inst == null) return;
        EntityAttributeModifier cur = inst.getModifier(id);
        if (value == 0) {
            if (cur != null) inst.removeModifier(id);
        } else if (cur == null || cur.value() != value) {
            if (cur != null) inst.removeModifier(id);
            inst.addTemporaryModifier(new EntityAttributeModifier(id, value, op));
        }
    }

    /** Our Haste is ambient with no particles and no icon; beacon/potion Haste is never all three. */
    private static boolean ours(StatusEffectInstance e) {
        return e.isAmbient() && !e.shouldShowParticles() && !e.shouldShowIcon();
    }

    private static void haste(ServerPlayerEntity p, int level) {
        RegistryEntry<net.minecraft.entity.effect.StatusEffect> type = StatusEffects.HASTE;
        StatusEffectInstance cur = p.getStatusEffect(type);
        if (level <= 0) {
            if (cur != null && ours(cur)) p.removeStatusEffect(type);
            return;
        }
        int amp = level - 1;
        if (cur != null && !ours(cur) && cur.getAmplifier() >= amp) return; // a real effect is as good or better
        if (cur != null && ours(cur) && cur.getAmplifier() == amp && cur.getDuration() > HASTE_TICKS / 2) return;
        p.addStatusEffect(new StatusEffectInstance(type, HASTE_TICKS, amp, true, false, false));
    }

    /** Re-syncs every online member of the civ right now (bought, paid, joined, kicked, late, disbanded). */
    public static void syncCiv(Honor h, Civ civ) {
        if (civ == null) return;
        for (String id : new java.util.ArrayList<>(civ.members)) {
            ServerPlayerEntity m = h.online(id);
            if (m != null) h.refreshPlayer(m);
        }
    }

    /** After someone left or was kicked or the civ was disbanded: their civ effects go straight away. */
    public static void syncPlayer(Honor h, String uuid) {
        ServerPlayerEntity m = h.online(uuid);
        if (m != null) h.refreshPlayer(m);
    }

    // ------------------------------------------------------------ upkeep: periodic and login

    /** Once a second: notices the upkeep turning late (switch off now), sends the one-time warning before it is due. */
    public static void second(Honor h) {
        long now = System.currentTimeMillis();
        LAST_ACTIVE.keySet().removeIf(id -> !h.data.civs.containsKey(id));
        for (Civ civ : h.data.civs.values()) {
            int mask = activeMask(civ, h, now);
            Integer last = LAST_ACTIVE.put(civ.id, mask);
            if (last != null && last != mask) {
                syncCiv(h, civ);
                // name every upgrade that just turned late (a paused one never counts)
                for (Upgrade u : Upgrade.values()) {
                    int bit = 1 << u.ordinal();
                    if ((last & bit) != 0 && (mask & bit) == 0 && CivUpgradeApi.isLate(civ, u, now)) {
                        notifyManagers(h, civ, "Your civ's " + u.display + " upkeep is late. " + u.display + " is off until you pay. Open /civ upgrades to pay.", true);
                    }
                }
            }
            for (Upgrade u : Upgrade.values()) {
                if (CivUpgradeApi.takeWarningOnce(civ, u, h.config, now)) {
                    notifyManagers(h, civ, "Your civ's " + u.display + " upkeep is due in " + CivEffects.duration(CivUpgradeApi.dueAt(civ, u) - now)
                            + " (" + CivUpkeep.describe(CivUpgradeApi.owedPerDay(civ, u, h.config)) + "). Open /civ upgrades to pay.", false);
                    h.markDirty();
                }
            }
        }
    }

    private static void notifyManagers(Honor h, Civ civ, String msg, boolean red) {
        for (String id : civ.members) {
            if (!CivUpgradeApi.canManage(civ, id)) continue;
            ServerPlayerEntity m = h.online(id);
            if (m != null) m.sendMessage(Text.literal("[" + civ.name + "] " + msg).formatted(red ? Formatting.RED : Formatting.GOLD));
        }
    }

    /** Login: leader and officers hear if the upkeep is late or due soon. */
    public static void onLogin(Honor h, ServerPlayerEntity p) {
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null || !CivUpgradeApi.canManage(civ, p.getUuid().toString())) return;
        long now = System.currentTimeMillis();
        for (Upgrade u : Upgrade.values()) {
            if (CivUpgradeApi.isLate(civ, u, now)) {
                p.sendMessage(Text.literal("Your civ's " + u.display + " is off until you pay its upkeep. Open /civ upgrades to pay ("
                        + CivUpkeep.describe(CivUpgradeApi.owedPerDay(civ, u, h.config)) + ")").formatted(Formatting.RED));
            } else if (CivUpgradeApi.warningDue(civ, u, h.config, now)) {
                p.sendMessage(Text.literal("Your civ's " + u.display + " upkeep is due in " + CivEffects.duration(CivUpgradeApi.dueAt(civ, u) - now)
                        + ". Open /civ upgrades to pay (" + CivUpkeep.describe(CivUpgradeApi.owedPerDay(civ, u, h.config)) + ")").formatted(Formatting.GOLD));
            }
        }
    }
}
