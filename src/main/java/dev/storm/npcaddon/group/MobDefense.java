package dev.storm.npcaddon.group;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

/**
 * Squad NPCs go for the nearest enemy they can see within their aggro range, and keep re-checking while they fight, so
 * they turn to whoever is closest now. Explicit attack orders are never changed. Hold-fire and passive squads stay out
 * of it, and NPCs walking a formation are left alone; area attacks choose their own targets the same way.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class MobDefense {
    private static final int MAX_RANGE = 48;
    /** A new enemy must be this much closer than the current one, so NPCs do not flicker between two. */
    static final double SWITCH_MARGIN = 1.5;
    private static int clock;

    private MobDefense() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 10 != 0) return;
        MinecraftServer server = e.getServer();
        GroupData data = GroupData.get(server.overworld());
        for (String name : data.names()) {
            Stance st = data.stance(name);
            if (st != Stance.AGGRESSIVE && st != Stance.DEFENSIVE) continue;
            for (UUID id : data.members(name)) {
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof EntityNPCInterface npc && npc.isAlive()) {
                        scan(npc, level);
                        break;
                    }
                }
            }
        }
    }

    private static void scan(EntityNPCInterface npc, ServerLevel level) {
        if (npc.isInteracting() || SiegeCrew.isCrew(npc) || GroupMover.hasArea(npc)
                || GroupMover.isBusyMoving(npc) || Retreat.isRetreating(npc)) return;
        LivingEntity current = npc.getTarget();
        if (current != null && !current.isAlive()) current = null;
        if (current != null && GroupMover.isOrderedTarget(npc, current)) return;
        double range = Math.max(1, Math.min(MAX_RANGE, npc.stats.aggroRange));
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity m : level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                m -> m != npc && m.isAlive() && isThreat(npc, m, level))) {
            double d = npc.distanceToSqr(m);
            if (d < bestD && npc.hasLineOfSight(m)) {
                bestD = d;
                best = m;
            }
        }
        if (best == null || best == current) return;
        if (current == null || !npc.hasLineOfSight(current)
                || npc.distanceTo(best) < npc.distanceTo(current) - SWITCH_MARGIN) {
            npc.setTarget(best);
        }
    }

    static boolean isThreat(EntityNPCInterface npc, LivingEntity m, ServerLevel level) {
        if (m instanceof Player p) {
            return !p.isCreative() && !p.isSpectator() && npc.faction.isAggressiveToPlayer(p);
        }
        // Also anything that is going for this NPC, such as an angered animal.
        return Hostility.isFoe(npc, m, level) || (m instanceof Mob mob && mob.getTarget() == npc
            && !(m instanceof EntityNPCInterface));
    }
}
