package com.honorsmp;

import io.netty.buffer.Unpooled;
import java.util.EnumSet;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;

/** Builds a tab-list "listed" update packet without needing access to the packet's private fields. */
public final class TabPackets {
    private TabPackets() {}

    public static PlayerListS2CPacket listed(ServerPlayerEntity p, boolean listed) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), p.getEntityWorld().getRegistryManager());
        buf.writeEnumSet(EnumSet.of(PlayerListS2CPacket.Action.UPDATE_LISTED), PlayerListS2CPacket.Action.class);
        buf.writeVarInt(1);
        buf.writeUuid(p.getUuid());
        buf.writeBoolean(listed);
        return PlayerListS2CPacket.CODEC.decode(buf);
    }
}
