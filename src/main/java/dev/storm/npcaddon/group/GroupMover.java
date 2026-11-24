package dev.storm.npcaddon.group;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
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
 * Walks NPCs to a target, makes them follow a player, and keeps their attacks alive. CustomNPCs AI (wandering,
 * return-home, moving paths) fights navigation commands, so those are switched off while a task is active and
 * restored afterwards. The NPC's home is moved to where it ended up, otherwise "return to start" would send it
 * straight back.
 * Active tasks are in memory only: if the server stops mid-task the NPC keeps whatever AI flags it had at that time.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class GroupMover {
    private static final int TIMEOUT_TICKS = 20 * 30;
    private static final double ARRIVE_DIST_SQ = 0.9 * 0.9;
    private static final int MAX_STALLS = 3;
    private static final double SPEED = 1.1;
    private static final double FOLLOW_SPEED = 1.2;
    private static final int MAX_AGGRO = 128;

    private static final class Task {
        final ServerLevel level;
        final Vec3 target;
        final boolean savedReturn;
        final int savedMoving;
        final boolean hold;
        /** Non-null: follow this entity until cancelled instead of walking to {@link #target}. */
        final UUID follow;
        final int slotIndex;
        int ticks;
        int stalls;

        Task(ServerLevel level, Vec3 target, boolean savedReturn, int savedMoving, boolean hold, UUID follow, int slotIndex) {
            this.level = level;
            this.target = target;
            this.savedReturn = savedReturn;
            this.savedMoving = savedMoving;
            this.hold = hold;
            this.follow = follow;
            this.slotIndex = slotIndex;
        }
    }

    /** An ordered attack: CustomNPCs drops targets outside the NPC's aggro range, so the range is widened meanwhile. */
    private static final class Attack {
        final ServerLevel level;
        final UUID target;
        final int savedRange;
        int ticks;
        int unloaded;

        Attack(ServerLevel level, UUID target, int savedRange) {
            this.level = level;
            this.target = target;
            this.savedRange = savedRange;
        }
    }

    /** A direction an idle NPC is held at. A leader means "same way as that entity faces", live. */
    private static final class Face {
        final ServerLevel level;
        final float yaw;
        final UUID leader;

        Face(ServerLevel level, float yaw, UUID leader) {
            this.level = level;
            this.yaw = yaw;
            this.leader = leader;
        }
    }

    /** An area-attack order: walk to the slot, engage valid targets inside the area, hold the slot when it is clear. */
    private static final class Area {
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        final Vec3 slot;
        boolean engaged;

        Area(ServerLevel level, Vec3 center, double radius, Vec3 slot) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.slot = slot;
        }
    }

    private static final Map<UUID, Area> AREAS = new HashMap<>();
    private static int areaClock;
    private static final Map<UUID, Face> FACES = new HashMap<>();
    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private static final Map<UUID, Attack> ATTACKS = new HashMap<>();

    private GroupMover() {}

    /** Start (or retarget) a move. A retarget keeps the originally saved AI state. */
    public static void moveTo(EntityNPCInterface npc, Vec3 target) {
        moveTo(npc, target, false);
    }

    /** With hold=true the NPC stands at the target afterwards and returns there if pulled away (defend). */
    public static void moveTo(EntityNPCInterface npc, Vec3 target, boolean hold) {
        AREAS.remove(npc.getUUID());
        walk(npc, target, hold);
    }

    private static void walk(EntityNPCInterface npc, Vec3 target, boolean hold) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        begin(npc, level, target, hold, null, 0);
        endAttack(npc);
        haltPatrol(npc);
        npc.getNavigation().moveTo(target.x, target.y, target.z, SPEED);
    }

    /**
     * Area attack: walk to the slot, fight everything valid (hostile monsters, NPCs of hostile factions) inside the
     * radius around the centre, and hold the slot when the area is clear. Lasts until another order or stop.
     */
    public static void areaAttack(EntityNPCInterface npc, Vec3 center, double radius, Vec3 slot) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        AREAS.put(npc.getUUID(), new Area(level, center, radius, slot));
        walk(npc, slot, true);
    }

    public static boolean hasArea(EntityNPCInterface npc) {
        return AREAS.containsKey(npc.getUUID());
    }

    /** True while the NPC is walking to a spot it will not hold (a formation move): it should not pick fights. */
    public static boolean isBusyMoving(EntityNPCInterface npc) {
        Task t = TASKS.get(npc.getUUID());
        return t != null && t.follow == null && !t.hold;
    }

    /** Follow the leader continuously (own slot around them) until cancel(). */
    public static void follow(EntityNPCInterface npc, Entity leader, int slotIndex) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        AREAS.remove(npc.getUUID());
        begin(npc, level, leader.position(), false, leader.getUUID(), slotIndex);
        endAttack(npc);
        haltPatrol(npc);
        Vec3 slot = slot(leader.position(), slotIndex);
        npc.getNavigation().moveTo(slot.x, slot.y, slot.z, FOLLOW_SPEED);
    }

    /** Hold the NPC at this Minecraft yaw whenever it is standing still, until stop or another order. */
    public static void face(EntityNPCInterface npc, float yaw) {
        if (npc.level() instanceof ServerLevel level) FACES.put(npc.getUUID(), new Face(level, yaw, null));
    }

    /** Hold the NPC facing whichever way the leader faces, whenever it is standing still. */
    public static void faceLeader(EntityNPCInterface npc, Entity leader) {
        if (npc.level() instanceof ServerLevel level) FACES.put(npc.getUUID(), new Face(level, 0, leader.getUUID()));
    }

    private static void tickFaces() {
        if (FACES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Face>> it = FACES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Face> en = it.next();
            Face f = en.getValue();
            Entity ent = f.level.getEntity(en.getKey());
            if (ent instanceof EntityNPCInterface dead && !dead.isAlive()) {
                it.remove();
                continue;
            }
            // Unloaded NPCs keep their order. Walking, fighting or talking NPCs turn by themselves.
            if (!(ent instanceof EntityNPCInterface npc) || npc.getTarget() != null || npc.isInteracting()
                    || !npc.getNavigation().isDone()) continue;
            float yaw = f.yaw;
            if (f.leader != null) {
                Entity leader = f.level.getEntity(f.leader);
                if (leader == null || !leader.isAlive()) {
                    it.remove();
                    continue;
                }
                yaw = leader.getYRot();
            }
            npc.setYRot(yaw);
            npc.setYBodyRot(yaw);
            npc.setYHeadRot(yaw);
        }
    }

    private static void begin(EntityNPCInterface npc, ServerLevel level, Vec3 target, boolean hold, UUID follow, int slotIndex) {
        FACES.remove(npc.getUUID());
        Task old = TASKS.get(npc.getUUID());
        boolean ret = old != null ? old.savedReturn : npc.ais.returnToStart;
        int moving = old != null ? old.savedMoving : npc.ais.getMovingType();
        TASKS.put(npc.getUUID(), new Task(level, target, ret, moving, hold, follow, slotIndex));
        npc.ais.returnToStart = false;
        npc.ais.setMovingType(0);
    }

    /** Orders the NPC to attack and keeps it attacking even when the target is beyond its normal aggro range. */
    public static void attack(EntityNPCInterface npc, LivingEntity target) {
        AREAS.remove(npc.getUUID());
        attackInternal(npc, target);
    }

    private static void attackInternal(EntityNPCInterface npc, LivingEntity target) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        FACES.remove(npc.getUUID());
        Attack old = ATTACKS.get(npc.getUUID());
        int saved = old != null ? old.savedRange : npc.stats.aggroRange;
        ATTACKS.put(npc.getUUID(), new Attack(level, target.getUUID(), saved));
        widen(npc, target, saved);
        npc.setTarget(target);
    }

    private static void widen(EntityNPCInterface npc, LivingEntity target, int saved) {
        int want = Math.max(saved, (int) Math.min(MAX_AGGRO, Math.ceil(npc.distanceTo(target)) + 32));
        if (npc.stats.aggroRange != want) npc.stats.setAggroRange(want);
    }

    /** True while this NPC has an explicit attack order on exactly this target (stances never block those). */
    public static boolean isOrderedTarget(EntityNPCInterface npc, LivingEntity target) {
        Attack a = ATTACKS.get(npc.getUUID());
        return a != null && a.target.equals(target.getUUID());
    }

    private static void endAttack(EntityNPCInterface npc) {
        Attack a = ATTACKS.remove(npc.getUUID());
        if (a != null && npc.stats.aggroRange != a.savedRange) npc.stats.setAggroRange(a.savedRange);
    }

    /**
     * Removes the running patrol goal. Changing the moving type only schedules an AI rebuild, and until then the
     * goal keeps walking to the waypoint it already picked. removeGoal stops it if it is running.
     */
    public static void haltPatrol(EntityNPCInterface npc) {
        var goals = new java.util.ArrayList<>(npc.goalSelector.getAvailableGoals());
        for (var w : goals) {
            if (w.getGoal() instanceof noppes.npcs.ai.EntityAIMovingPath) npc.goalSelector.removeGoal(w.getGoal());
        }
        npc.getNavigation().stop();
    }

    /** Short label of what the NPC is doing because of an addon order, for the squad screen. */
    public static String describe(EntityNPCInterface npc) {
        if (ATTACKS.containsKey(npc.getUUID())) return "Attacking";
        if (AREAS.containsKey(npc.getUUID())) return "Area";
        Task t = TASKS.get(npc.getUUID());
        if (t != null) return t.follow != null ? "Following" : t.hold ? "Defending" : "Moving";
        if (npc.ais.getMovingType() == 2) return "Patrolling";
        return npc.ais.returnToStart ? "Holding" : "Idle";
    }

    public static int activeCount() {
        return TASKS.size();
    }

    private static void finish(EntityNPCInterface npc, Task t) {
        npc.getNavigation().stop();
        npc.ais.setStartPos(npc.blockPosition());
        npc.ais.returnToStart = t.hold || t.savedReturn;
        npc.ais.setMovingType(t.hold ? 0 : t.savedMoving);
    }

    /** Stops an active move, follow or attack and restores the NPC's AI flags without touching its home. */
    public static void cancel(EntityNPCInterface npc) {
        AREAS.remove(npc.getUUID());
        cancelTask(npc);
    }

    private static void cancelTask(EntityNPCInterface npc) {
        FACES.remove(npc.getUUID());
        endAttack(npc);
        Task t = TASKS.remove(npc.getUUID());
        if (t == null) return;
        npc.getNavigation().stop();
        npc.ais.returnToStart = t.savedReturn;
        npc.ais.setMovingType(t.savedMoving);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tickTasks();
        tickAttacks();
        tickFaces();
        tickAreas();
    }

    private static void tickTasks() {
        if (TASKS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Task>> it = TASKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Task> en = it.next();
            Task t = en.getValue();
            Entity ent = t.level.getEntity(en.getKey());
            if (ent instanceof EntityNPCInterface dead && !dead.isAlive()) {
                it.remove();
                continue;
            }
            if (!(ent instanceof EntityNPCInterface npc)) {
                // Unloaded: wait for it to come back. Walk tasks give up after the timeout (AI flags stay off then).
                if (t.follow == null && ++t.ticks > TIMEOUT_TICKS) it.remove();
                continue;
            }
            t.ticks++;
            if (t.follow != null) {
                if (!tickFollow(npc, t)) it.remove();
                continue;
            }
            boolean arrived = npc.position().distanceToSqr(t.target) <= ARRIVE_DIST_SQ;
            if (arrived || t.ticks > TIMEOUT_TICKS) {
                finish(npc, t);
                it.remove();
            } else if (t.ticks % 10 == 0 && npc.getNavigation().isDone()) {
                // Path ended short of the slot (blocked, water, or stopped early): retry a few times, then settle.
                if (++t.stalls >= MAX_STALLS) {
                    finish(npc, t);
                    it.remove();
                } else {
                    npc.getNavigation().moveTo(t.target.x, t.target.y, t.target.z, SPEED);
                }
            }
        }
    }

    /** One follow tick; false when the leader is gone (the NPC is then settled where it stands). */
    private static boolean tickFollow(EntityNPCInterface npc, Task t) {
        Entity leader = t.level.getEntity(t.follow);
        if (leader == null || !leader.isAlive()) {
            finish(npc, t);
            return false;
        }
        if (t.ticks % 10 != 0 || npc.getTarget() != null) return true; // fighting: let CustomNPCs handle it
        Vec3 slot = slot(leader.position(), t.slotIndex);
        double d = npc.position().distanceToSqr(slot);
        if (d > 2.5 * 2.5 || (d > 1.5 * 1.5 && npc.getNavigation().isDone())) {
            npc.getNavigation().moveTo(slot.x, slot.y, slot.z, d > 10 * 10 ? FOLLOW_SPEED + 0.3 : FOLLOW_SPEED);
        }
        return true;
    }

    private static void tickAreas() {
        if (AREAS.isEmpty() || ++areaClock % 10 != 0) return;
        Iterator<Map.Entry<UUID, Area>> it = AREAS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Area> en = it.next();
            Area a = en.getValue();
            Entity ent = a.level.getEntity(en.getKey());
            if (ent instanceof EntityNPCInterface dead && !dead.isAlive()) {
                it.remove();
                continue;
            }
            if (!(ent instanceof EntityNPCInterface npc)) continue; // unloaded: keeps its order
            LivingEntity cur = npc.getTarget();
            if (cur != null && cur.isAlive()) {
                double far = a.radius + 12;
                if (isOrderedTarget(npc, cur) && cur.position().distanceToSqr(a.center) > far * far) {
                    npc.setTarget(null); // it ran out of the area: pick someone else
                } else {
                    a.engaged = true;
                    continue;
                }
            }
            LivingEntity next = findAreaTarget(npc, a);
            double near = a.radius + 8;
            if (next != null && npc.position().distanceToSqr(a.center) <= near * near) {
                cancelTask(npc); // stop the walk so it does not fight the attack goal
                npc.ais.returnToStart = false;
                attackInternal(npc, next);
                a.engaged = true;
            } else if (next == null && a.engaged) {
                a.engaged = false;
                walk(npc, a.slot, true); // clear: back to holding the spot
            }
        }
    }

    private static LivingEntity findAreaTarget(EntityNPCInterface npc, Area a) {
        double r2 = a.radius * a.radius;
        AABB box = new AABB(a.center, a.center).inflate(a.radius);
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : a.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != npc && e.isAlive())) {
            if (e.position().distanceToSqr(a.center) > r2) continue;
            boolean valid = e instanceof Enemy || (e instanceof EntityNPCInterface o && npc.faction.isAggressiveToNpc(o));
            if (!valid) continue;
            double d = npc.distanceToSqr(e);
            if (d < bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    private static void tickAttacks() {
        if (ATTACKS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Attack>> it = ATTACKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Attack> en = it.next();
            Attack a = en.getValue();
            if (++a.ticks % 20 != 0) continue;
            Entity ent = a.level.getEntity(en.getKey());
            if (ent instanceof EntityNPCInterface dead && !dead.isAlive()) {
                it.remove();
                continue;
            }
            if (!(ent instanceof EntityNPCInterface npc)) {
                if (++a.unloaded > 60) it.remove();
                continue;
            }
            // Over when the target is gone or the NPC switched to something else (retaliation, another order).
            if (!(a.level.getEntity(a.target) instanceof LivingEntity target) || !target.isAlive()
                    || npc.getTarget() != target) {
                if (npc.stats.aggroRange != a.savedRange) npc.stats.setAggroRange(a.savedRange);
                it.remove();
            } else {
                widen(npc, target, a.savedRange);
            }
        }
    }

    /** Ring of slots around a centre, nearest-first spiral. */
    public static Vec3 slot(Vec3 centre, int index) {
        if (index == 0) return centre.add(1.5, 0, 0);
        double angle = index * 2.399963; // golden angle
        double r = 1.5 + 0.6 * Math.sqrt(index);
        return centre.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
    }
}
