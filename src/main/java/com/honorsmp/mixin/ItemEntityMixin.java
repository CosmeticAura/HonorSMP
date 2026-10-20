package com.honorsmp.mixin;

import com.honorsmp.ItemFixer;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "onPlayerCollision", at = @At("HEAD"))
    private void honorsmp$fixOnPickup(PlayerEntity player, CallbackInfo ci) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack s = self.getStack();
        ItemStack f = ItemFixer.fix(s, sp);
        if (f != s) self.setStack(f);
    }
}
