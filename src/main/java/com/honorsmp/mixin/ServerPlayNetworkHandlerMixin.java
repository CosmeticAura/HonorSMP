package com.honorsmp.mixin;

import com.honorsmp.SignPrompt;
import net.minecraft.network.packet.c2s.play.UpdateSignC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public class ServerPlayNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    /** Catches the answer to a SignPrompt before vanilla looks for a (non-existent) sign block entity. */
    @Inject(method = "onUpdateSign", at = @At("HEAD"), cancellable = true)
    private void honorsmp$signPrompt(UpdateSignC2SPacket packet, CallbackInfo ci) {
        if (player != null && SignPrompt.expects(player, packet.getPos())) {
            ci.cancel();
            SignPrompt.receive(player, packet.getText());
        }
    }
}
