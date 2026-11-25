package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.RecruitScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: open the window for one of the player's recruits. */
public record ManageRecruitPacket(int entity, String name, String squad) {
    public static void encode(ManageRecruitPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeUtf(p.name, 64);
        buf.writeUtf(p.squad, 64);
    }

    public static ManageRecruitPacket decode(FriendlyByteBuf buf) {
        return new ManageRecruitPacket(buf.readVarInt(), buf.readUtf(64), buf.readUtf(64));
    }

    public static void handle(ManageRecruitPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RecruitScreen.open(p)));
        ctx.setPacketHandled(true);
    }
}
