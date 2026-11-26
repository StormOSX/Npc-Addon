package dev.storm.npcaddon.group;

import dev.storm.npcaddon.faction.Membership;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.UUID;

/**
 * Squad NPCs of a player's faction, or of an allied one, come to the player's aid when something attacks them nearby.
 * Only aggressive and defensive squads do, and nobody with other work: medics, crews, retreating or ordered NPCs.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class MemberDefense {
    private static final double RANGE = 24;

    private MemberDefense() {}

    @SubscribeEvent
    public static void hurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.isCreative()
                || !(e.getSource().getEntity() instanceof LivingEntity attacker) || attacker == p) return;
        MinecraftServer server = p.server;
        if (Membership.factionOf(server, p.getUUID()) == Membership.NONE) return;
        // Never against the player's own side, and no player fights where those are switched off.
        if (attacker instanceof EntityNPCInterface o && Membership.sides(server, p.getUUID(), o.faction.id)) return;
        if (attacker instanceof Player other && (!server.isPvpAllowed()
                || Membership.factionOf(server, other.getUUID()) == Membership.factionOf(server, p.getUUID()))) return;
        GroupData data = GroupData.get(server.overworld());
        for (String squad : data.names()) {
            Stance stance = data.stance(squad);
            if (stance != Stance.AGGRESSIVE && stance != Stance.DEFENSIVE) continue;
            for (UUID id : data.members(squad)) {
                if (p.serverLevel().getEntity(id) instanceof EntityNPCInterface npc && npc != attacker && npc.isAlive()
                        && npc.distanceToSqr(p) <= RANGE * RANGE
                        && Membership.sides(server, p.getUUID(), npc.faction.id) && isFree(npc)) {
                    npc.setTarget(attacker);
                }
            }
        }
    }

    private static boolean isFree(EntityNPCInterface npc) {
        LivingEntity current = npc.getTarget();
        // Whoever is already fighting something at least as near keeps at it.
        if (current != null && current.isAlive()) return false;
        return !Medic.isMedic(npc) && !SiegeCrew.isCrew(npc) && !Retreat.isRetreating(npc)
            && !GroupMover.isBusyMoving(npc) && !Mounts.isMounting(npc);
    }
}
