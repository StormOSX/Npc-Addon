package dev.storm.npcaddon.network;

import dev.storm.npcaddon.faction.FactionRelations;
import dev.storm.npcaddon.faction.Relation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: set the relation between two factions. Everything is re-validated server side. */
public record SetRelationPacket(int a, int b, Relation relation) {

    public static void encode(SetRelationPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.a);
        buf.writeVarInt(p.b);
        buf.writeEnum(p.relation);
    }

    public static SetRelationPacket decode(FriendlyByteBuf buf) {
        return new SetRelationPacket(buf.readVarInt(), buf.readVarInt(), buf.readEnum(Relation.class));
    }

    public static void handle(SetRelationPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            boolean ok = FactionRelations.set(player.server, p.a, p.b, p.relation);
            player.sendSystemMessage(Component.literal(ok
                ? "Faction " + p.a + " <-> " + p.b + " set to " + p.relation
                : "Unknown faction id, or both ids are the same"));
        });
        ctx.setPacketHandled(true);
    }
}
