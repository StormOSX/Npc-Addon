package dev.storm.npcaddon.group;

import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.faction.Relation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import noppes.npcs.entity.EntityNPCInterface;

/** What a squad NPC counts as an enemy on its own initiative. */
final class Hostility {
    private Hostility() {}

    /** Hostile monsters, NPCs of a hostile faction and Ancient Warfare NPCs of a hostile faction. Not players. */
    static boolean isFoe(EntityNPCInterface npc, LivingEntity t, ServerLevel level) {
        if (t == npc) return false;
        if (t instanceof Enemy) return true;
        if (t instanceof EntityNPCInterface o) return npc.faction.isAggressiveToNpc(o);
        String aw = AwRelations.factionOf(t);
        return aw != null && AwRelations.get(level.getServer(), npc.faction.id, aw) == Relation.HOSTILE;
    }
}
