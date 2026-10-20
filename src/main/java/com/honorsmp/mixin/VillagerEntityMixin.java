package com.honorsmp.mixin;

import com.honorsmp.Honor;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.TradeOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin {
    @Inject(method = "prepareOffersFor", at = @At("TAIL"))
    private void honorsmp$prices(PlayerEntity player, CallbackInfo ci) {
        if (player instanceof ServerPlayerEntity sp && Honor.I != null) {
            for (TradeOffer offer : ((VillagerEntity) (Object) this).getOffers()) {
                int change = Honor.I.priceChange(sp, offer.getOriginalFirstBuyItem().getCount());
                if (change != 0) offer.increaseSpecialPrice(change);
            }
        }
    }
}
