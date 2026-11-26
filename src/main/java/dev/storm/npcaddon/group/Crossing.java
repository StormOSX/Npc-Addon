package dev.storm.npcaddon.group;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * NPCs following a player come along when that player changes dimension, with the mount they are riding. Siege weapons
 * stay behind. The NPCs are held where they are for a moment, until the player's surroundings have loaded.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Crossing {
    private static final int SETTLE_TICKS = 10;
    private static final int TIMEOUT_TICKS = 20 * 15;

    private static final class Pending {
        final ServerLevel from;
        final UUID player;
        final GroupMover.Follower order;
        int ticks;

        Pending(ServerLevel from, UUID player, GroupMover.Follower order) {
            this.from = from;
            this.player = player;
            this.order = order;
        }
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private Crossing() {}

    @SubscribeEvent
    public static void changed(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        for (GroupMover.Follower order : GroupMover.followersOf(p.getUUID())) {
            for (ServerLevel level : p.server.getAllLevels()) {
                if (level != p.level() && level.getEntity(order.npc()) instanceof EntityNPCInterface npc
                        && npc.isAlive()) {
                    GroupMover.cancel(npc);
                    npc.setTarget(null);
                    hold(level, npc);
                    PENDING.add(new Pending(level, p.getUUID(), order));
                    break;
                }
            }
        }
    }

    /** Keeps the NPC's chunk loaded although the player has left. The ticket runs out by itself. */
    private static void hold(ServerLevel level, Entity npc) {
        level.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, new ChunkPos(npc.blockPosition()), 2,
            npc.getId());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        MinecraftServer server = e.getServer();
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending w = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(w.player);
            if (p == null || !(w.from.getEntity(w.order.npc()) instanceof EntityNPCInterface npc) || !npc.isAlive()
                    || ++w.ticks > TIMEOUT_TICKS) {
                it.remove();
                continue;
            }
            hold(w.from, npc);
            if (w.ticks < SETTLE_TICKS || !p.serverLevel().isPositionEntityTicking(p.blockPosition())) continue;
            it.remove();
            bring(npc, p, w.order);
        }
    }

    private static void bring(EntityNPCInterface npc, ServerPlayer p, GroupMover.Follower order) {
        ServerLevel to = p.serverLevel();
        Entity mount = Mounts.isMounted(npc) ? npc.getVehicle() : null;
        if (to != npc.level()) {
            Vec3 at = Arrivals.nearSpot(p, mount != null);
            if (at == null) at = p.position();
            if (mount != null) {
                Mounts.dismount(npc);
                mount = move(mount, to, at);
            }
            if (!(move(npc, to, at) instanceof EntityNPCInterface moved)) return;
            npc = moved;
            npc.ais.setStartPos(npc.blockPosition());
            if (mount != null) Mounts.mount(npc, mount);
        }
        GroupMover.follow(npc, p, order.slot());
        if (order.yaw() != null) GroupMover.face(npc, order.yaw());
        else GroupMover.faceLeader(npc, p);
    }

    /** Teleporting between dimensions replaces the entity; returns the new one, or null if it failed. */
    private static Entity move(Entity e, ServerLevel to, Vec3 at) {
        UUID id = e.getUUID();
        // So it is not carried straight back by the portal it appears beside.
        e.setPortalCooldown();
        if (!e.teleportTo(to, at.x, at.y, at.z, Set.of(), e.getYRot(), 0)) return null;
        Entity moved = to.getEntity(id);
        if (moved != null) moved.setPortalCooldown();
        return moved;
    }
}
