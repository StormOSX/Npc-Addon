package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.RecruitScreen;
import dev.storm.npcaddon.group.RecruitType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: open the recruit window.
 *
 * @param squads the player's own squads
 * @param member the player's squad the NPC is already in (manage it), or empty (hire it)
 */
public record RecruitOfferPacket(int entity, String name, RecruitType type, int gold, List<String> squads,
        String member) {
    public static void encode(RecruitOfferPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeUtf(p.name, 64);
        buf.writeEnum(p.type);
        buf.writeVarInt(p.gold);
        int n = Math.min(p.squads.size(), 64);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) buf.writeUtf(p.squads.get(i), 64);
        buf.writeUtf(p.member, 64);
    }

    public static RecruitOfferPacket decode(FriendlyByteBuf buf) {
        int entity = buf.readVarInt();
        String name = buf.readUtf(64);
        RecruitType type = buf.readEnum(RecruitType.class);
        int gold = buf.readVarInt();
        int n = Math.min(buf.readVarInt(), 64);
        List<String> squads = new ArrayList<>(n);
        for (int i = 0; i < n; i++) squads.add(buf.readUtf(64));
        return new RecruitOfferPacket(entity, name, type, gold, squads, buf.readUtf(64));
    }

    public static void handle(RecruitOfferPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecruitScreen.open(p)));
        ctx.setPacketHandled(true);
    }
}
