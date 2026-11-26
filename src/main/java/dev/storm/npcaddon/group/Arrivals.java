package dev.storm.npcaddon.group;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * New recruits appear some way off, preferably behind the player, and make their own way over. One that stops getting
 * closer is brought nearby so it cannot be lost on the way.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Arrivals {
    private static final int[] DISTANCES = {72, 54, 39, 27};
    private static final int[] FALLBACK_DISTANCES = {8, 6, 4};
    private static final int[] TURNS = {180, 135, 225, 90, 270, 45, 315, 0};
    private static final double ARRIVED = 6;
    /** Ticks without getting closer before the recruit is brought over. */
    private static final int PATIENCE = 20 * 15;

    private static final class Walk {
        final ServerLevel level;
        final UUID player;
        double best = Double.MAX_VALUE;
        int waited;

        Walk(ServerLevel level, UUID player) {
            this.level = level;
            this.player = player;
        }
    }

    private static final Map<UUID, Walk> WALKS = new HashMap<>();
    private static int clock;

    private Arrivals() {}

    /** A place far from the player where a recruit (on a horse or on foot) fits, or null. */
    static Vec3 farSpot(ServerPlayer p, boolean mounted) {
        return spot(p, mounted, DISTANCES);
    }

    private static Vec3 spot(ServerPlayer p, boolean mounted, int[] distances) {
        ServerLevel level = p.serverLevel();
        double half = mounted ? 0.75 : 0.35;
        double height = mounted ? 2.8 : 1.95;
        int feet = p.blockPosition().getY();
        for (int distance : distances) {
            for (int turn : TURNS) {
                double yaw = Math.toRadians(p.getYRot() + turn);
                int x = (int) Math.floor(p.getX() - Math.sin(yaw) * distance);
                int z = (int) Math.floor(p.getZ() + Math.cos(yaw) * distance);
                if (!level.isPositionEntityTicking(new BlockPos(x, feet, z))) continue;
                for (int y = feet + 10; y >= feet - 14; y--) {
                    BlockPos under = new BlockPos(x, y - 1, z);
                    BlockState ground = level.getBlockState(under);
                    // Solid ground, not a treetop.
                    if (ground.getCollisionShape(level, under).isEmpty() || ground.is(BlockTags.LEAVES)) continue;
                    AABB room = new AABB(x + 0.5 - half, y, z + 0.5 - half, x + 0.5 + half, y + height, z + 0.5 + half);
                    if (level.noCollision(room) && !level.containsAnyLiquid(room)
                            && level.getBlockStates(room).noneMatch(b -> b.is(BlockTags.PORTALS))) {
                        return new Vec3(x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    /** A place at one of these distances from the player, for someone on foot or mounted, or null. */
    static Vec3 spotAt(ServerPlayer p, boolean mounted, int[] distances) {
        return spot(p, mounted, distances);
    }

    /** A place right by the player where a recruit or follower fits, or null. */
    static Vec3 nearSpot(ServerPlayer p, boolean mounted) {
        return spot(p, mounted, FALLBACK_DISTANCES);
    }

    static void track(EntityNPCInterface npc, ServerPlayer p) {
        WALKS.put(npc.getUUID(), new Walk(p.serverLevel(), p.getUUID()));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 20 != 0 || WALKS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Walk>> it = WALKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Walk> en = it.next();
            Walk w = en.getValue();
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(w.player);
            Entity ent = w.level.getEntity(en.getKey());
            // Over when either is gone, or the recruit has been given another order meanwhile.
            if (p == null || p.level() != w.level || !(ent instanceof EntityNPCInterface npc) || !npc.isAlive()
                    || !GroupMover.isFollowing(npc, w.player)) {
                it.remove();
                continue;
            }
            double distance = npc.distanceTo(p);
            if (distance <= ARRIVED) {
                p.sendSystemMessage(Component.literal("<" + npc.getName().getString() + "> Reporting for duty"));
                it.remove();
            } else if (distance < w.best - 2) {
                w.best = distance;
                w.waited = 0;
            } else if (npc.getTarget() == null && (w.waited += 20) >= PATIENCE) {
                boolean mounted = Mounts.isMounted(npc);
                Vec3 near = nearSpot(p, mounted);
                if (near == null) near = p.position();
                Entity moved = mounted ? npc.getVehicle() : npc;
                moved.teleportTo(near.x, near.y, near.z);
                w.waited = 0;
                w.best = Double.MAX_VALUE;
            }
        }
    }
}
