package dev.storm.npcaddon.network;

import dev.storm.npcaddon.faction.Membership;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Client -> server: join or leave a faction, or (operators) open or close one.
 *
 * @param points for OPEN: the points needed, negative to close
 */
public record AllegianceActionPacket(Action action, int faction, int points) {
    public enum Action { REFRESH, JOIN, LEAVE, OPEN }

    public static void encode(AllegianceActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeInt(p.faction);
        buf.writeInt(p.points);
    }

    public static AllegianceActionPacket decode(FriendlyByteBuf buf) {
        return new AllegianceActionPacket(buf.readEnum(Action.class), buf.readInt(), buf.readInt());
    }

    public static void handle(AllegianceActionPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            switch (p.action) {
                case JOIN -> Membership.join(player, p.faction);
                case LEAVE -> Membership.leave(player, true);
                case OPEN -> {
                    if (player.hasPermissions(2)) Membership.setOpen(player.server, p.faction, p.points);
                }
                default -> {}
            }
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), AllegiancePacket.build(player));
        });
        ctx.setPacketHandled(true);
    }
}
