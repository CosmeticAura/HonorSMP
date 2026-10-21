package com.honorsmp.mixin;

import net.minecraft.advancement.criterion.BredAnimalsCriterion;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BredAnimalsCriterion.class)
public class BredAnimalsCriterionMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/AnimalEntity;Lnet/minecraft/entity/passive/PassiveEntity;)V", at = @At("HEAD"))
    private void honorsmp$onTrigger(ServerPlayerEntity player, AnimalEntity parent, AnimalEntity other, PassiveEntity child, CallbackInfo ci) {
        com.honorsmp.AutoHonor.onBreed(player);
    }
}
