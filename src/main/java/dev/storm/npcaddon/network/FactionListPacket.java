package dev.storm.npcaddon.network;

import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.client.ClientSquads;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IFaction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: every CustomNPCs faction (id, name, colour) and the online player names. */
public record FactionListPacket(List<Info> factions, List<String> players) {
    private static final int MAX = 512;

    /** A CustomNPCs faction (id, colour) or, when aw is set, an Ancient Warfare faction (id -1, its default standing). */
    public record Info(int id, String name, int color, boolean aw, int awDefault) {
        /** Stable identity across refreshes. */
        public String key() {
            return aw ? "a:" + name : "c:" + id;
        }
    }

    public static FactionListPacket build(MinecraftServer server) {
        List<Info> out = new ArrayList<>();
        for (IFaction f : NpcAPI.Instance().getFactions().list()) {
            if (out.size() >= MAX) break;
            String n = f.getName() == null ? "" : f.getName();
            out.add(new Info(f.getId(), n.length() > 40 ? n.substring(0, 40) : n, f.getColor(), false, 0));
        }
        for (String n : AwRelations.names()) {
            if (out.size() >= MAX) break;
            out.add(new Info(-1, n.length() > 40 ? n.substring(0, 40) : n, 0x7F8FA6, true, AwRelations.defaultStanding(n)));
        }
        List<String> names = new ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (names.size() >= MAX) break;
            names.add(p.getGameProfile().getName());
        }
        return new FactionListPacket(out, names);
    }

    public static void encode(FactionListPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.factions.size());
        for (Info i : p.factions) {
            buf.writeVarInt(i.id);
            buf.writeUtf(i.name, 64);
            buf.writeInt(i.color);
            buf.writeBoolean(i.aw);
            buf.writeInt(i.awDefault);
        }
        buf.writeVarInt(p.players.size());
        for (String n : p.players) buf.writeUtf(n, 16);
    }

    public static FactionListPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<Info> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) list.add(new Info(buf.readVarInt(), buf.readUtf(64), buf.readInt(), buf.readBoolean(), buf.readInt()));
        int m = Math.min(buf.readVarInt(), MAX);
        List<String> players = new ArrayList<>(m);
        for (int i = 0; i < m; i++) players.add(buf.readUtf(16));
        return new FactionListPacket(list, players);
    }

    public static void handle(FactionListPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ClientSquads.setFactions(p.factions, p.players)));
        ctx.setPacketHandled(true);
    }
}
