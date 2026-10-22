package com.honorsmp.mixin;

import com.honorsmp.Honor;
import com.honorsmp.ItemFixer;
import com.honorsmp.KeepStash;
import java.util.OptionalInt;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {
    @Inject(method = "openHandledScreen", at = @At("RETURN"))
    private void honorsmp$sweepOnOpen(NamedScreenHandlerFactory factory, CallbackInfoReturnable<OptionalInt> cir) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (cir.getReturnValue().isPresent()) ItemFixer.sweepHandler(self.currentScreenHandler, self);
    }

    /** Before the death drops: take the kept hotbar/offhand and XP out so they cannot also drop. */
    @Inject(method = "onDeath", at = @At("HEAD"))
    private void honorsmp$keepOnDeath(net.minecraft.entity.damage.DamageSource source, CallbackInfo ci) {
        if (Honor.I != null) KeepStash.take((ServerPlayerEntity) (Object) this);
    }
}
