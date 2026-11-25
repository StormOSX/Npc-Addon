package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.ClientRecruits;
import dev.storm.npcaddon.group.RecruitType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Server -> client: the hireable recruits, the player's gold and squads, and seconds until they may hire again. */
public record RecruitMenuPacket(List<Entry> entries, int gold, List<String> squads, int cooldown) {
    private static final int MAX = 64;

    public record Entry(String name, RecruitType type) {}

    public static void encode(RecruitMenuPacket p, FriendlyByteBuf buf) {
        int n = Math.min(p.entries.size(), MAX);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(p.entries.get(i).name, 64);
            buf.writeEnum(p.entries.get(i).type);
        }
        buf.writeVarInt(p.gold);
        int s = Math.min(p.squads.size(), MAX);
        buf.writeVarInt(s);
        for (int i = 0; i < s; i++) buf.writeUtf(p.squads.get(i), 64);
        buf.writeVarInt(p.cooldown);
    }

    public static RecruitMenuPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), MAX);
        List<Entry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) entries.add(new Entry(buf.readUtf(64), buf.readEnum(RecruitType.class)));
        int gold = buf.readVarInt();
        int s = Math.min(buf.readVarInt(), MAX);
        List<String> squads = new ArrayList<>(s);
        for (int i = 0; i < s; i++) squads.add(buf.readUtf(64));
        return new RecruitMenuPacket(entries, gold, squads, buf.readVarInt());
    }

    public static void handle(RecruitMenuPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRecruits.set(p)));
        ctx.setPacketHandled(true);
    }
}
