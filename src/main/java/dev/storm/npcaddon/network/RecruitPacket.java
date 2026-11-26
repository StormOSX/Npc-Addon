package dev.storm.npcaddon.network;

import dev.storm.npcaddon.group.RecruitType;
import dev.storm.npcaddon.group.Recruits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: recruiting actions. The server checks everything.
 *
 * @param entity the recruit to dismiss
 * @param name the catalog recruit to hire, save or delete
 * @param squad the squad a hire joins
 * @param type the type to save a recruit as
 */
public record RecruitPacket(Action action, int entity, String name, String squad, RecruitType type) {
    public enum Action { REFRESH, HIRE, DISMISS, SAVE, DELETE, BUY_HORN }

    public static RecruitPacket refresh() {
        return new RecruitPacket(Action.REFRESH, 0, "", "", RecruitType.MELEE);
    }

    public static void encode(RecruitPacket p, FriendlyByteBuf buf) {
        buf.writeEnum(p.action);
        buf.writeVarInt(p.entity);
        buf.writeUtf(p.name, 32);
        buf.writeUtf(p.squad, 32);
        buf.writeEnum(p.type);
    }

    public static RecruitPacket decode(FriendlyByteBuf buf) {
        return new RecruitPacket(buf.readEnum(Action.class), buf.readVarInt(), buf.readUtf(32), buf.readUtf(32),
            buf.readEnum(RecruitType.class));
    }

    public static void handle(RecruitPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            switch (p.action) {
                case HIRE -> Recruits.hire(player, p.name, p.squad);
                case DISMISS -> Recruits.dismiss(player, p.entity);
                case SAVE -> Recruits.saveAimed(player, p.name, p.type);
                case DELETE -> Recruits.delete(player, p.name);
                case BUY_HORN -> Recruits.buyHorn(player);
                default -> {}
            }
            if (p.action != Action.DISMISS) Recruits.sendMenu(player);
        });
        ctx.setPacketHandled(true);
    }
}
