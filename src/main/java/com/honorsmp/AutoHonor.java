package com.honorsmp;

import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.AutoRules;
import com.honorsmp.logic.BossFights;
import java.time.LocalDate;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Property;

/** Thin Minecraft glue for the automatic honor events. The math lives in logic/AutoRules and logic/BossFights. */
public final class AutoHonor {
    private AutoHonor() {}

    public static final BossFights FIGHTS = new BossFights();

    /** The player's record with its daily fields rolled, or null for judges / no server. */
    private static PlayerRecord rec(Honor h, ServerPlayerEntity p) {
        if (h.isJudge(p)) return null;
        PlayerRecord r = h.record(p);
        AutoRules.rollDay(r, AutoRules.todayKey(LocalDate.now()));
        return r;
    }

    private static void give(Honor h, ServerPlayerEntity p, PlayerRecord r, int honor, String reason) {
        h.change(r.uuid, r.name, honor, reason, "auto", p, "", 0);
        h.markDirty();
    }

    public static void onCure(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        PlayerRecord r = rec(h, p);
        if (r == null) return;
        int g = AutoRules.capped(h.config.zombieCureHonor, r.cureHonorToday, h.config.zombieCureDailyCap);
        if (g <= 0) return;
        r.cureHonorToday += g;
        give(h, p, r, g, "Cured a zombie villager");
    }

    public static void onHarvest(ServerPlayerEntity p, BlockState state) {
        Honor h = Honor.I;
        if (h == null || p.isCreative() || p.isSpectator()) return;
        String id = Registries.BLOCK.getId(state.getBlock()).toString();
        int age = -1;
        for (Property<?> pr : state.getProperties()) {
            if (pr instanceof IntProperty ip && ip.getName().equals("age")) age = state.get(ip);
        }
        if (!AutoRules.matureCrop(id, age)) return;
        PlayerRecord r = rec(h, p);
        if (r == null) return;
        var c = h.config;
        var s = AutoRules.counter(r.harvestCount, c.harvestPerCrops, c.harvestHonor, r.harvestHonorToday, c.harvestDailyCap);
        r.harvestCount = s.count();
        h.markDirty();
        if (s.honor() > 0) {
            r.harvestHonorToday += s.honor();
            give(h, p, r, s.honor(), "Harvested " + c.harvestPerCrops + " crops");
        }
    }

    public static void onBreed(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        PlayerRecord r = rec(h, p);
        if (r == null) return;
        var c = h.config;
        var s = AutoRules.counter(r.breedCount, c.breedPerBreeds, c.breedHonor, r.breedHonorToday, c.breedDailyCap);
        r.breedCount = s.count();
        h.markDirty();
        if (s.honor() > 0) {
            r.breedHonorToday += s.honor();
            give(h, p, r, s.honor(), "Bred " + c.breedPerBreeds + " animals");
        }
    }

    public static void onTrade(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        PlayerRecord r = rec(h, p);
        if (r == null) return;
        var c = h.config;
        var s = AutoRules.counter(r.tradeCount, c.tradePerTrades, c.tradeHonor, r.tradeHonorToday, c.tradeDailyCap);
        r.tradeCount = s.count();
        h.markDirty();
        if (s.honor() > 0) {
            r.tradeHonorToday += s.honor();
            give(h, p, r, s.honor(), "Completed " + c.tradePerTrades + " trades");
        }
    }

    public static void onRaidWon(ServerPlayerEntity p) {
        Honor h = Honor.I;
        if (h == null) return;
        PlayerRecord r = rec(h, p);
        if (r == null || h.config.raidWinHonor == 0) return;
        give(h, p, r, h.config.raidWinHonor, "Won a raid");
    }

    private static String bossType(LivingEntity e) {
        if (e instanceof EnderDragonEntity) return "ender_dragon";
        if (e instanceof WitherEntity) return "wither";
        if (e instanceof WardenEntity) return "warden";
        return null;
    }

    private static String bossName(String t) {
        return switch (t) {
            case "ender_dragon" -> "the Ender Dragon";
            case "wither" -> "the Wither";
            default -> "the Warden";
        };
    }

    /** Called for every damage a living entity takes. Attacker is the shooter for projectiles. */
    public static void onDamage(LivingEntity entity, DamageSource source) {
        if (bossType(entity) == null) return;
        if (source.getAttacker() instanceof ServerPlayerEntity p) FIGHTS.record(entity.getUuid(), p.getUuid());
    }

    public static void onDeath(LivingEntity entity, DamageSource source) {
        Honor h = Honor.I;
        if (h == null) return;
        String boss = bossType(entity);
        if (boss != null) {
            for (UUID id : FIGHTS.end(entity.getUuid())) payBoss(h, id, boss);
            return;
        }
        if (!(source.getAttacker() instanceof ServerPlayerEntity killer)) return;
        if (AutoRules.villagerPenalty(entity instanceof VillagerEntity || entity instanceof WanderingTraderEntity, true)) {
            PlayerRecord r = rec(h, killer);
            if (r != null && h.config.villagerKillPenalty != 0) {
                give(h, killer, r, -Math.abs(h.config.villagerKillPenalty), "Killed a villager");
            }
            return;
        }
        if (entity instanceof Tameable t) {
            var ref = t.getOwnerReference();
            UUID owner = ref == null ? null : ref.getUuid();
            if (AutoRules.petPenalty(killer.getUuid(), owner)) {
                PlayerRecord r = rec(h, killer);
                if (r != null && h.config.petKillPenalty != 0) {
                    h.change(r.uuid, r.name, -Math.abs(h.config.petKillPenalty), "Killed another player's pet", "auto", killer,
                            h.nameOf(owner.toString()), 0);
                    h.markDirty();
                }
            }
        }
    }

    private static void payBoss(Honor h, UUID id, String boss) {
        PlayerRecord r = h.data.players.get(id.toString());
        if (r == null || h.isJudge(r.uuid) || h.config.bossKillHonor == 0) return;
        AutoRules.rollDay(r, AutoRules.todayKey(LocalDate.now()));
        if (!AutoRules.claimBoss(r, boss)) return;
        h.change(r.uuid, r.name, h.config.bossKillHonor, "Helped kill " + bossName(boss), "auto", null, "", 0);
        h.markDirty();
    }
}
