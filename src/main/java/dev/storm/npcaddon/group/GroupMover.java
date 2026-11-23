package dev.storm.npcaddon.group;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

    private static final Map<UUID, Task> TASKS = new HashMap<>();
    private static final Map<UUID, Attack> ATTACKS = new HashMap<>();

    private GroupMover() {}

    /** Start (or retarget) a move. A retarget keeps the originally saved AI state. */
    public static void moveTo(EntityNPCInterface npc, Vec3 target) {
        moveTo(npc, target, false);
    }

    /** With hold=true the NPC stands at the target afterwards and returns there if pulled away (defend). */
    public static void moveTo(EntityNPCInterface npc, Vec3 target, boolean hold) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        begin(npc, level, target, hold, null, 0);
        endAttack(npc);
        haltPatrol(npc);
        npc.getNavigation().moveTo(target.x, target.y, target.z, SPEED);
    }

    /** Follow the leader continuously (own slot around them) until cancel(). */
    public static void follow(EntityNPCInterface npc, Entity leader, int slotIndex) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        begin(npc, level, leader.position(), false, leader.getUUID(), slotIndex);
        endAttack(npc);
        haltPatrol(npc);
        Vec3 slot = slot(leader.position(), slotIndex);
        npc.getNavigation().moveTo(slot.x, slot.y, slot.z, FOLLOW_SPEED);
    }

    private static void begin(EntityNPCInterface npc, ServerLevel level, Vec3 target, boolean hold, UUID follow, int slotIndex) {
        Task old = TASKS.get(npc.getUUID());
        boolean ret = old != null ? old.savedReturn : npc.ais.returnToStart;
        int moving = old != null ? old.savedMoving : npc.ais.getMovingType();
        TASKS.put(npc.getUUID(), new Task(level, target, ret, moving, hold, follow, slotIndex));
        npc.ais.returnToStart = false;
        npc.ais.setMovingType(0);
    }

    /** Orders the NPC to attack and keeps it attacking even when the target is beyond its normal aggro range. */
    public static void attack(EntityNPCInterface npc, LivingEntity target) {
        if (!(npc.level() instanceof ServerLevel level)) return;
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
