package com.honorsmp.mixin;

import net.minecraft.advancement.criterion.VillagerTradeCriterion;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerTradeCriterion.class)
public class VillagerTradeCriterionMixin {
    @Inject(method = "trigger(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/entity/passive/MerchantEntity;Lnet/minecraft/item/ItemStack;)V", at = @At("HEAD"))
    private void honorsmp$onTrigger(ServerPlayerEntity player, MerchantEntity merchant, ItemStack stack, CallbackInfo ci) {
        com.honorsmp.AutoHonor.onTrade(player);
    }
}
