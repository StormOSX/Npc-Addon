package dev.storm.npcaddon.group;

import dev.storm.npcaddon.aw.AwSiege;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A war horn calls a temporary force made from the recruit catalog: half melee, a quarter ranged, then riders and siege
 * crews. It appears far off, marches in as a column (breaking ranks to fight on the way), follows the caller as a squad
 * of theirs, and leaves once the fighting has been over for a while.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Reinforcements {
    public static final int SIZE = 20;
    public static final int HORN_PRICE = 40;
    private static final int MELEE = 10;
    private static final int RANGED = 5;
    private static final int MOUNTED = 3;
    private static final int SIEGE = 2;

    private static final int[] DISTANCES = {150, 135, 120, 96, 72, 54};
    private static final String TAG = "npcaddon_reinforcement";

    private static final int QUIET_SECONDS = 60;
    private static final int NO_BATTLE_SECONDS = 180;
    private static final int MAX_SECONDS = 60 * 15;
    private static final int OFFLINE_SECONDS = 60;
    private static final Map<UUID, Integer> OFFLINE = new HashMap<>();
    private static final double ENEMY_RANGE = 32;
    private static final double ARRIVED = 10;
    private static final int MARCH_TIMEOUT = 20 * 60 * 5;
    private static final int STUCK_TICKS = 20 * 10;
    private static final int STRAGGLER_TICKS = 20 * 8;
    private static final double STRAGGLER_DIST = 24;
    /** The leader waits while the column is on average further than this from its places. */
    private static final double WAIT_LAG = 7;

    private static final class Unit {
        final UUID npc;
        /** Place in the column: how far behind the head, and to which side. */
        final double back;
        final double side;
        int straggling;

        Unit(UUID npc, double back, double side) {
            this.npc = npc;
            this.back = back;
            this.side = side;
        }
    }

    private static final class March {
        final ServerLevel level;
        final List<Unit> units = new ArrayList<>();
        /** Where the head of the column has been, newest first; the others walk along it. */
        final List<Vec3> trail = new ArrayList<>();
        double length;
        int ticks;
        Vec3 checked;
        int stuck;

        March(ServerLevel level) {
            this.level = level;
        }
    }

    /** Owner -> the march in progress (memory only: after a reload the force simply follows). */
    private static final Map<UUID, March> MARCHES = new HashMap<>();
    private static final Set<UUID> MARCHING = new HashSet<>();
    /** Force members and owners that were in a fight since the last check. */
    private static final Set<UUID> HURT = new HashSet<>();
    /** Loaded entities that carry the reinforcement mark and may be left over from a force that is gone. */
    private static final Set<Entity> LEFTOVERS = new HashSet<>();
    private static int clock;

    private Reinforcements() {}

    static boolean isMarching(EntityNPCInterface npc) {
        return MARCHING.contains(npc.getUUID());
    }

    private static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    // ---------------- calling ----------------

    /** Calls a force to the player. @return false (after saying why) if none came, so the horn is kept */
    public static boolean call(ServerPlayer p) {
        ReinforcementData data = ReinforcementData.get(p.server);
        if (data.forces.containsKey(p.getUUID())) {
            tell(p, "Your reinforcements are still in the field");
            return false;
        }
        ServerLevel level = p.serverLevel();
        List<RecruitCatalog.Entry> picks = pick(p, level);
        if (picks.isEmpty()) {
            tell(p, "There is nobody on the recruit list to answer the horn");
            return false;
        }
        Vec3 spot = Arrivals.spotAt(p, true, DISTANCES);
        if (spot == null) {
            tell(p, "No reinforcements can reach you here: find more open ground");
            return false;
        }

        March march = new March(level);
        double total = 0;
        for (RecruitCatalog.Entry e : picks) total += spacing(e.type()) / (e.type() == RecruitType.ENGINEER ? 1 : 2);
        Vec3 away = spot.subtract(p.position()).multiply(1, 0, 1).normalize();
        for (int d = 0; d <= total + 8; d++) march.trail.add(GroupMover.grounded(level, spot.add(away.scale(d))));
        march.length = total + 8;

        GroupData groups = GroupData.get(p.server.overworld());
        String squad = squadName(p, groups);
        ReinforcementData.Force force = new ReinforcementData.Force(squad);
        List<UUID> ids = new ArrayList<>();
        double back = 0;
        int inRow = 0;
        for (RecruitCatalog.Entry e : picks) {
            boolean engineer = e.type() == RecruitType.ENGINEER;
            if (engineer && inRow == 1) {
                back += spacing(RecruitType.MELEE);
                inRow = 0;
            }
            double side = engineer ? 0 : (inRow == 0 ? -1 : 1) * (e.type() == RecruitType.MOUNTED ? 1.5 : 1.0);
            Vec3 at = place(level, march, back, side);
            EntityNPCInterface npc = Recruits.spawnCopy(level, e, at);
            if (npc != null) {
                npc.getPersistentData().putBoolean(TAG, true);
                npc.ais.returnToStart = false;
                npc.ais.setMovingType(0);
                float yaw = VehicleDriver.yawTo(at, p.position());
                GroupMover.turn(npc, yaw);
                if (e.type() == RecruitType.MOUNTED) Recruits.giveHorse(level, npc, at);
                if (engineer) giveWeapon(level, p, npc, at, yaw, force);
                march.units.add(new Unit(npc.getUUID(), back, side));
                ids.add(npc.getUUID());
            }
            // Foot and riders march two abreast, siege weapons one behind another.
            if (engineer || ++inRow == 2) {
                back += spacing(e.type());
                inRow = 0;
            }
        }
        if (ids.isEmpty()) {
            tell(p, "Nobody could answer the horn");
            return false;
        }

        groups.addAll(squad, ids);
        groups.setOwner(squad, p.getUUID());
        groups.setStance(squad, Stance.AGGRESSIVE);
        groups.setRetreat(squad, 0);
        groups.setShape(squad, Formation.COLUMN, 2);
        for (UUID id : ids) {
            if (level.getEntity(id) instanceof EntityNPCInterface npc) StanceManager.apply(npc, Stance.AGGRESSIVE);
        }
        force.members.addAll(ids);
        data.forces.put(p.getUUID(), force);
        data.setDirty();
        MARCHES.put(p.getUUID(), march);
        MARCHING.addAll(ids);
        WandController.setSelected(p, squad);
        level.playSound(null, p.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 16.0F, 1.0F);
        int distance = (int) Math.sqrt(p.position().distanceToSqr(spot));
        tell(p, ids.size() + " reinforcements are marching in from " + distance + " blocks away (squad '" + squad + "')");
        return true;
    }

    private static double spacing(RecruitType type) {
        return switch (type) {
            case MOUNTED -> 4.0;
            case ENGINEER -> 6.0;
            default -> 2.2;
        };
    }

    /**
     * The catalog entries to create, in marching order: riders, melee, ranged, siege. A type nobody was saved for is
     * made up with melee recruits; siege crews need Ancient Warfare.
     */
    private static List<RecruitCatalog.Entry> pick(ServerPlayer p, ServerLevel level) {
        Map<RecruitType, List<RecruitCatalog.Entry>> byType = new HashMap<>();
        for (RecruitCatalog.Entry e : RecruitCatalog.get(p.server).entries().values()) {
            // Leave out recruits whose faction would attack the caller.
            var trial = Recruits.spawnCopy(level, e, p.position());
            if (trial == null) continue;
            boolean hostile = trial.faction.isAggressiveToPlayer(p);
            trial.discard();
            if (!hostile) byType.computeIfAbsent(e.type(), t -> new ArrayList<>()).add(e);
        }
        List<RecruitCatalog.Entry> fallback = byType.getOrDefault(RecruitType.MELEE, List.of());
        if (fallback.isEmpty()) {
            fallback = byType.values().stream().flatMap(List::stream)
                .filter(e -> e.type() != RecruitType.ENGINEER).toList();
        }
        boolean siege = AwSiege.createMobile(level) != null;
        List<RecruitCatalog.Entry> out = new ArrayList<>();
        int[] counts = {MOUNTED, MELEE, RANGED, SIEGE};
        RecruitType[] order = {RecruitType.MOUNTED, RecruitType.MELEE, RecruitType.RANGED, RecruitType.ENGINEER};
        for (int i = 0; i < order.length; i++) {
            List<RecruitCatalog.Entry> pool = byType.getOrDefault(order[i], List.of());
            if (pool.isEmpty() || order[i] == RecruitType.ENGINEER && !siege) pool = fallback;
            if (pool.isEmpty()) continue;
            for (int n = 0; n < counts[i]; n++) out.add(pool.get(level.random.nextInt(pool.size())));
        }
        return out;
    }

    private static void giveWeapon(ServerLevel level, ServerPlayer p, EntityNPCInterface npc, Vec3 at, float yaw,
            ReinforcementData.Force force) {
        Entity weapon = AwSiege.createMobile(level);
        if (weapon == null) return;
        weapon.moveTo(at.x, at.y, at.z, yaw, 0);
        weapon.getPersistentData().putBoolean(TAG, true);
        force.weapons.add(weapon.getUUID());
        level.addFreshEntity(weapon);
        SiegeCrew.man(npc, weapon, p);
        AwSiege.begin(weapon, npc);
    }

    private static String squadName(ServerPlayer p, GroupData groups) {
        String base = p.getGameProfile().getName().replaceAll("[^A-Za-z0-9_]", "");
        base = "horn_" + (base.length() > 20 ? base.substring(0, 20) : base);
        String name = base;
        for (int i = 2; groups.exists(name); i++) name = base + i;
        return name;
    }

    /** The point this far back along the column's trail, shifted to the side. */
    private static Vec3 place(ServerLevel level, March m, double back, double side) {
        Vec3 point = m.trail.get(m.trail.size() - 1);
        Vec3 ahead = new Vec3(0, 0, 1);
        double gone = 0;
        for (int i = 1; i < m.trail.size(); i++) {
            Vec3 a = m.trail.get(i - 1);
            Vec3 b = m.trail.get(i);
            double step = a.distanceTo(b);
            Vec3 dir = a.subtract(b).multiply(1, 0, 1);
            if (dir.lengthSqr() > 1.0E-4) ahead = dir.normalize();
            if (gone + step >= back) {
                point = a.lerp(b, step == 0 ? 0 : (back - gone) / step);
                break;
            }
            gone += step;
        }
        if (side == 0) return point;
        Vec3 shifted = GroupMover.grounded(level, point.add(-ahead.z * side, 0, ahead.x * side));
        // A narrow way: fall into single file rather than aiming into a wall or off a drop.
        return Math.abs(shifted.y - point.y) > 1.5 ? point : shifted;
    }

    // ---------------- marching ----------------

    private static EntityNPCInterface find(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof EntityNPCInterface n && n.isAlive() ? n : null;
    }

    /** The entity that actually moves: the mount or siege weapon the NPC rides, or the NPC. */
    private static Entity body(EntityNPCInterface npc) {
        return npc.getVehicle() != null ? npc.getVehicle() : npc;
    }

    /** One march step; true when it is over. */
    private static boolean march(ServerPlayer p, March m) {
        m.ticks += 10;
        if (p.serverLevel() != m.level) return m.ticks > MARCH_TIMEOUT;
        List<Unit> alive = new ArrayList<>();
        List<EntityNPCInterface> npcs = new ArrayList<>();
        for (Unit u : m.units) {
            EntityNPCInterface n = find(m.level, u.npc);
            if (n == null) continue;
            // Any order from the caller ends the march.
            if (GroupMover.hasOrder(n) || SiegeCrew.hasOtherOrder(n)) return true;
            alive.add(u);
            npcs.add(n);
        }
        if (alive.isEmpty()) return true;
        EntityNPCInterface head = npcs.get(0);
        Vec3 headAt = body(head).position();
        if (m.ticks > MARCH_TIMEOUT || headAt.distanceTo(p.position()) <= ARRIVED) return true;
        if (headAt.distanceTo(m.trail.get(0)) >= 0.8) {
            m.trail.add(0, headAt);
            double kept = 0;
            for (int i = 1; i < m.trail.size(); i++) {
                kept += m.trail.get(i - 1).distanceTo(m.trail.get(i));
                if (kept > m.length) {
                    while (m.trail.size() > i + 1) m.trail.remove(m.trail.size() - 1);
                    break;
                }
            }
        }

        double lag = 0;
        int counted = 0;
        for (int i = 1; i < alive.size(); i++) {
            Unit u = alive.get(i);
            EntityNPCInterface npc = npcs.get(i);
            if (npc.getTarget() != null) continue; // broke ranks to fight
            double rel = Math.max(0, u.back - alive.get(0).back);
            boolean crew = SiegeCrew.isCrew(npc);
            Vec3 slot = place(m.level, m, rel, crew ? 0 : u.side);
            double off = body(npc).position().distanceTo(slot);
            lag += Math.min(off, 20);
            counted++;
            u.straggling = off > STRAGGLER_DIST ? u.straggling + 10 : 0;
            if (u.straggling >= STRAGGLER_TICKS) {
                // The trail is ground the head has already crossed, so it is safe to bring them up onto it.
                body(npc).teleportTo(slot.x, slot.y, slot.z);
                u.straggling = 0;
            } else if (crew) {
                SiegeCrew.march(npc, slot);
            } else if (off > 1.2) {
                npc.getNavigation().moveTo(slot.x, slot.y, slot.z, off > 6 ? 1.4 : 1.15);
            }
        }

        if (head.getTarget() != null) {
            m.stuck = 0;
            return false;
        }
        boolean wait = counted > 0 && lag / counted > WAIT_LAG && m.ticks % 200 < 140;
        if (SiegeCrew.isCrew(head)) {
            SiegeCrew.march(head, wait ? headAt : p.position());
        } else if (wait) {
            head.getNavigation().stop();
        } else if (head.getNavigation().isDone() || m.ticks % 40 == 0) {
            GroupMover.head(head, m.level, GroupMover.grounded(m.level, p.position()), m.stuck / STUCK_TICKS, 0.9);
        }
        if (m.checked == null || headAt.distanceTo(m.checked) > 3 || wait) {
            m.checked = headAt;
            m.stuck = 0;
        } else if ((m.stuck += 10) >= STUCK_TICKS * 2) {
            // Hopelessly stuck: bring the head well forward; the rest catch up along the new trail.
            int half = (int) Math.max(12, headAt.distanceTo(p.position()) / 2);
            Vec3 spot = Arrivals.spotAt(p, true, new int[]{half, half * 2 / 3, 10});
            if (spot == null) return true;
            body(head).teleportTo(spot.x, spot.y, spot.z);
            m.trail.add(0, spot);
            m.stuck = 0;
        }
        return false;
    }

    /** The column has arrived (or given up on staying one): everyone follows the caller. */
    private static void arrive(ServerPlayer p, ReinforcementData.Force force, March m, boolean ordered) {
        GroupData groups = GroupData.get(p.server.overworld());
        int slot = 0;
        for (UUID id : groups.members(force.squad)) {
            MARCHING.remove(id);
            EntityNPCInterface npc = find(p.serverLevel(), id);
            if (npc == null || ordered) continue;
            if (SiegeCrew.isCrew(npc)) {
                SiegeCrew.follow(npc, p, slot);
            } else {
                GroupMover.follow(npc, p, slot++);
                GroupMover.faceLeader(npc, p);
            }
        }
        if (m != null) for (Unit u : m.units) MARCHING.remove(u.npc);
        if (!ordered) tell(p, "Your reinforcements have arrived");
    }

    private static boolean wasOrdered(March m) {
        for (Unit u : m.units) {
            EntityNPCInterface n = find(m.level, u.npc);
            if (n != null && (GroupMover.hasOrder(n) || SiegeCrew.hasOtherOrder(n))) return true;
        }
        return false;
    }

    // ---------------- the battle and leaving ----------------

    @SubscribeEvent
    public static void hurt(LivingHurtEvent e) {
        if (e.getEntity().level().isClientSide || !(e.getSource().getEntity() instanceof LivingEntity)) return;
        HURT.add(e.getEntity().getUUID());
        HURT.add(e.getSource().getEntity().getUUID());
    }

    /** Fighting means someone has a target or takes or deals a blow, or an enemy is close by. */
    private static boolean fighting(ServerPlayer p, UUID owner, List<EntityNPCInterface> npcs) {
        if (HURT.contains(owner)) return true;
        for (EntityNPCInterface n : npcs) {
            if (HURT.contains(n.getUUID()) || n.getTarget() != null && n.getTarget().isAlive()) return true;
        }
        if (npcs.isEmpty()) return false;
        EntityNPCInterface any = npcs.get(0);
        ServerLevel level = (ServerLevel) any.level();
        AABB around = any.getBoundingBox();
        for (EntityNPCInterface n : npcs) if (n.level() == level) around = around.minmax(n.getBoundingBox());
        if (p != null && p.serverLevel() == level) around = around.minmax(p.getBoundingBox());
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class, around.inflate(ENEMY_RANGE),
                t -> t.isAlive() && Hostility.isFoe(any, t, level))) {
            if (p != null && t.distanceToSqr(p) <= ENEMY_RANGE * ENEMY_RANGE) return true;
            for (EntityNPCInterface n : npcs) {
                if (n.level() == level && t.distanceToSqr(n) <= ENEMY_RANGE * ENEMY_RANGE) return true;
            }
        }
        return false;
    }

    private static List<EntityNPCInterface> loaded(MinecraftServer server, Iterable<UUID> ids) {
        List<EntityNPCInterface> out = new ArrayList<>();
        for (UUID id : ids) {
            for (ServerLevel level : server.getAllLevels()) {
                EntityNPCInterface n = find(level, id);
                if (n != null) {
                    out.add(n);
                    break;
                }
            }
        }
        return out;
    }

    /** Ends the force: the soldiers walk away and vanish, and their siege weapons with them. */
    private static void disband(MinecraftServer server, UUID owner, String why) {
        ReinforcementData data = ReinforcementData.get(server);
        ReinforcementData.Force force = data.forces.remove(owner);
        if (force == null) return;
        data.setDirty();
        March m = MARCHES.remove(owner);
        if (m != null) for (Unit u : m.units) MARCHING.remove(u.npc);
        GroupData groups = GroupData.get(server.overworld());
        List<EntityNPCInterface> npcs = loaded(server, force.members);
        groups.removeGroup(force.squad);
        // Unloaded soldiers are sent away when they next load (see leftovers).
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        for (EntityNPCInterface npc : npcs) sendAway(npc, p);
        for (UUID id : force.weapons) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity weapon = level.getEntity(id);
                if (weapon != null) weapon.discard();
            }
        }
        if (p != null) tell(p, why);
    }

    private static void sendAway(EntityNPCInterface npc, ServerPlayer p) {
        MARCHING.remove(npc.getUUID());
        boolean ride = Departures.onOwnHorse(npc);
        GroupMover.cancel(npc); // also gets a crew off its weapon
        if (!ride) Mounts.dismount(npc);
        npc.setTarget(null);
        GroupMover.haltPatrol(npc);
        StanceManager.apply(npc, Stance.DEFENSIVE);
        Departures.start(npc, p != null && p.level() == npc.level() ? p : npc);
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.loadedFromDisk() && e.getEntity().getPersistentData().getBoolean(TAG)) {
            LEFTOVERS.add(e.getEntity());
        }
    }

    private static void leftovers(MinecraftServer server) {
        if (LEFTOVERS.isEmpty()) return;
        ReinforcementData data = ReinforcementData.get(server);
        GroupData groups = GroupData.get(server.overworld());
        for (Entity e : new ArrayList<>(LEFTOVERS)) {
            LEFTOVERS.remove(e);
            if (e.isRemoved()) continue;
            if (e instanceof EntityNPCInterface npc) {
                if (groups.squadsOf(npc.getUUID()).isEmpty()) {
                    npc.getPersistentData().remove(TAG);
                    sendAway(npc, null);
                }
            } else if (!data.ownsWeapon(e.getUUID())) {
                e.discard();
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 10 != 0) return;
        MinecraftServer server = e.getServer();
        leftovers(server);
        ReinforcementData data = ReinforcementData.get(server);
        boolean second = clock % 20 == 0;
        GroupData groups = GroupData.get(server.overworld());
        for (UUID owner : new ArrayList<>(data.forces.keySet())) {
            ReinforcementData.Force force = data.forces.get(owner);
            ServerPlayer p = server.getPlayerList().getPlayer(owner);
            if (!groups.exists(force.squad) || groups.members(force.squad).isEmpty()) {
                disband(server, owner, "Your reinforcements are gone");
                continue;
            }
            if (p == null) {
                // A short grace: the world may only just be loading, or the player is reconnecting.
                if (second && OFFLINE.merge(owner, 1, Integer::sum) >= OFFLINE_SECONDS) disband(server, owner, "");
                continue;
            }
            OFFLINE.remove(owner);
            if (force.marching) {
                March m = MARCHES.get(owner);
                // Left over from before the world was reloaded.
                if (m != null && server.getLevel(m.level.dimension()) != m.level) m = null;
                if (m == null || march(p, m)) {
                    MARCHES.remove(owner);
                    force.marching = false;
                    data.setDirty();
                    arrive(p, force, m, m != null && wasOrdered(m));
                }
                continue;
            }
            if (!second) continue;
            force.age++;
            if (fighting(p, owner, loaded(server, groups.members(force.squad)))) {
                force.fought = true;
                force.quiet = 0;
            } else {
                force.quiet++;
            }
            data.setDirty();
            if (force.fought && force.quiet >= QUIET_SECONDS) {
                disband(server, owner, "The battle is over: your reinforcements are heading home");
            } else if (!force.fought && force.age >= NO_BATTLE_SECONDS) {
                disband(server, owner, "No battle came: your reinforcements are heading home");
            } else if (force.age >= MAX_SECONDS) {
                disband(server, owner, "Your reinforcements cannot stay any longer");
            }
        }
        if (second) HURT.clear();
    }
}
