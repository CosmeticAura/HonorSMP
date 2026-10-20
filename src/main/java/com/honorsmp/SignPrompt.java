package com.honorsmp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.block.Blocks;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Vanilla sign editor through packets: a fake sign is shown to the player only (block update packet),
 * the editor is opened with a SignEditorOpenS2CPacket and the answer is caught in
 * ServerPlayNetworkHandlerMixin before vanilla sees it. The real block is restored afterwards.
 */
public final class SignPrompt {
    private SignPrompt() {}

    private record Pending(ServerWorld world, BlockPos pos, Consumer<String[]> callback) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    /** Closes the player's current screen, shows a sign editor, calls back on the server thread with the 4 lines. */
    public static void ask(ServerPlayerEntity p, Consumer<String[]> callback) {
        cancel(p);
        if (p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();
        ServerWorld w = (ServerWorld) p.getEntityWorld();
        BlockPos pos = p.getBlockPos().up(2);
        if (pos.getY() >= w.getTopYInclusive()) pos = p.getBlockPos().down(2);
        PENDING.put(p.getUuid(), new Pending(w, pos, callback));
        p.networkHandler.sendPacket(new BlockUpdateS2CPacket(pos, Blocks.OAK_SIGN.getDefaultState()));
        p.networkHandler.sendPacket(new SignEditorOpenS2CPacket(pos, true));
    }

    /** Full reset: drop every pending sign prompt. */
    public static void clearAll() {
        PENDING.clear();
    }

    /** Netty thread: does this sign packet belong to a prompt? */
    public static boolean expects(ServerPlayerEntity p, BlockPos pos) {
        Pending pd = PENDING.get(p.getUuid());
        return pd != null && pd.pos.equals(pos);
    }

    /** Called (after expects() was true) with the typed lines; runs the callback on the server thread. */
    public static void receive(ServerPlayerEntity p, String[] lines) {
        Pending pd = PENDING.remove(p.getUuid());
        if (pd == null) return;
        Honor h = Honor.I;
        Runnable r = () -> {
            restore(p, pd);
            try {
                pd.callback.accept(lines);
            } catch (Exception e) {
                Honor.LOG.error("Sign prompt callback failed", e);
            }
        };
        if (h != null) h.server.execute(r);
        else r.run();
    }

    private static void restore(ServerPlayerEntity p, Pending pd) {
        if (p.isRemoved() || p.networkHandler == null) return;
        p.networkHandler.sendPacket(new BlockUpdateS2CPacket(pd.world, pd.pos));
    }

    /** Drops a pending prompt and restores the real block (disconnect, stop, new prompt). */
    public static void cancel(ServerPlayerEntity p) {
        Pending pd = PENDING.remove(p.getUuid());
        if (pd != null) restore(p, pd);
    }
}
