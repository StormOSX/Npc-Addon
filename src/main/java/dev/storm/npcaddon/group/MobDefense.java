package dev.storm.npcaddon.group;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

/**
 * Squad NPCs are hostile to vanilla hostile mobs (zombies, skeletons, creepers, ...): once a second, an idle NPC
 * picks the nearest one it can see within its aggro range. Hold-fire and passive squads stay out of it, and NPCs
 * walking a formation or running an area attack are left alone.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class MobDefense {
    private static final int MAX_RANGE = 48;
    private static int clock;

    private MobDefense() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 20 != 0) return;
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
        if (npc.getTarget() != null || npc.isInteracting() || GroupMover.hasArea(npc) || GroupMover.isBusyMoving(npc)) return;
        double range = Math.max(1, Math.min(MAX_RANGE, npc.stats.aggroRange));
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity m : level.getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                m -> m instanceof Enemy && m.isAlive() && npc.hasLineOfSight(m))) {
            double d = npc.distanceToSqr(m);
            if (d < range * range && d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best != null) npc.setTarget(best);
    }
}
