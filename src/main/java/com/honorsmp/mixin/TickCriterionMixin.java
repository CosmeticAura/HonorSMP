package com.honorsmp.mixin;

import net.minecraft.advancement.criterion.TickCriterion;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.advancement.criterion.Criteria;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickCriterion.class)
public class TickCriterionMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/network/ServerPlayerEntity;)V", at = @At("HEAD"))
    private void honorsmp$onTrigger(ServerPlayerEntity player, CallbackInfo ci) {
        // TickCriterion is shared by several triggers; Raid fires HERO_OF_THE_VILLAGE once per player when a raid is won.
        if ((Object) this == Criteria.HERO_OF_THE_VILLAGE) com.honorsmp.AutoHonor.onRaidWon(player);
    }
}
