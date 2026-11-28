package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.TerritoryScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: the territory claims the player manages.
 *
 * @param open the claim to open the window on, -1 to open it on any, 0 to only refresh an open window
 */
public record TerritoryPacket(int open, List<Entry> entries) {
    private static final int MAX = 256;

    public record Entry(int id, String faction, int radius, String dim, int x, int y, int z, String owner) {}

    public static void encode(TerritoryPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.open);
        // The claim to be shown always fits in.
        List<Entry> list = new ArrayList<>(p.entries);
        list.sort(java.util.Comparator.comparingInt(e -> e.id == p.open ? 0 : 1));
        int n = Math.min(list.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Entry e = list.get(i);
            buf.writeInt(e.id);
            buf.writeUtf(e.faction, 64);
            buf.writeVarInt(e.radius);
            buf.writeUtf(e.dim, 128);
            buf.writeInt(e.x);
            buf.writeInt(e.y);
            buf.writeInt(e.z);
            buf.writeUtf(e.owner, 64);
        }
    }

    public static TerritoryPacket decode(FriendlyByteBuf buf) {
        int open = buf.readInt();
        int n = Math.min(buf.readVarInt(), MAX);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readInt(), buf.readUtf(64), buf.readVarInt(), buf.readUtf(128), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readUtf(64)));
        }
        list.sort(java.util.Comparator.comparingInt(Entry::id));
        return new TerritoryPacket(open, list);
    }

    public static void handle(TerritoryPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TerritoryScreen.receive(p)));
        ctx.setPacketHandled(true);
    }
}
