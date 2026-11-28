package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.ClientSquads;
import dev.storm.npcaddon.group.GroupData;
import dev.storm.npcaddon.group.GroupMover;
import dev.storm.npcaddon.group.Morale;
import dev.storm.npcaddon.group.Veterancy;
import dev.storm.npcaddon.group.Recruits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: every squad with its members' name, health and current order. */
public record SquadListPacket(List<Entry> entries) {
    private static final int MAX_MEMBERS = 48;

    public record Member(String name, float health, float maxHealth, String state, boolean medic, int rank) {}

    public record Entry(String name, int total, int loaded, List<Member> members, String stance, int retreat, int morale) {}

    private static String clip(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** The squads this player may command. */
    public static SquadListPacket build(ServerPlayer player) {
        MinecraftServer server = player.server;
        GroupData data = GroupData.get(server.overworld());
        List<Entry> out = new ArrayList<>();
        for (String name : data.names()) {
            if (!Recruits.canUse(player, name)) continue;
            int loaded = 0;
            List<Member> members = new ArrayList<>();
            for (UUID id : data.members(name)) {
                EntityNPCInterface npc = null;
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof EntityNPCInterface n && n.isAlive()) {
                        npc = n;
                        break;
                    }
                }
                if (npc != null) {
                    loaded++;
                    members.add(new Member(clip(npc.getName().getString(), 32), npc.getHealth(), npc.getMaxHealth(),
                        GroupMover.describe(npc), data.isMedic(id), Veterancy.rank(npc)));
                } else {
                    members.add(new Member("(not loaded)", 0, 0, "Unloaded", data.isMedic(id), 0));
                }
            }
            out.add(new Entry(name, data.members(name).size(), loaded, members, data.stance(name).key,
                data.retreat(name), Morale.value(server, name)));
        }
        return new SquadListPacket(out);
    }

    public static void encode(SquadListPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            buf.writeUtf(e.name, 64);
            buf.writeVarInt(e.total);
            buf.writeVarInt(e.loaded);
            buf.writeUtf(e.stance, 16);
            buf.writeVarInt(e.retreat);
            buf.writeInt(e.morale);
            int n = Math.min(e.members.size(), MAX_MEMBERS);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                Member m = e.members.get(i);
                buf.writeUtf(m.name, 64);
                buf.writeFloat(m.health);
                buf.writeFloat(m.maxHealth);
                buf.writeUtf(m.state, 16);
                buf.writeBoolean(m.medic);
                buf.writeByte(m.rank);
            }
        }
    }

    public static SquadListPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 256);
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(64);
            int total = buf.readVarInt();
            int loaded = buf.readVarInt();
            String stance = buf.readUtf(16);
            int retreat = buf.readVarInt();
            int morale = buf.readInt();
            int count = Math.min(buf.readVarInt(), MAX_MEMBERS);
            List<Member> members = new ArrayList<>(count);
            for (int j = 0; j < count; j++) {
                members.add(new Member(buf.readUtf(64), buf.readFloat(), buf.readFloat(), buf.readUtf(16),
                    buf.readBoolean(), buf.readByte()));
            }
            list.add(new Entry(name, total, loaded, members, stance, retreat, morale));
        }
        return new SquadListPacket(list);
    }

    public static void handle(SquadListPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientSquads.set(p.entries)));
        ctx.setPacketHandled(true);
    }
}
