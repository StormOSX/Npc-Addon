package dev.storm.npcaddon.network;

import dev.storm.npcaddon.group.WandController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: the player clicked with the command wand (right = use key, otherwise attack key; ctrl = Ctrl held). */
public record WandPacket(boolean right, boolean ctrl) {
    public static void encode(WandPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.right);
        buf.writeBoolean(p.ctrl);
    }

    public static WandPacket decode(FriendlyByteBuf buf) {
        return new WandPacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(WandPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) WandController.click(player, p.right, p.ctrl);
        });
        ctx.setPacketHandled(true);
    }
}
