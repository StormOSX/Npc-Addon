package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A squad NPC whose health falls to its squad's threshold breaks off, runs from the enemies around it and waits where
 * it is safe. It goes back to what it was doing once it has recovered and no enemy is near; a rally or any other order
 * ends the retreat at once. It does not flee again until it has healed above the threshold. The state is kept in the
 * NPC's persistent data, so it survives a reload.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Retreat {
    private static final String STATE = "npcaddon_retreat";
    /** Set from the start of a retreat until the NPC is healthier than the threshold again. */
    private static final String SPENT = "npcaddon_retreat_spent";
    private static final double SAFE_DISTANCE = 16;
    private static final double SPEED = 1.35;
    /** Health fraction at which an NPC counts as recovered (higher when the threshold is high). */
    private static final float RECOVERED = 0.5f;
    private static final float RECOVERY_MARGIN = 0.25f;
    /** Present in the state of a routed NPC: the game time the rout began. */
    private static final String ROUT = "rout";
    private static final int ROUT_TICKS = 20 * 10;
    private static int clock;

    private Retreat() {}

    public static boolean isRetreating(EntityNPCInterface npc) {
        return npc.getPersistentData().contains(STATE);
    }

    private static void start(EntityNPCInterface npc) {
        CompoundTag state = GroupMover.snapshot(npc);
        // cancel() restores the AI flags an active order had switched off, so read them afterwards.
        GroupMover.cancel(npc);
        state.putBoolean("return", npc.ais.returnToStart);
        state.putInt("moving", npc.ais.getMovingType());
        npc.getPersistentData().put(STATE, state);
        npc.getPersistentData().putBoolean(SPENT, true);
        npc.ais.returnToStart = false;
        npc.ais.setMovingType(0);
        GroupMover.haltPatrol(npc);
        npc.setTarget(null);
    }

    static boolean isRouted(EntityNPCInterface npc) {
        return npc.getPersistentData().getCompound(STATE).contains(ROUT);
    }

    /** Morale broke: flee whatever the health, until it has been safe for a while. */
    static void rout(EntityNPCInterface npc) {
        if (isRetreating(npc)) return;
        start(npc);
        npc.getPersistentData().remove(SPENT);
        npc.getPersistentData().getCompound(STATE).putLong(ROUT, npc.level().getGameTime());
    }

    /** Ends a retreat without resuming the earlier order: used before every new order. */
    public static void end(EntityNPCInterface npc) {
        finish(npc);
    }

    /** @return the saved state, or null if the NPC was not retreating */
    private static CompoundTag finish(EntityNPCInterface npc) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(STATE)) return null;
        CompoundTag state = pd.getCompound(STATE);
        pd.remove(STATE);
        npc.getNavigation().stop();
        npc.ais.returnToStart = state.getBoolean("return");
        npc.ais.setMovingType(state.getInt("moving"));
        return state;
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide
                && isRetreating(npc)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        clock++;
        MinecraftServer server = e.getServer();
        GroupData data = GroupData.get(server.overworld());
        Set<UUID> done = new HashSet<>();
        for (String name : data.names()) {
            for (UUID id : data.members(name)) {
                if (!done.add(id)) continue;
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof EntityNPCInterface npc && npc.isAlive()) {
                        update(npc, level, data.retreatOfMember(id) / 100f);
                        break;
                    }
                }
            }
        }
    }

    private static void update(EntityNPCInterface npc, ServerLevel level, float threshold) {
        float health = npc.getHealth() / npc.getMaxHealth();
        CompoundTag pd = npc.getPersistentData();
        if (!isRetreating(npc)) {
            if (clock % 10 != 0) return;
            if (health > threshold) pd.remove(SPENT);
            else if (threshold > 0 && !pd.getBoolean(SPENT)) start(npc);
            return;
        }
        boolean routed = isRouted(npc);
        if (threshold <= 0 && !routed) {
            resume(npc, level, finish(npc));
            return;
        }
        // Every tick: CustomNPCs keeps fighting its last attacker even without a target.
        LivingEntity attacker = npc.getLastHurtByMob();
        if (npc.getTarget() != null) npc.setTarget(null);
        if (attacker != null) {
            pd.getCompound(STATE).putUUID("attacker", attacker.getUUID());
            npc.setLastHurtByMob(null);
        }
        if (clock % 10 != 0) return;
        LivingEntity threat = nearestThreat(npc, level, pd.getCompound(STATE));
        if (threat != null) {
            if (npc.getNavigation().isDone() || clock % 20 == 0) {
                Vec3 away = DefaultRandomPos.getPosAway(npc, 16, 7, threat.position());
                if (away != null) npc.getNavigation().moveTo(away.x, away.y, away.z, SPEED);
            }
        } else if (routed) {
            // Safe and long enough since the rout: steady again, whatever the health.
            if (level.getGameTime() - pd.getCompound(STATE).getLong(ROUT) >= ROUT_TICKS) {
                resume(npc, level, finish(npc));
            }
        } else if (health >= Math.max(RECOVERED, threshold + RECOVERY_MARGIN)) {
            pd.remove(SPENT);
            resume(npc, level, finish(npc));
        }
    }

    /** The closest enemy near enough to run from: one the NPC can see, or one that is after it. */
    private static LivingEntity nearestThreat(EntityNPCInterface npc, ServerLevel level, CompoundTag state) {
        UUID attacker = state.hasUUID("attacker") ? state.getUUID("attacker") : null;
        LivingEntity best = null;
        double bestD = SAFE_DISTANCE * SAFE_DISTANCE;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(SAFE_DISTANCE),
                t -> t != npc && t.isAlive()
                    && (t.getUUID().equals(attacker) || MobDefense.isThreat(npc, t, level)))) {
            double d = npc.distanceToSqr(t);
            boolean after = t.getUUID().equals(attacker) || t instanceof net.minecraft.world.entity.Mob m
                && m.getTarget() == npc;
            if (d < bestD && (after || npc.hasLineOfSight(t))) {
                bestD = d;
                best = t;
            }
        }
        // A player who hit the NPC once and went creative or spectator is no longer chasing it.
        if (best instanceof net.minecraft.world.entity.player.Player p && (p.isCreative() || p.isSpectator())) return null;
        return best;
    }

    /** Takes up the order the NPC had before it fled. Without one, the restored AI settings take it home. */
    private static void resume(EntityNPCInterface npc, ServerLevel level, CompoundTag state) {
        if (state == null) return;
        if (state.hasUUID("follow")) {
            Entity leader = level.getEntity(state.getUUID("follow"));
            if (leader == null || !leader.isAlive()) return;
            GroupMover.follow(npc, leader, state.getInt("slot"));
        } else if (state.contains("areaRadius")) {
            GroupMover.areaAttack(npc, vec(state, "area"), state.getDouble("areaRadius"), vec(state, "spot"));
        } else if (state.contains("hold")) {
            GroupMover.moveTo(npc, vec(state, "spot"), state.getBoolean("hold"));
        }
        if (state.contains("wall")) {
            CompoundTag wall = state.getCompound("wall");
            if (!state.contains("hold")) GroupMover.moveTo(npc, vec(wall, "spot"), true);
            ShieldWall.assign(npc, vec(wall, "spot"), wall.getFloat("yaw"), wall.getBoolean("rear"));
        }
        if (state.hasUUID("faceLeader")) {
            Entity leader = level.getEntity(state.getUUID("faceLeader"));
            if (leader != null) GroupMover.faceLeader(npc, leader);
        } else if (state.contains("yaw")) {
            GroupMover.face(npc, state.getFloat("yaw"));
        }
    }

    static void putVec(CompoundTag tag, String key, Vec3 v) {
        tag.putDouble(key + "X", v.x);
        tag.putDouble(key + "Y", v.y);
        tag.putDouble(key + "Z", v.z);
    }

    static Vec3 vec(CompoundTag tag, String key) {
        return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
    }
}
