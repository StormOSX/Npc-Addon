package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cavalry: squad members ride horses or other CustomNPCs. Vanilla already lets a mob rider steer a horse with its own
 * navigation. A CustomNPCs mount does not, so its AI is switched off while ridden and it is sent wherever its rider is
 * heading. Its settings are kept in its persistent data and restored when the rider gets off.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Mounts {
    /** On a ridden CustomNPCs mount: its own AI settings. */
    private static final String RIDDEN = "npcaddon_mount_ai";
    /** Set on a CustomNPC once it has been ridden, so the squad's Mount order uses it again. */
    private static final String MOUNT = "npcaddon_mount";
    private static final int WALK_TIMEOUT = 20 * 30;
    private static final double REACH = 1.5;
    private static final double SPEED = 1.2;

    private static final class Pending {
        final ServerLevel level;
        final UUID mount;
        int ticks;

        Pending(ServerLevel level, UUID mount) {
            this.level = level;
            this.mount = mount;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Set<UUID> NPC_MOUNTS = new HashSet<>();
    private static int clock;

    private Mounts() {}

    private static boolean isHorse(Entity e) {
        return e instanceof Horse || e instanceof Donkey || e instanceof Mule || e instanceof SkeletonHorse
            || e instanceof ZombieHorse;
    }

    /**
     * Whether a squad member could be sent to ride this now. Any CustomNPC outside the squads can be picked by hand;
     * the squad order only takes those ridden before (or with CustomNPCs' mount control), so it does not saddle up
     * villagers.
     */
    public static boolean isFreeMount(Entity e, boolean pickedByHand) {
        if (e == null || !e.isAlive() || e.isVehicle() || e.isPassenger() || isClaimed(e)) return false;
        if (isHorse(e)) return !((AbstractHorse) e).isBaby();
        if (!(e instanceof EntityNPCInterface npc) || !(e.level() instanceof ServerLevel level)) return false;
        if (!GroupData.get(level.getServer().overworld()).squadsOf(e.getUUID()).isEmpty()) return false;
        if (Recruits.type(npc) != null) return false;
        return pickedByHand || npc.ais.mountControl || npc.getPersistentData().getBoolean(MOUNT);
    }

    /**
     * Whether a wand click on this means "ride it": a horse, or a CustomNPC outside the squads that neither side is
     * hostile to. Hostile NPCs are attacked instead.
     */
    static boolean wandMounts(ServerLevel level, GroupData data, String squad, Entity target, boolean operator) {
        if (!isFreeMount(target, operator)) return false;
        if (!(target instanceof EntityNPCInterface mount)) return true;
        for (UUID id : data.members(squad)) {
            if (level.getEntity(id) instanceof EntityNPCInterface member
                    && (member.faction.isAggressiveToNpc(mount) || mount.faction.isAggressiveToNpc(member))) {
                return false;
            }
        }
        return true;
    }

    static boolean isClaimed(Entity e) {
        for (Pending p : PENDING.values()) if (p.mount.equals(e.getUUID())) return true;
        return false;
    }

    public static boolean isMounted(EntityNPCInterface npc) {
        Entity v = npc.getVehicle();
        return v != null && (isHorse(v) || v instanceof EntityNPCInterface);
    }

    /** True while the NPC rides a mount or is walking to one. */
    public static boolean isBusy(EntityNPCInterface npc) {
        return isMounted(npc) || isMounting(npc);
    }

    static boolean isMounting(EntityNPCInterface npc) {
        return PENDING.containsKey(npc.getUUID());
    }

    /** Where the NPC stands on the ground: its mount's position when it rides one. */
    static Vec3 position(EntityNPCInterface npc) {
        return isMounted(npc) ? npc.getVehicle().position() : npc.position();
    }

    /** Sends the NPC to get on this mount, dropping what it was doing. */
    public static void sendTo(EntityNPCInterface npc, Entity mount) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        GroupMover.cancel(npc);
        npc.setTarget(null);
        PENDING.put(npc.getUUID(), new Pending(level, mount.getUUID()));
        npc.getNavigation().moveTo(mount, SPEED);
    }

    /** Stops walking to a mount (another order arrived). A rider stays mounted. */
    static void abandon(EntityNPCInterface npc) {
        PENDING.remove(npc.getUUID());
    }

    public static void dismount(EntityNPCInterface npc) {
        abandon(npc);
        if (!isMounted(npc)) return;
        Entity mount = npc.getVehicle();
        npc.getNavigation().stop();
        npc.stopRiding();
        if (mount instanceof EntityNPCInterface m) restore(m);
        npc.ais.setStartPos(npc.blockPosition());
    }

    private static void mount(EntityNPCInterface npc, Entity mount) {
        npc.getNavigation().stop();
        if (mount instanceof EntityNPCInterface m) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("return", m.ais.returnToStart);
            tag.putInt("moving", m.ais.getMovingType());
            tag.putBoolean("control", m.ais.mountControl);
            m.getPersistentData().put(RIDDEN, tag);
            m.getPersistentData().putBoolean(MOUNT, true);
            m.ais.returnToStart = false;
            m.ais.setMovingType(0);
            // With mount control on, the mount waits for player-style input that an NPC rider never gives.
            m.ais.mountControl = false;
            m.setTarget(null);
            m.getNavigation().stop();
        }
        if (npc.startRiding(mount, true)) {
            if (mount instanceof EntityNPCInterface) NPC_MOUNTS.add(mount.getUUID());
        } else if (mount instanceof EntityNPCInterface m) {
            restore(m);
        }
    }

    private static void restore(EntityNPCInterface mount) {
        CompoundTag pd = mount.getPersistentData();
        if (!pd.contains(RIDDEN)) return;
        CompoundTag tag = pd.getCompound(RIDDEN);
        pd.remove(RIDDEN);
        NPC_MOUNTS.remove(mount.getUUID());
        mount.getNavigation().stop();
        mount.ais.setStartPos(mount.blockPosition());
        mount.ais.mountControl = tag.getBoolean("control");
        mount.ais.returnToStart = tag.getBoolean("return");
        mount.ais.setMovingType(tag.getInt("moving"));
    }

    /** A ridden CustomNPCs mount does not pick fights of its own. */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide
                && npc.getPersistentData().contains(RIDDEN)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof EntityNPCInterface npc
                && npc.getPersistentData().contains(RIDDEN)) {
            NPC_MOUNTS.add(npc.getUUID());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        alignRiders(e.getServer());
        if (++clock % 5 != 0) return;
        for (Map.Entry<UUID, Pending> en : new ArrayList<>(PENDING.entrySet())) {
            Pending p = en.getValue();
            if (!(p.level.getEntity(en.getKey()) instanceof EntityNPCInterface npc)) continue;
            Entity mount = p.level.getEntity(p.mount);
            p.ticks += 5;
            if (!npc.isAlive() || mount == null || !mount.isAlive() || mount.isVehicle() || p.ticks > WALK_TIMEOUT) {
                PENDING.remove(en.getKey());
            } else if (npc.getBoundingBox().intersects(mount.getBoundingBox().inflate(REACH))) {
                PENDING.remove(en.getKey());
                mount(npc, mount);
            } else if (p.ticks % 10 == 0) {
                npc.getNavigation().moveTo(mount, SPEED);
            }
        }
        steerNpcMounts(e.getServer());
    }

    /** Riders look the way their mount is heading unless they are busy with something. */
    private static void alignRiders(MinecraftServer server) {
        GroupData data = GroupData.get(server.overworld());
        for (String name : data.names()) {
            for (UUID id : data.members(name)) {
                for (ServerLevel level : server.getAllLevels()) {
                    if (!(level.getEntity(id) instanceof EntityNPCInterface npc)) continue;
                    if (isMounted(npc) && npc.getTarget() == null && !npc.isInteracting()
                            && npc.getVehicle() instanceof LivingEntity mount) {
                        GroupMover.turn(npc, mount.yBodyRot);
                    }
                    break;
                }
            }
        }
    }

    private static void steerNpcMounts(MinecraftServer server) {
        for (UUID id : new ArrayList<>(NPC_MOUNTS)) {
            for (ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(id) instanceof EntityNPCInterface mount) || !mount.isAlive()) continue;
                if (!(mount.getFirstPassenger() instanceof EntityNPCInterface rider) || !rider.isAlive()) {
                    restore(mount);
                    break;
                }
                Path path = rider.getNavigation().getPath();
                if (path == null || path.isDone()) {
                    mount.getNavigation().stop();
                } else if (mount.getNavigation().isDone() || mount.getNavigation().getTargetPos() == null
                        || mount.getNavigation().getTargetPos().distSqr(path.getTarget()) > 2) {
                    var to = path.getTarget();
                    mount.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, SPEED);
                }
                break;
            }
        }
    }
}
