package dev.storm.npcaddon.aw;

import dev.storm.npcaddon.faction.Relation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Makes the stored CustomNPCs <-> Ancient Warfare relations real:
 * - allied pairs never target each other (this also stops retaliation),
 * - hostile pairs go for each other: once a second, idle NPCs of a hostile pair pick the nearest visible member of the
 *   other side. AW NPCs do not know CustomNPCs factions at all, so this is what makes them fight.
 * Neutral pairs are left to each mod's own behaviour. No AW types appear in this class.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class AwEvents {
    private static final double AW_RANGE = 24;
    private static final double MAX_RANGE = 48;
    private static int clock;

    private AwEvents() {}

    /** Relation between a CustomNPCs NPC and an AW NPC in either order; neutral for any other pair. */
    private static Relation relationOf(Entity a, Entity b, ServerLevel level) {
        EntityNPCInterface npc;
        Entity other;
        if (a instanceof EntityNPCInterface n) {
            npc = n;
            other = b;
        } else if (b instanceof EntityNPCInterface n) {
            npc = n;
            other = a;
        } else {
            return Relation.NEUTRAL;
        }
        if (npc == other || npc.faction == null || other instanceof EntityNPCInterface) return Relation.NEUTRAL;
        String aw = AwRelations.factionOf(other);
        if (aw == null) return Relation.NEUTRAL;
        return AwRelationData.get(level.getServer().overworld()).get(npc.faction.id, aw);
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (!AwCompat.loaded()) return;
        LivingEntity target = e.getNewTarget();
        if (target == null || !(e.getEntity().level() instanceof ServerLevel level)) return;
        if (relationOf(e.getEntity(), target, level) == Relation.ALLIED) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 20 != 0 || !AwCompat.loaded()) return;
        AwRelationData data = AwRelationData.get(e.getServer().overworld());
        if (!data.hasHostile()) return;
        for (ServerLevel level : e.getServer().getAllLevels()) scan(level, data);
    }

    private static void scan(ServerLevel level, AwRelationData data) {
        List<EntityNPCInterface> custom = new ArrayList<>();
        List<Mob> aw = new ArrayList<>();
        List<String> awNames = new ArrayList<>();
        for (Entity en : level.getAllEntities()) {
            if (!(en instanceof LivingEntity le) || !le.isAlive()) continue;
            if (en instanceof EntityNPCInterface n) {
                if (n.faction != null && data.hasHostileCustom(n.faction.id)) custom.add(n);
            } else if (en instanceof Mob m) {
                String f = AwRelations.factionOf(m);
                if (f != null && data.hasHostileAw(f)) {
                    aw.add(m);
                    awNames.add(f);
                }
            }
        }
        if (custom.isEmpty() || aw.isEmpty()) return;

        for (EntityNPCInterface n : custom) {
            if (n.getTarget() != null || n.isInteracting()) continue;
            double range = Math.max(1, Math.min(MAX_RANGE, n.stats.aggroRange));
            Mob best = null;
            double bestD = range * range;
            for (int i = 0; i < aw.size(); i++) {
                Mob m = aw.get(i);
                double d = n.distanceToSqr(m);
                if (d < bestD && data.get(n.faction.id, awNames.get(i)) == Relation.HOSTILE && n.hasLineOfSight(m)) {
                    bestD = d;
                    best = m;
                }
            }
            if (best != null) n.setTarget(best);
        }
        for (int i = 0; i < aw.size(); i++) {
            Mob m = aw.get(i);
            if (m.getTarget() != null) continue;
            EntityNPCInterface best = null;
            double bestD = AW_RANGE * AW_RANGE;
            for (EntityNPCInterface n : custom) {
                double d = m.distanceToSqr(n);
                if (d < bestD && data.get(n.faction.id, awNames.get(i)) == Relation.HOSTILE && m.hasLineOfSight(n)) {
                    bestD = d;
                    best = n;
                }
            }
            if (best != null) m.setTarget(best);
        }
    }
}
