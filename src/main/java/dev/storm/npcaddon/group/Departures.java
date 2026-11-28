package dev.storm.npcaddon.group;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A dismissed hired recruit walks (or rides) away from the players and vanishes once it is well off, or after a while
 * if it cannot get away. Its remaining time is kept on the NPC, so it carries on after a reload.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Departures {
    private static final String LEAVING = "npcaddon_leaving";
    /** The direction (Minecraft yaw) the recruit is leaving in. */
    private static final String HEADING = "npcaddon_leaving_yaw";
    /** Directions to try, relative to the heading, when the way straight on is blocked. */
    private static final int[] TURNS = {0, 30, -30, 60, -60, 100, -100, 150, -150};
    private static final int[] STRIDES = {20, 12, 6};
    /** On a hired recruit that came with a horse: the horse's id. */
    static final String HORSE = "npcaddon_horse";
    private static final int MAX_TICKS = 20 * 25;
    private static final double GONE_DISTANCE = 48;
    private static final double SPEED = 1.1;
    private static final Set<UUID> WALKING = new HashSet<>();
    private static int clock;

    private Departures() {}

    static void start(EntityNPCInterface npc, Entity from) {
        npc.getPersistentData().putInt(LEAVING, MAX_TICKS);
        npc.getPersistentData().putFloat(HEADING, VehicleDriver.yawTo(from.position(), npc.position()));
        // CustomNPCs keeps an NPC standing while it thinks someone is talking to it.
        npc.interactingEntities.clear();
        npc.ais.returnToStart = false;
        npc.ais.setMovingType(0);
        npc.setTarget(null);
        WALKING.add(npc.getUUID());
        if (npc.level() instanceof ServerLevel level) stride(npc, level);
    }

    static boolean isLeaving(EntityNPCInterface npc) {
        return npc.getPersistentData().contains(LEAVING);
    }

    /** Whether this NPC came with the horse it is riding. */
    static boolean onOwnHorse(EntityNPCInterface npc) {
        CompoundTag pd = npc.getPersistentData();
        return npc.getVehicle() != null && pd.hasUUID(HORSE) && pd.getUUID(HORSE).equals(npc.getVehicle().getUUID());
    }

    /** It is on its way out and picks no more fights. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide
                && npc.getPersistentData().contains(LEAVING)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof EntityNPCInterface npc
                && npc.getPersistentData().contains(LEAVING)) {
            WALKING.add(npc.getUUID());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 10 != 0 || WALKING.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (UUID id : new ArrayList<>(WALKING)) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof EntityNPCInterface npc) {
                    walk(npc, level);
                    break;
                }
            }
        }
    }

    private static void walk(EntityNPCInterface npc, ServerLevel level) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(LEAVING)) {
            WALKING.remove(npc.getUUID());
            return;
        }
        int left = pd.getInt(LEAVING) - 10;
        pd.putInt(LEAVING, left);
        Player nearest = level.getNearestPlayer(npc, -1);
        if (left <= 0 || nearest == null || npc.distanceTo(nearest) >= GONE_DISTANCE || !npc.isAlive()) {
            vanish(npc, level);
            return;
        }
        npc.setLastHurtByMob(null);
        if (npc.getNavigation().isDone()) stride(npc, level);
    }

    /** Sets off on the next stretch, keeping to the same direction as far as the ground allows. */
    private static void stride(EntityNPCInterface npc, ServerLevel level) {
        float heading = npc.getPersistentData().getFloat(HEADING);
        for (int turn : TURNS) {
            double yaw = Math.toRadians(heading + turn);
            for (int distance : STRIDES) {
                BlockPos to = ground(level, Mth.floor(npc.getX() - Math.sin(yaw) * distance), npc.blockPosition().getY(),
                    Mth.floor(npc.getZ() + Math.cos(yaw) * distance));
                if (to == null) continue;
                Path path = npc.getNavigation().createPath(to, 1);
                // A path that barely moves the NPC means this way is shut.
                if (path == null || path.getEndNode() == null
                        || path.getEndNode().asBlockPos().distSqr(npc.blockPosition()) < 16) continue;
                npc.getNavigation().moveTo(path, SPEED);
                // Having turned aside, carry on that way instead of walking back into the obstacle.
                if (turn != 0) npc.getPersistentData().putFloat(HEADING, heading + turn);
                return;
            }
        }
    }

    /** The standing height nearest to y in this column, or null. */
    private static BlockPos ground(ServerLevel level, int x, int y, int z) {
        for (int step = 0; step <= 12; step++) {
            BlockPos at = new BlockPos(x, y + (step % 2 == 0 ? step / 2 : -(step + 1) / 2), z);
            if (!level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()
                    && level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return at;
        }
        return null;
    }

    /** Removes the recruit, and the horse it came with unless somebody else is using it. */
    private static void vanish(EntityNPCInterface npc, ServerLevel level) {
        WALKING.remove(npc.getUUID());
        CompoundTag pd = npc.getPersistentData();
        Entity horse = pd.hasUUID(HORSE) ? level.getEntity(pd.getUUID(HORSE)) : null;
        npc.stopRiding();
        puff(level, npc);
        npc.discard();
        if (horse != null && !horse.isVehicle()) {
            puff(level, horse);
            horse.discard();
        }
    }

    private static void puff(ServerLevel level, Entity e) {
        level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 20,
            e.getBbWidth() / 2, e.getBbHeight() / 3, e.getBbWidth() / 2, 0.02);
    }
}
