package com.honorsmp;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Fines (Part D replaces this stub). */
public final class Fines {
    private Fines() {}

    /** Hook called by the case menu's Fine button. */
    public static void openFineFor(Honor h, ServerPlayerEntity judge, HonorData.Case c) {
        judge.sendMessage(Text.literal("Fines are not available yet.").formatted(Formatting.YELLOW));
    }
}
