package dev.storm.npcaddon.network;

import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.faction.PlayerStandings;
import dev.storm.npcaddon.faction.Standing;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: set one player's standing with a faction, or a faction's default for new players. */
/** aw is the Ancient Warfare faction name when the target is an AW faction, otherwise empty (faction is then used). */
public record PlayerStandingPacket(Mode mode, String player, int faction, String aw, Standing standing) {
    public enum Mode { PLAYER, DEFAULT, DEFAULT_AND_APPLY }

    public static void encode(PlayerStandingPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.mode);
        buf.writeUtf(p.player, 16);
        buf.writeVarInt(p.faction);
        buf.writeUtf(p.aw, 64);
        buf.writeEnum(p.standing);
    }

    public static PlayerStandingPacket decode(FriendlyByteBuf buf) {
        return new PlayerStandingPacket(buf.readEnum(Mode.class), buf.readUtf(16), buf.readVarInt(), buf.readUtf(64),
            buf.readEnum(Standing.class));
    }

    public static void handle(PlayerStandingPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender == null || !sender.hasPermissions(2)) return;
            PlayerStandings.Result r;
            if (!p.aw.isEmpty() && p.mode != Mode.PLAYER) {
                sender.sendSystemMessage(Component.literal(
                    "Ancient Warfare default standings come from its datapack and cannot be changed here"));
                return;
            }
            if (p.mode == Mode.PLAYER) {
                ServerPlayer target = sender.server.getPlayerList().getPlayerByName(p.player);
                if (target == null) {
                    sender.sendSystemMessage(Component.literal("Player '" + p.player + "' is not online"));
                    return;
                }
                r = p.aw.isEmpty() ? PlayerStandings.setPlayer(target, p.faction, p.standing)
                    : AwRelations.setPlayer(target, p.aw, p.standing);
            } else {
                r = PlayerStandings.setDefault(sender.server, p.faction, p.standing, p.mode == Mode.DEFAULT_AND_APPLY);
            }
            sender.sendSystemMessage(Component.literal(r.message()));
        });
        ctx.setPacketHandled(true);
    }
}
