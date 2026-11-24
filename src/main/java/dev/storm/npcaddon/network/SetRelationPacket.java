package dev.storm.npcaddon.network;

import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.faction.FactionRelations;
import dev.storm.npcaddon.faction.Relation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: set the relation between two factions. Everything is re-validated server side. */
public record SetRelationPacket(int a, String awA, int b, String awB, Relation relation) {

    public static void encode(SetRelationPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.a);
        buf.writeUtf(p.awA, 64);
        buf.writeVarInt(p.b);
        buf.writeUtf(p.awB, 64);
        buf.writeEnum(p.relation);
    }

    public static SetRelationPacket decode(FriendlyByteBuf buf) {
        return new SetRelationPacket(buf.readVarInt(), buf.readUtf(64), buf.readVarInt(), buf.readUtf(64),
            buf.readEnum(Relation.class));
    }

    public static void handle(SetRelationPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            boolean aAw = !p.awA.isEmpty();
            boolean bAw = !p.awB.isEmpty();
            if (aAw && bAw) {
                player.sendSystemMessage(Component.literal("Ancient Warfare factions keep their own relations"));
                return;
            }
            if (aAw || bAw) {
                int custom = aAw ? p.b : p.a;
                String aw = aAw ? p.awA : p.awB;
                boolean ok = AwRelations.set(player.server, custom, aw, p.relation);
                player.sendSystemMessage(Component.literal(ok
                    ? "Faction " + custom + " <-> AW " + aw + " set to " + p.relation
                    : "Unknown faction, or Ancient Warfare is not installed"));
                return;
            }
            boolean ok = FactionRelations.set(player.server, p.a, p.b, p.relation);
            player.sendSystemMessage(Component.literal(ok
                ? "Faction " + p.a + " <-> " + p.b + " set to " + p.relation
                : "Unknown faction id, or both ids are the same"));
        });
        ctx.setPacketHandled(true);
    }
}
