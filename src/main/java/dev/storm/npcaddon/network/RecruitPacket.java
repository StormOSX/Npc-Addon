package dev.storm.npcaddon.network;

import dev.storm.npcaddon.group.Recruits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: hire this NPC into the named squad, or dismiss it. The server checks everything. */
public record RecruitPacket(int entity, boolean dismiss, String squad) {
    public static void encode(RecruitPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeBoolean(p.dismiss);
        buf.writeUtf(p.squad, 32);
    }

    public static RecruitPacket decode(FriendlyByteBuf buf) {
        return new RecruitPacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(32));
    }

    public static void handle(RecruitPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (p.dismiss) Recruits.dismiss(player, p.entity);
            else Recruits.hire(player, p.entity, p.squad);
        });
        ctx.setPacketHandled(true);
    }
}
