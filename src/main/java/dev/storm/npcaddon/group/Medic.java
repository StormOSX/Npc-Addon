package dev.storm.npcaddon.group;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Squad medics never fight. They heal the most wounded squad member in reach (themselves included), walk to wounded
 * members that are out of reach and back away from enemies that come close. Orders that walk the squad somewhere come
 * first. Healing is done here, so the NPC's own job and settings stay as they are.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Medic {
    /** Present while the medic is moving on its own; holds the return-to-home setting to restore afterwards. */
    private static final String TENDING = "npcaddon_medic_return";
    private static final double HEAL_RANGE = 10;
    private static final double SEEK_RANGE = 24;
    private static final double KEEP_FROM_ENEMY = 7;
    /** A member below this health fraction is worth walking to. */
    private static final float SEEK_BELOW = 0.8f;
    private static final float HEAL_FRACTION = 0.1f;
    private static final float MIN_HEAL = 2;
    private static final int HEAL_TICKS = 40;
    private static final double SPEED = 1.2;
    /** Medic -> clock value until which it is shown as healing. */
    private static final Map<UUID, Integer> HEALED = new HashMap<>();
    private static int clock;

    private Medic() {}

    public static boolean isMedic(EntityNPCInterface npc) {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        return GroupData.get(level.getServer().overworld()).isMedic(npc.getUUID());
    }

    /** True while the medic is away from its order, tending to the wounded or keeping clear of enemies. */
    static boolean isTending(EntityNPCInterface npc) {
        return npc.getPersistentData().contains(TENDING);
    }

    static boolean isHealing(EntityNPCInterface npc) {
        return isTending(npc) || HEALED.getOrDefault(npc.getUUID(), 0) > clock;
    }

    /** Puts the medic's settings back: used before every new order. */
    public static void end(EntityNPCInterface npc) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(TENDING)) return;
        npc.ais.returnToStart = pd.getBoolean(TENDING);
        pd.remove(TENDING);
        npc.getNavigation().stop();
    }

    private static void begin(EntityNPCInterface npc) {
        if (isTending(npc)) return;
        npc.getPersistentData().putBoolean(TENDING, npc.ais.returnToStart);
        npc.ais.returnToStart = false;
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide
                && isMedic(npc)) {
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
                        if (data.isMedic(id)) update(npc, level, data);
                        else if (isTending(npc)) end(npc);
                        break;
                    }
                }
            }
        }
    }

    private static void update(EntityNPCInterface npc, ServerLevel level, GroupData data) {
        if (npc.getTarget() != null) npc.setTarget(null);
        if (npc.isInteracting()) return;
        boolean free = !Retreat.isRetreating(npc) && !GroupMover.isWalking(npc) && !SiegeCrew.isCrew(npc)
            && !npc.isPassenger();
        LivingEntity enemy = free ? nearestEnemy(npc, level) : null;
        if (enemy != null) {
            begin(npc);
            npc.getNavigation().stop();
            RangedSupport.stepAway(npc, level, enemy);
        }
        // Spread the medics' work over different ticks.
        int phase = clock + (npc.getId() & 31);
        if (phase % 10 != 0) return;

        EntityNPCInterface inReach = null;
        EntityNPCInterface seen = null;
        for (String squad : data.squadsOf(npc.getUUID())) {
            for (UUID id : data.members(squad)) {
                if (!(level.getEntity(id) instanceof EntityNPCInterface mate) || !mate.isAlive()
                        || mate.getHealth() >= mate.getMaxHealth()) continue;
                double d = npc.distanceTo(mate);
                if (d > SEEK_RANGE) continue;
                if (d <= HEAL_RANGE && (mate == npc || npc.hasLineOfSight(mate))) {
                    if (inReach == null || fraction(mate) < fraction(inReach)) inReach = mate;
                } else if (fraction(mate) < SEEK_BELOW && (seen == null || fraction(mate) < fraction(seen))) {
                    seen = mate;
                }
            }
        }
        if (inReach != null && HEALED.getOrDefault(npc.getUUID(), 0) <= clock + 10) {
            heal(npc, level, inReach);
        }
        if (!free || enemy != null) return;
        // Walk to someone out of reach when nobody here needs help, or when they are much worse off.
        if (seen != null && npc.ais.getMovingType() != 2
                && (inReach == null || fraction(seen) < fraction(inReach) - 0.3f)) {
            begin(npc);
            npc.getNavigation().moveTo(seen, SPEED);
        } else if (inReach != null && isTending(npc)) {
            npc.getNavigation().stop();
        } else if (inReach == null) {
            end(npc);
        }
    }

    private static float fraction(LivingEntity e) {
        return e.getHealth() / e.getMaxHealth();
    }

    private static void heal(EntityNPCInterface medic, ServerLevel level, EntityNPCInterface patient) {
        HEALED.put(medic.getUUID(), clock + HEAL_TICKS + 10);
        patient.heal(Math.max(MIN_HEAL, patient.getMaxHealth() * HEAL_FRACTION));
        if (patient != medic) medic.getLookControl().setLookAt(patient, 30.0F, 30.0F);
        medic.swing(InteractionHand.MAIN_HAND);
        level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3,
            patient.getZ(), 3, 0.3, 0.2, 0.3, 0);
    }

    private static LivingEntity nearestEnemy(EntityNPCInterface npc, ServerLevel level) {
        LivingEntity best = null;
        double bestD = KEEP_FROM_ENEMY * KEEP_FROM_ENEMY;
        for (LivingEntity t : level.getEntitiesOfClass(LivingEntity.class,
                npc.getBoundingBox().inflate(KEEP_FROM_ENEMY),
                t -> t != npc && t.isAlive() && MobDefense.isThreat(npc, t, level))) {
            double d = npc.distanceToSqr(t);
            if (d < bestD && npc.hasLineOfSight(t)) {
                bestD = d;
                best = t;
            }
        }
        return best;
    }
}
