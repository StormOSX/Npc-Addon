package dev.storm.npcaddon.group;

import dev.storm.npcaddon.territory.Territory;
import dev.storm.npcaddon.territory.TerritoryData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Territory defence: a claiming faction's NPCs inside the claim that have no orders go after intruders, and walk back
 * to where they stood once the claim is clear. What they were doing before is kept in their persistent data.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Guard {
    private static final String TAG = "npcaddon_guard";
    private static final double SPEED = 1.2;
    private static final int RETURN_TICKS = 20 * 60;
    private static final double SWITCH_MARGIN = 3;
    /** NPCs out defending (or walking back), and the dimension they are in. */
    private static final Map<UUID, ServerLevel> GUARDS = new HashMap<>();
    private static final Map<UUID, Integer> RETURNING = new HashMap<>();
    private static int clock;

    private Guard() {}

    static boolean isGuarding(EntityNPCInterface npc) {
        return npc.getPersistentData().contains(TAG);
    }

    /** Puts the NPC's settings back where it stands: used before every new order. */
    public static void end(EntityNPCInterface npc) {
        CompoundTag pd = npc.getPersistentData();
        if (!pd.contains(TAG)) return;
        CompoundTag state = pd.getCompound(TAG);
        pd.remove(TAG);
        GUARDS.remove(npc.getUUID());
        RETURNING.remove(npc.getUUID());
        npc.getNavigation().stop();
        npc.ais.setStartPos(BlockPos.of(state.getLong("home")));
        npc.ais.returnToStart = state.getBoolean("return");
        npc.ais.setMovingType(state.getInt("moving"));
    }

    private static void begin(EntityNPCInterface npc, ServerLevel level, TerritoryData.Claim claim) {
        CompoundTag state = new CompoundTag();
        state.putInt("claim", claim.id);
        state.putLong("home", npc.ais.startPos().asLong());
        state.putBoolean("return", npc.ais.returnToStart);
        state.putInt("moving", npc.ais.getMovingType());
        Retreat.putVec(state, "stood", npc.position());
        npc.getPersistentData().put(TAG, state);
        npc.ais.returnToStart = false;
        npc.ais.setMovingType(0);
        GroupMover.haltPatrol(npc);
        GUARDS.put(npc.getUUID(), level);
    }

    /** Whether the NPC may drop what it is doing to defend: nobody's orders come second to this. */
    private static boolean free(EntityNPCInterface npc, GroupData data) {
        if (isGuarding(npc) || npc.getTarget() != null || npc.isInteracting()) return false;
        if (GroupMover.hasOrder(npc) || ShieldWall.isAssigned(npc)) return false;
        if (Retreat.isRetreating(npc) || SiegeCrew.isCrew(npc) || Mounts.isMounting(npc)
                || Reinforcements.isMarching(npc) || Departures.isLeaving(npc)) return false;
        UUID id = npc.getUUID();
        if (data.isMedic(id)) return false;
        List<String> squads = data.squadsOf(id);
        if (squads.isEmpty()) return true;
        Stance stance = data.stanceOfMember(id);
        // A squad that is patrolling or posted somewhere is under orders.
        return stance != Stance.HOLD_FIRE && stance != Stance.PASSIVE && !GroupMover.isPosted(npc)
            && npc.ais.getMovingType() != 2;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 10 != 0) return;
        MinecraftServer server = e.getServer();
        if (clock % 20 == 0) rouse(server);
        update(server);
    }

    /** Sends idle defenders of every claim with intruders into action. */
    private static void rouse(MinecraftServer server) {
        GroupData data = GroupData.get(server.overworld());
        for (TerritoryData.Claim claim : TerritoryData.get(server).claims()) {
            if (claim.faction == Territory.NO_FACTION) continue;
            ServerLevel level = Territory.level(server, claim);
            if (level == null || !level.hasChunkAt(claim.pos)) continue;
            if (Territory.intruders(level, claim).isEmpty()) continue;
            for (EntityNPCInterface npc : level.getEntitiesOfClass(EntityNPCInterface.class, Territory.box(claim),
                    n -> n.isAlive() && n.faction.id == claim.faction && Territory.inside(claim, n.position()))) {
                if (free(npc, data)) begin(npc, level, claim);
            }
        }
    }

    private static void update(MinecraftServer server) {
        if (GUARDS.isEmpty()) return;
        TerritoryData territory = TerritoryData.get(server);
        Iterator<Map.Entry<UUID, ServerLevel>> it = new HashMap<>(GUARDS).entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ServerLevel> en = it.next();
            if (en.getValue().getServer() != server) {
                GUARDS.remove(en.getKey());
                continue;
            }
            if (!(en.getValue().getEntity(en.getKey()) instanceof EntityNPCInterface npc)) continue;
            if (!npc.isAlive()) end(npc);
            if (!isGuarding(npc)) {
                GUARDS.remove(en.getKey());
                RETURNING.remove(en.getKey());
                continue;
            }
            defend(npc, en.getValue(), territory);
        }
    }

    private static void defend(EntityNPCInterface npc, ServerLevel level, TerritoryData territory) {
        CompoundTag state = npc.getPersistentData().getCompound(TAG);
        TerritoryData.Claim claim = territory.claim(state.getInt("claim"));
        List<LivingEntity> intruders = claim == null || claim.faction != npc.faction.id
            || Territory.level(level.getServer(), claim) != level ? List.of() : Territory.intruders(level, claim);
        LivingEntity current = npc.getTarget();
        if (current != null && !current.isAlive()) current = null;

        LivingEntity best = null;
        for (LivingEntity i : intruders) {
            if (best == null || npc.distanceToSqr(i) < npc.distanceToSqr(best)) best = i;
        }
        if (best != null) {
            RETURNING.remove(npc.getUUID());
            // Stay on the current target unless another is clearly closer.
            if (current != null && intruders.contains(current)
                    && npc.distanceTo(best) > npc.distanceTo(current) - SWITCH_MARGIN) {
                best = current;
            }
            double reach = Math.max(8, npc.stats.aggroRange * 0.8);
            if (npc.distanceTo(best) <= reach || best == current && npc.distanceTo(best) <= npc.stats.aggroRange) {
                if (npc.getTarget() != best) npc.setTarget(best);
            } else {
                // Too far for CustomNPCs to keep as a target: walk closer first.
                if (npc.getTarget() != null) npc.setTarget(null);
                if (npc.getNavigation().isDone() || clock % 40 == 0) {
                    GroupMover.head(npc, level, best.position(), 0, SPEED);
                }
            }
            return;
        }
        // Whatever it still fights (an attacker from outside) is left to finish.
        if (current != null) return;
        Vec3 stood = Retreat.vec(state, "stood");
        int ticks = RETURNING.merge(npc.getUUID(), 10, Integer::sum);
        double flat = npc.position().subtract(stood).multiply(1, 0, 1).length();
        if (flat <= 1.5 && Math.abs(npc.getY() - stood.y) <= 2.5 || ticks > RETURN_TICKS) {
            end(npc);
        } else if (npc.getNavigation().isDone()) {
            GroupMover.head(npc, level, stood, ticks / 200, 1.0);
        }
    }

    /** A guard saved mid-defence carries on (and goes back) after a reload. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof EntityNPCInterface npc && e.getLevel() instanceof ServerLevel level
                && isGuarding(npc)) {
            GUARDS.put(npc.getUUID(), level);
        }
    }
}
