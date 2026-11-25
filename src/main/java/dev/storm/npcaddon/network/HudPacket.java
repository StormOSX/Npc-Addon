package dev.storm.npcaddon.network;

import dev.storm.npcaddon.client.ClientHud;
import dev.storm.npcaddon.group.GroupData;
import dev.storm.npcaddon.group.GroupMover;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Server -> client: the player's selected squad for the on-screen HUD. An empty squad name clears the HUD. */
public record HudPacket(String squad, String stance, double wandRadius, List<Member> members) {
    private static final int MAX_MEMBERS = 48;

    public record Member(UUID id, String name, float health, float maxHealth, String state, String dim,
                         double x, double y, double z, boolean loaded) {}

    public static HudPacket empty() {
        return new HudPacket("", "", 0, List.of());
    }

    public static HudPacket build(MinecraftServer server, GroupData data, String squad, double wandRadius) {
        List<Member> out = new ArrayList<>();
        for (UUID id : data.members(squad)) {
            if (out.size() >= MAX_MEMBERS) break;
            EntityNPCInterface npc = null;
            ServerLevel where = null;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof EntityNPCInterface n && n.isAlive()) {
                    npc = n;
                    where = level;
                    break;
                }
            }
            if (npc == null) {
                out.add(new Member(id, "(not loaded)", 0, 0, "Unloaded", "", 0, 0, 0, false));
            } else {
                String name = npc.getName().getString();
                out.add(new Member(id, name.length() > 32 ? name.substring(0, 32) : name, npc.getHealth(),
                    npc.getMaxHealth(), GroupMover.describe(npc), where.dimension().location().toString(),
                    npc.getX(), npc.getY(), npc.getZ(), true));
            }
        }
        return new HudPacket(squad, data.stance(squad).key, wandRadius, out);
    }

    public static void encode(HudPacket p, FriendlyByteBuf buf) {
        buf.writeUtf(p.squad, 64);
        buf.writeUtf(p.stance, 16);
        buf.writeDouble(p.wandRadius);
        int n = Math.min(p.members.size(), MAX_MEMBERS);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            Member m = p.members.get(i);
            buf.writeUUID(m.id);
            buf.writeUtf(m.name, 64);
            buf.writeFloat(m.health);
            buf.writeFloat(m.maxHealth);
            buf.writeUtf(m.state, 16);
            buf.writeUtf(m.dim, 64);
            buf.writeDouble(m.x);
            buf.writeDouble(m.y);
            buf.writeDouble(m.z);
            buf.writeBoolean(m.loaded);
        }
    }

    public static HudPacket decode(FriendlyByteBuf buf) {
        String squad = buf.readUtf(64);
        String stance = buf.readUtf(16);
        double wandRadius = buf.readDouble();
        int n = Math.min(buf.readVarInt(), MAX_MEMBERS);
        List<Member> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Member(buf.readUUID(), buf.readUtf(64), buf.readFloat(), buf.readFloat(), buf.readUtf(16),
                buf.readUtf(64), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        }
        return new HudPacket(squad, stance, wandRadius, list);
    }

    public static void handle(HudPacket p, Supplier<NetworkEvent.Context> ctxSup) {
        NetworkEvent.Context ctx = ctxSup.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHud.set(p)));
        ctx.setPacketHandled(true);
    }
}
