package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.ClientShields;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: an NPC in the second rank of a shield wall holds its shield angled overhead, or stops. */
public record ShieldPosePacket(int entity, boolean raised) {
    public static void encode(ShieldPosePacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeBoolean(p.raised);
    }

    public static ShieldPosePacket decode(FriendlyByteBuf buf) {
        return new ShieldPosePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ShieldPosePacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
            () -> () -> ClientShields.set(p.entity, p.raised)));
        ctx.setPacketHandled(true);
    }
}
