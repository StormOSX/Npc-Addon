package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.ClientVehicles;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the input an NPC driver is giving a siege vehicle. */
public record VehicleInputPacket(int entity, float forward, float turn) {
    public static void encode(VehicleInputPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entity);
        buf.writeFloat(p.forward);
        buf.writeFloat(p.turn);
    }

    public static VehicleInputPacket decode(FriendlyByteBuf buf) {
        return new VehicleInputPacket(buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(VehicleInputPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientVehicles.apply(p)));
        ctx.setPacketHandled(true);
    }
}
