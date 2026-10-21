package com.honorsmp.mixin;

import net.minecraft.advancement.criterion.CuredZombieVillagerCriterion;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CuredZombieVillagerCriterion.class)
public class CuredZombieVillagerCriterionMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/entity/mob/ZombieEntity;Lnet/minecraft/entity/passive/VillagerEntity;)V", at = @At("HEAD"))
    private void honorsmp$onTrigger(ServerPlayerEntity player, ZombieEntity zombie, VillagerEntity villager, CallbackInfo ci) {
        com.honorsmp.AutoHonor.onCure(player);
    }
}
