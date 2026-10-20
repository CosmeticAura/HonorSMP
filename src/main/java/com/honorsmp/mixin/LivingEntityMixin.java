package com.honorsmp.mixin;

import com.honorsmp.Honor;
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
}
