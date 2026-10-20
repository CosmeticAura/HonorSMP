package com.honorsmp.mixin;

import com.honorsmp.ItemFixer;
import net.minecraft.screen.ForgingScreenHandler;
import net.minecraft.screen.AnvilScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
    @Inject(method = "updateResult", at = @At("TAIL"))
    private void honorsmp$capOutput(CallbackInfo ci) {
        ForgingScreenHandler self = (ForgingScreenHandler) (Object) this;
        ItemFixer.fixCaps(self.slots.get(self.getResultSlotIndex()).getStack());
    }
}
