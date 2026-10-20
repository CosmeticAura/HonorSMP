package com.honorsmp;

import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

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
            Sidebar.forget(p);
            h.refreshPlayer(p);
            h.updateTabVisibility();
            Taxes.onLogin(h, p);
            h.bounties.onLogin(p);
            if (h.isJudge(p)) Cases.deliverPending(h, p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Honor h = Honor.I;
            if (h == null) return;
            ServerPlayerEntity p = handler.getPlayer();
            Sidebar.forget(p);
            h.treasury.closeIfViewer(p);
            h.bounties.closeFor(p);
            h.clearAttributes(p);
            h.saveData();
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            Honor h = Honor.I;
            ItemStack st = player.getStackInHand(hand);
            if (h != null && player instanceof ServerPlayerEntity sp && Compass.isCompass(st)) Compass.onUse(h, sp, st);
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> onDamage(entity, source, taken));
        ServerLivingEntityEvents.AFTER_DEATH.register(HonorSMP::onDeath);
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
        if (entity instanceof ServerPlayerEntity dead) { h.treasury.closeIfViewer(dead); h.bounties.closeFor(dead); }
        if (!(attacker instanceof ServerPlayerEntity killer) || attacker == entity) {
            if (entity instanceof ServerPlayerEntity v) h.record(v).secondsSinceDeath = 0;
            return;
        }
        if (entity instanceof ServerPlayerEntity victim) h.onPlayerKill(killer, victim);
    }
}
