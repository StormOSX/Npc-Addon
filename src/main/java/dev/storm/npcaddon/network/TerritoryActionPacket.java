package dev.storm.npcaddon.network;

import dev.storm.npcaddon.territory.Territory;
import dev.storm.npcaddon.territory.TerritoryData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: territory window actions. The server checks everything.
 *
 * @param value the direction (+1 or -1) of a radius or faction change
 */
public record TerritoryActionPacket(Action action, int claim, int value) {
    public enum Action { LIST, RADIUS, FACTION, REMOVE, SHOW }

    public static void encode(TerritoryActionPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeInt(p.claim);
        buf.writeInt(p.value);
    }

    public static TerritoryActionPacket decode(FriendlyByteBuf buf) {
        return new TerritoryActionPacket(buf.readEnum(Action.class), buf.readInt(), buf.readInt());
    }

    public static void handle(TerritoryActionPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            switch (p.action) {
                case LIST -> {
                    Territory.send(player, -1);
                    return;
                }
                case RADIUS -> Territory.changeRadius(player, p.claim, p.value);
                case FACTION -> Territory.changeFaction(player, p.claim, p.value);
                case REMOVE -> Territory.abandon(player, p.claim);
                case SHOW -> {
                    TerritoryData.Claim c = TerritoryData.get(player.server).claim(p.claim);
                    if (c != null && Territory.manages(player, c)) Territory.outline(player, c);
                }
            }
            Territory.send(player, 0);
        });
        ctx.setPacketHandled(true);
    }
}
