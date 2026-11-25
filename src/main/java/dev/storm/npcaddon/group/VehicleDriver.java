package dev.storm.npcaddon.group;

import dev.storm.npcaddon.aw.AwSiege;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.VehicleInputPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Steers one siege vehicle for its NPC driver. Vehicles have no pathfinding of their own, so the driver's path is used
 * as a list of waypoints. A vehicle that stops making progress backs off and tries again a few times. Call once a tick.
 */
final class VehicleDriver {
    private static final int REPLAN_TICKS = 40;
    private static final int PROGRESS_TICKS = 40;
    private static final int REVERSE_TICKS = 25;
    private static final int MAX_STUCK = 4;
    private static final double WAYPOINT_DIST = 2.0;
    private static final double STRAIGHT_DIST = 6.0;

    private Vec3 goal;
    private Path path;
    private int ticks;
    private Vec3 lastPos;
    private int reverse;
    private int stuck;

    private static final int RESEND_TICKS = 20;
    /** Vehicle -> last input sent to clients: forward, turn, tick. */
    private static final Map<UUID, float[]> SENT = new HashMap<>();

    /**
     * Applies driving input and passes it on to the players who see the vehicle. Without it their copy only slides to
     * each new position and the wheels barely turn.
     */
    private static void input(Entity vehicle, float forward, float turn) {
        if (vehicle == null) return;
        AwSiege.drive(vehicle, forward, turn);
        float now = vehicle.tickCount;
        float[] last = SENT.get(vehicle.getUUID());
        boolean idle = forward == 0 && turn == 0;
        if (last != null && last[0] == forward && last[1] == turn && (idle || now - last[2] < RESEND_TICKS)) return;
        SENT.put(vehicle.getUUID(), new float[]{forward, turn, now});
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> vehicle),
            new VehicleInputPacket(vehicle.getId(), forward, turn));
    }

    /** Forgets the current route (new order). */
    void reset() {
        goal = null;
        path = null;
        lastPos = null;
        ticks = 0;
        reverse = 0;
        stuck = 0;
    }

    static double flatDistance(Vec3 a, Vec3 b) {
        double x = a.x - b.x;
        double z = a.z - b.z;
        return Math.sqrt(x * x + z * z);
    }

    static void stop(Entity vehicle) {
        input(vehicle, 0, 0);
    }

    /** Turns on the spot towards a yaw. @return true once it faces that way */
    static boolean align(Entity vehicle, float yaw) {
        float diff = Mth.wrapDegrees(yaw - vehicle.getYRot());
        if (Math.abs(diff) < 4) {
            stop(vehicle);
            return true;
        }
        input(vehicle, 0, Mth.clamp(-diff / 4, -1, 1));
        return false;
    }

    static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** @return false when the vehicle cannot get there and has given up */
    boolean drive(EntityNPCInterface npc, Entity vehicle, Vec3 target) {
        // Vehicles have no step height of their own and would stop at every single block.
        vehicle.setMaxUpStep(1.0F);
        if (goal == null || goal.distanceToSqr(target) > 4) {
            goal = target;
            path = null;
            ticks = 0;
        }
        Vec3 pos = vehicle.position();
        if (path == null || ticks % REPLAN_TICKS == 0) {
            path = npc.getNavigation().createPath(BlockPos.containing(target), 1);
        }
        if (++ticks % PROGRESS_TICKS == 0) {
            if (lastPos != null && reverse == 0 && flatDistance(pos, lastPos) < 0.6) {
                if (++stuck >= MAX_STUCK) {
                    stop(vehicle);
                    return false;
                }
                reverse = REVERSE_TICKS;
            } else if (lastPos != null && flatDistance(pos, lastPos) > 3) {
                stuck = 0;
            }
            lastPos = pos;
        }
        if (reverse > 0) {
            reverse--;
            input(vehicle, hasGround(vehicle, -2) ? -1 : 0, stuck % 2 == 0 ? 1 : -1);
            return true;
        }
        Vec3 next = target;
        if (path != null && flatDistance(pos, target) > STRAIGHT_DIST) {
            while (!path.isDone() && flatDistance(pos, Vec3.atBottomCenterOf(path.getNextNodePos())) < WAYPOINT_DIST) {
                path.advance();
            }
            if (!path.isDone()) next = Vec3.atBottomCenterOf(path.getNextNodePos());
        }
        float diff = Mth.wrapDegrees(yawTo(pos, next) - vehicle.getYRot());
        float forward = Math.abs(diff) < 30 ? 1 : Math.abs(diff) < 75 ? 0.4f : 0;
        if (forward > 0 && !hasGround(vehicle, 2)) forward = 0;
        input(vehicle, forward, Mth.clamp(-diff / 4, -1, 1));
        return true;
    }

    /** Whether there is ground (no drop of more than two blocks) this far ahead of the vehicle (negative: behind). */
    private static boolean hasGround(Entity vehicle, double ahead) {
        double yaw = Math.toRadians(vehicle.getYRot());
        double x = vehicle.getX() - Math.sin(yaw) * ahead;
        double z = vehicle.getZ() + Math.cos(yaw) * ahead;
        double y = vehicle.getY();
        return !vehicle.level().noCollision(vehicle, new AABB(x - 0.4, y - 2.2, z - 0.4, x + 0.4, y - 0.05, z + 0.4));
    }
}
