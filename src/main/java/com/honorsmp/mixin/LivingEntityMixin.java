package com.honorsmp.mixin;

import com.honorsmp.CivSafety;
import com.honorsmp.Honor;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @ModifyVariable(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), argsOnly = true)
    private StatusEffectInstance honorsmp$scaleDuration(StatusEffectInstance effect) {
        if ((Object) this instanceof ServerPlayerEntity sp && Honor.I != null) {
            return Honor.I.scaleEffect(sp, effect);
        }
        return effect;
    }

    /** Harmful potion/cloud effects thrown by a civ-mate never land. */
    @Inject(method = "addStatusEffect(Lnet/minecraft/entity/effect/StatusEffectInstance;Lnet/minecraft/entity/Entity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void honorsmp$noFriendlyEffects(StatusEffectInstance effect, Entity source, CallbackInfoReturnable<Boolean> cir) {
        if (CivSafety.blockEffect((Entity) (Object) this, effect, source)) cir.setReturnValue(false);
    }
}
