package com.honorsmp;

import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.FakePlayer;
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
            if (server.getTicks() % 40 == 0) {
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) ItemFixer.sweepPlayer(p);
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Cmds.register(dispatcher));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            Honor h = Honor.I;
            if (h == null) return;
            ServerPlayerEntity p = handler.getPlayer();
            PlayerRecord r = h.record(p);
            r.lastIp = Honor.ipOf(p);
            ItemFixer.sweepPlayer(p);
            Sidebar.forget(p);
            h.refreshPlayer(p);
            if (p.isAlive()) KeepStash.restore(p);
            h.updateTabVisibility();
            h.bounties.onLogin(p);
            h.fines.onLogin(p);
            h.donationBox.onLogin(p);
            h.civChest.onLogin(p);
            CivPerks.onLogin(h, p);
            if (h.isJudge(p)) Cases.deliverPending(h, p);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            Honor h = Honor.I;
            if (h == null) return;
            ServerPlayerEntity p = handler.getPlayer();
            Sidebar.forget(p);
            CivSafety.CHAT_MODE.remove(p.getUuid());
            h.treasury.closeIfViewer(p);
            h.bounties.closeFor(p);
            h.fines.closeFor(p);
            h.donations.closeFor(p);
            h.donationBox.closeFor(p);
            h.civChest.closeFor(p);
            Menu.closeFor(p);
            SignPrompt.cancel(p);
            h.clearAttributes(p);
            h.saveData();
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            Honor h = Honor.I;
            ItemStack st = player.getStackInHand(hand);
            if (h != null && player instanceof ServerPlayerEntity sp && Compass.isCompass(st)) Compass.onUse(h, sp, st);
            return ActionResult.PASS;
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (Honor.I != null) Honor.I.sieges.onEntityLoad(entity);
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> CivSafety.allowDamage(entity, source));
        net.fabricmc.fabric.api.message.v1.ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
                (message, sender, params) -> CivSafety.allowChat(sender, message.getSignedContent()));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> onDamage(entity, source, taken));
        ServerLivingEntityEvents.AFTER_DEATH.register(HonorSMP::onDeath);
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (player instanceof ServerPlayerEntity sp && !(player instanceof FakePlayer)) AutoHonor.onHarvest(sp, state);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((old, now, alive) -> {
            if (Honor.I != null) { Honor.I.combat.clear(now.getUuid().toString()); Honor.I.refreshPlayer(now); KeepStash.restore(now); }
        });
    }

    private static void onDamage(LivingEntity entity, DamageSource source, float taken) {
        Honor h = Honor.I;
        if (h == null) return;
        AutoHonor.onDamage(entity, source);
        h.sieges.onDamage(entity, source);
        if (!(entity instanceof ServerPlayerEntity victim)) return;
        if (source.getAttacker() instanceof ServerPlayerEntity attacker && attacker != victim) {
            h.combat.record(victim.getUuid().toString(), System.currentTimeMillis());
            h.damage.add(new DamageEvent(System.currentTimeMillis(), attacker.getUuid().toString(), victim.getUuid().toString(), taken));
        }
    }

    private static void onDeath(LivingEntity entity, DamageSource source) {
        Honor h = Honor.I;
        if (h == null) return;
        AutoHonor.onDeath(entity, source);
        Entity attacker = source.getAttacker();
        if (entity instanceof ServerPlayerEntity dead) { h.treasury.closeIfViewer(dead); h.bounties.closeFor(dead); h.fines.closeFor(dead); h.donations.closeFor(dead); h.donationBox.closeFor(dead); h.civChest.closeFor(dead); Menu.closeFor(dead); SignPrompt.cancel(dead); }
        if (!(attacker instanceof ServerPlayerEntity killer) || attacker == entity) {
            if (entity instanceof ServerPlayerEntity v) h.record(v).secondsSinceDeath = 0;
            return;
        }
        if (entity instanceof ServerPlayerEntity victim) h.onPlayerKill(killer, victim);
    }
}
