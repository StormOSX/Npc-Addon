package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.ClientAllegiance;
import dev.storm.npcaddon.faction.Membership;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: the factions the player can see in the Join tab (all of them for operators, otherwise the open
 * ones and their own) and which one they are in.
 */
public record AllegiancePacket(List<Entry> entries, int current) {
    private static final int MAX = 256;

    /** @param need points needed to join, or -1 when closed */
    public record Entry(int id, String name, int color, int need, int points) {}

    public static AllegiancePacket build(ServerPlayer p) {
        int current = Membership.factionOf(p.server, p.getUUID());
        List<Entry> out = new ArrayList<>();
        List<Faction> all = new ArrayList<>(FactionController.instance.factions.values());
        all.sort(Comparator.comparingInt(f -> f.id));
        for (Faction f : all) {
            int need = Membership.requirement(p.server, f.id);
            if (need < 0 && f.id != current && !p.hasPermissions(2)) continue;
            String name = f.name.length() > 48 ? f.name.substring(0, 48) : f.name;
            out.add(new Entry(f.id, name, f.color, need, Membership.points(p, f.id)));
        }
        return new AllegiancePacket(out, current);
    }

    public static void encode(AllegiancePacket p, FriendlyByteBuf buf) {
        int n = Math.min(p.entries.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = p.entries.get(i);
            buf.writeInt(e.id);
            buf.writeUtf(e.name, 64);
            buf.writeInt(e.color);
            buf.writeInt(e.need);
            buf.writeInt(e.points);
        }
        buf.writeInt(p.current);
    }

    public static AllegiancePacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readInt(), buf.readUtf(64), buf.readInt(), buf.readInt(), buf.readInt()));
        }
        return new AllegiancePacket(list, buf.readInt());
    }

    public static void handle(AllegiancePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAllegiance.set(p)));
        ctx.setPacketHandled(true);
    }
}
