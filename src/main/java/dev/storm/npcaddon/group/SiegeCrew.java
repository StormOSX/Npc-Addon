package dev.storm.npcaddon.group;

import dev.storm.npcaddon.Config;
import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.aw.AwSiege;
import dev.storm.npcaddon.faction.Relation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Squad NPCs crewing Ancient Warfare siege weapons. A crew walks to its weapon, sits at it and fires from where it
 * stands: at hostiles it picks itself (aggressive/defensive stance), or at an ordered spot. Any other squad order
 * releases the crew. The weapon and any ordered bombardment are kept in the NPC's persistent data, so both resume after
 * a reload. No AW types appear here.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class SiegeCrew {
    private static final String WEAPON = "npcaddon_crew";
    private static final String SAVED_RETURN = "npcaddon_crew_return";
    private static final String SAVED_MOVING = "npcaddon_crew_moving";
    private static final String SPOT_X = "npcaddon_crew_spot_x";
    private static final String SPOT_Y = "npcaddon_crew_spot_y";
    private static final String SPOT_Z = "npcaddon_crew_spot_z";
    private static final String SPOT_RADIUS = "npcaddon_crew_spot_radius";

    private static final int PERIOD = 10;
    private static final int WALK_TIMEOUT = 20 * 30;
    private static final int MISSING_TIMEOUT = 20 * 5;
    private static final int RELOAD_TICKS = 80;
    private static final double MOUNT_DIST = 2.0;
    /** Automatic fire holds back when a friend is this close to the target. */
    private static final double SAFETY_RADIUS = 5;
    private static final int MAX_SIGHT_CHECKS = 8;

    private static final class Crew {
        final ServerLevel level;
        final UUID weapon;
        UUID owner;
        /** Non-null: bombard here instead of picking targets. */
        Vec3 spot;
        double radius;
        int walking;
        int missing;
        int reload;

        Crew(ServerLevel level, UUID weapon, UUID owner) {
            this.level = level;
            this.weapon = weapon;
            this.owner = owner;
        }
    }

    private static final Map<UUID, Crew> CREWS = new HashMap<>();
    private static int clock;

    private SiegeCrew() {}

    public static boolean isCrew(EntityNPCInterface npc) {
        return CREWS.containsKey(npc.getUUID());
    }

    public static boolean isClaimed(Entity weapon) {
        for (Crew c : CREWS.values()) if (c.weapon.equals(weapon.getUUID())) return true;
        return false;
    }

    public static String describe(EntityNPCInterface npc) {
        Crew c = CREWS.get(npc.getUUID());
        if (c == null) return null;
        if (npc.getVehicle() == null) return "To weapon";
        return c.spot != null ? "Bombard" : "Crewing";
    }

    /** Sends the NPC to crew this weapon. Replaces whatever it was doing. */
    public static void man(EntityNPCInterface npc, Entity weapon, ServerPlayer owner) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        GroupMover.cancel(npc);
        npc.setTarget(null);
        CompoundTag tag = npc.getPersistentData();
        tag.putBoolean(SAVED_RETURN, npc.ais.returnToStart);
        tag.putInt(SAVED_MOVING, npc.ais.getMovingType());
        tag.putUUID(WEAPON, weapon.getUUID());
        npc.ais.returnToStart = false;
        npc.ais.setMovingType(0);
        GroupMover.haltPatrol(npc);
        CREWS.put(npc.getUUID(), new Crew(level, weapon.getUUID(), owner == null ? null : owner.getUUID()));
    }

    /** Takes a crew off ordered bombardment, back to picking its own targets. */
    public static void resumeAuto(EntityNPCInterface npc) {
        Crew c = CREWS.get(npc.getUUID());
        if (c == null) return;
        c.spot = null;
        forgetSpot(npc.getPersistentData());
    }

    private static void forgetSpot(CompoundTag tag) {
        tag.remove(SPOT_X);
        tag.remove(SPOT_Y);
        tag.remove(SPOT_Z);
        tag.remove(SPOT_RADIUS);
    }

    /**
     * Orders the crew to shoot at (or randomly around) a spot until another order.
     *
     * @return false if the NPC is no crew or the spot is outside its weapon's range
     */
    public static boolean bombard(EntityNPCInterface npc, Vec3 spot, double radius) {
        Crew c = CREWS.get(npc.getUUID());
        if (c == null) return false;
        Entity weapon = c.level.getEntity(c.weapon);
        if (weapon == null || npc.level() != c.level) return false;
        double d = Math.sqrt(weapon.distanceToSqr(spot));
        if (d + radius < AwSiege.minRange(weapon) || d - radius > AwSiege.maxRange(weapon)) return false;
        c.spot = spot;
        c.radius = radius;
        CompoundTag tag = npc.getPersistentData();
        tag.putDouble(SPOT_X, spot.x);
        tag.putDouble(SPOT_Y, spot.y);
        tag.putDouble(SPOT_Z, spot.z);
        tag.putDouble(SPOT_RADIUS, radius);
        return true;
    }

    /** Gets the NPC out of its weapon and restores its AI settings. Does nothing for an NPC that is no crew. */
    public static void release(EntityNPCInterface npc) {
        Crew c = CREWS.remove(npc.getUUID());
        CompoundTag tag = npc.getPersistentData();
        if (c == null && !tag.hasUUID(WEAPON)) return;
        Entity vehicle = npc.getVehicle();
        if (AwSiege.isWeapon(vehicle)) AwSiege.end(vehicle, npc);
        if (npc.getVehicle() == vehicle && AwSiege.isWeapon(vehicle)) npc.stopRiding();
        npc.getNavigation().stop();
        npc.ais.setStartPos(npc.blockPosition());
        npc.ais.returnToStart = tag.getBoolean(SAVED_RETURN);
        npc.ais.setMovingType(tag.getInt(SAVED_MOVING));
        tag.remove(WEAPON);
        tag.remove(SAVED_RETURN);
        tag.remove(SAVED_MOVING);
        forgetSpot(tag);
    }

    /** Crews do not chase or melee: they work their weapon. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof EntityNPCInterface npc && isCrew(npc)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.getEntity() instanceof EntityNPCInterface npc
                && npc.getPersistentData().hasUUID(WEAPON)) {
            UUID weapon = npc.getPersistentData().getUUID(WEAPON);
            Crew old = CREWS.get(npc.getUUID());
            // A chunk reload keeps the running entry; a new session rebuilds it from the saved data.
            if (old == null || old.level != level || !old.weapon.equals(weapon)) {
                Crew c = new Crew(level, weapon, null);
                CompoundTag tag = npc.getPersistentData();
                if (tag.contains(SPOT_RADIUS)) {
                    c.spot = new Vec3(tag.getDouble(SPOT_X), tag.getDouble(SPOT_Y), tag.getDouble(SPOT_Z));
                    c.radius = tag.getDouble(SPOT_RADIUS);
                }
                CREWS.put(npc.getUUID(), c);
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % PERIOD != 0 || CREWS.isEmpty()) return;
        for (Map.Entry<UUID, Crew> en : new ArrayList<>(CREWS.entrySet())) {
            Crew c = en.getValue();
            if (!(c.level.getEntity(en.getKey()) instanceof EntityNPCInterface npc)) continue; // unloaded
            if (!npc.isAlive()) {
                release(npc);
                continue;
            }
            tickCrew(npc, c);
        }
    }

    private static void tickCrew(EntityNPCInterface npc, Crew c) {
        Entity weapon = c.level.getEntity(c.weapon);
        if (weapon == null || !weapon.isAlive() || !AwSiege.isWeapon(weapon)) {
            if ((c.missing += PERIOD) >= MISSING_TIMEOUT) give(npc, c, "My siege weapon is gone");
            return;
        }
        c.missing = 0;

        if (npc.getVehicle() != weapon) {
            AABB reach = weapon.getBoundingBox().inflate(MOUNT_DIST);
            if (reach.intersects(npc.getBoundingBox())) {
                npc.getNavigation().stop();
                if (!AwSiege.begin(weapon, npc)) give(npc, c, "I cannot use that weapon");
                else c.walking = 0;
            } else if ((c.walking += PERIOD) >= WALK_TIMEOUT) {
                give(npc, c, "I cannot reach the weapon");
            } else if (npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(weapon.getX(), weapon.getY(), weapon.getZ(), 1.1);
            }
            return;
        }

        if (c.reload > 0) {
            c.reload -= PERIOD;
            return;
        }
        LivingEntity target = c.spot != null ? marker(c) : autoTarget(npc, c, weapon);
        if (target != null && AwSiege.fire(weapon, npc, target, Config.UNLIMITED_SIEGE_AMMO.get())) {
            c.reload = RELOAD_TICKS;
        }
    }

    private static void give(EntityNPCInterface npc, Crew c, String why) {
        ServerPlayer owner = c.owner == null ? null : c.level.getServer().getPlayerList().getPlayer(c.owner);
        if (owner != null) owner.sendSystemMessage(Component.literal("<" + npc.getName().getString() + "> " + why));
        release(npc);
    }

    /**
     * Weapons aim at living things, so a bombardment aims at an armor stand that is never added to the world, placed on
     * the ordered spot or at a random point within the radius.
     */
    private static LivingEntity marker(Crew c) {
        Vec3 at = c.spot;
        if (c.radius > 0) {
            double angle = c.level.random.nextDouble() * Math.PI * 2;
            double r = c.radius * Math.sqrt(c.level.random.nextDouble());
            double x = at.x + Math.cos(angle) * r;
            double z = at.z + Math.sin(angle) * r;
            BlockPos top = c.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                BlockPos.containing(x, at.y, z));
            // Under a roof the surface height would put the aim point on top of the building.
            double y = Math.abs(top.getY() - at.y) <= 6 ? top.getY() : at.y;
            at = new Vec3(x, y, z);
        }
        return new ArmorStand(c.level, at.x, at.y, at.z);
    }

    private static LivingEntity autoTarget(EntityNPCInterface npc, Crew c, Entity weapon) {
        GroupData data = GroupData.get(c.level.getServer().overworld());
        Stance st = data.stanceOfMember(npc.getUUID());
        if (st != Stance.AGGRESSIVE && st != Stance.DEFENSIVE) return null;
        double min = AwSiege.minRange(weapon);
        double max = AwSiege.maxRange(weapon);
        AABB box = weapon.getBoundingBox().inflate(max, 32, max);
        List<LivingEntity> found = c.level.getEntitiesOfClass(LivingEntity.class, box, t -> {
            if (t == npc || !t.isAlive() || !isHostile(npc, t, c.level)) return false;
            double d = weapon.distanceToSqr(t);
            return d >= min * min && d <= max * max;
        });
        found.sort(Comparator.comparingDouble(weapon::distanceToSqr));
        int checks = 0;
        for (LivingEntity t : found) {
            if (friendNear(npc, t, c.level)) continue;
            if (AwSiege.canAttack(weapon, t)) return t;
            if (++checks >= MAX_SIGHT_CHECKS) break;
        }
        return null;
    }

    private static boolean isHostile(EntityNPCInterface npc, LivingEntity t, ServerLevel level) {
        if (t instanceof Enemy) return true;
        if (t instanceof EntityNPCInterface o) return npc.faction.isAggressiveToNpc(o);
        String aw = AwRelations.factionOf(t);
        return aw != null && AwRelations.get(level.getServer(), npc.faction.id, aw) == Relation.HOSTILE;
    }

    private static boolean friendNear(EntityNPCInterface npc, LivingEntity target, ServerLevel level) {
        return !level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SAFETY_RADIUS),
            f -> f != target && f.isAlive()
                && (f instanceof Player p && !p.isCreative() && !p.isSpectator()
                    || f instanceof EntityNPCInterface o && !npc.faction.isAggressiveToNpc(o))).isEmpty();
    }
}
