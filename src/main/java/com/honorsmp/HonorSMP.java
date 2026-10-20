package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LockableContainerBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class HonorSMP implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Honor.I = new Honor(server);
            Honor.I.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (Honor.I != null) Honor.I.stop();
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (Honor.I != null) Honor.I.tick();
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Cmds.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Honor h = Honor.I;
            if (h == null) return;
            ServerPlayerEntity p = handler.getPlayer();
            PlayerRecord r = h.record(p);
            r.lastIp = Honor.ipOf(p);
            h.refreshPlayer(p);
            h.updateTabVisibility();
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Honor h = Honor.I;
            if (h == null) return;
            ServerPlayerEntity p = handler.getPlayer();
            h.lastClaim.remove(p.getUuid());
            h.clearAttributes(p);
            h.saveData();
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> onDamage(entity, source, taken));
        ServerLivingEntityEvents.AFTER_DEATH.register(HonorSMP::onDeath);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayerEntity sp && Honor.I != null) Honor.I.onBlockChanged(sp, world, pos);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (player instanceof ServerPlayerEntity sp && Honor.I != null && !world.isClient()) {
                BlockEntity be = world.getBlockEntity(hit.getBlockPos());
                if (be instanceof LockableContainerBlockEntity && !sp.shouldCancelInteraction()) {
                    Honor.I.onContainerOpened(sp, world, hit.getBlockPos());
                }
            }
            return ActionResult.PASS;
        });
        EntitySleepEvents.ALLOW_SLEEPING.register((player, pos) -> {
            Honor h = Honor.I;
            if (h == null || !(player instanceof ServerPlayerEntity sp)) return null;
            if (h.tier(sp).isDishonored()) {
                Civ c = h.civAt(sp.getEntityWorld(), pos);
                if (c != null && h.civSide(c) == Side.HONORABLE) {
                    return new PlayerEntity.SleepFailureReason(Text.literal("You cannot sleep inside the land of an honorable civilization.").formatted(Formatting.RED));
                }
            }
            return null;
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, now, alive) -> {
            if (Honor.I != null) Honor.I.refreshPlayer(now);
        });
    }

    private static void onDamage(LivingEntity entity, DamageSource source, float taken) {
        Honor h = Honor.I;
        if (h == null || !(entity instanceof ServerPlayerEntity victim)) return;
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
            h.damage.add(new DamageEvent(System.currentTimeMillis(), attacker.getUuid().toString(), victim.getUuid().toString(), taken));
        }
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
        Honor h = Honor.I;
        if (h == null) return;
        Entity attacker = source.getAttacker();
        if (!(attacker instanceof ServerPlayerEntity killer) || attacker == entity) {
            if (entity instanceof ServerPlayerEntity v) h.record(v).secondsSinceDeath = 0;
            return;
        }
        if (entity instanceof VillagerEntity || entity instanceof IronGolemEntity) {
            Civ c = h.civAt(entity.getEntityWorld(), entity.getBlockPos());
            if (c != null) {
                PlayerRecord r = h.record(killer);
                h.change(r.uuid, r.name, h.config.killVillagerOrGolemInClaim,
                        "Killed a " + (entity instanceof VillagerEntity ? "villager" : "iron golem") + " in " + c.name, "auto", killer, "", 0);
            }
            return;
        }
        if (entity instanceof ServerPlayerEntity victim) h.onPlayerKill(killer, victim);
    }
}
