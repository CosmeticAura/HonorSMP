package com.honorsmp.mixin;

import com.honorsmp.ItemFixer;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {
    @Inject(method = "onButtonClick", at = @At("RETURN"))
    private void honorsmp$capEnchanted(PlayerEntity player, int id, CallbackInfoReturnable<Boolean> cir) {
        ScreenHandler self = (ScreenHandler) (Object) this;
        ItemFixer.fixCaps(self.slots.get(0).getStack());
    }
}
