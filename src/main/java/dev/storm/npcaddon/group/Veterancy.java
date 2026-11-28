package dev.storm.npcaddon.group;

import dev.storm.npcaddon.aw.AwRelations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Squad NPCs earn ranks by killing enemies: each rank adds health and damage. The kill count lives in the NPC's
 * persistent data; an NPC that dies loses it.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Veterancy {
    private static final String KILLS = "npcaddon_kills";
    private static final int[] THRESHOLDS = {5, 15, 30};
    private static final String[] NAMES = {"Veteran", "Elite", "Champion"};
    /** Extra health and damage per rank, as a fraction. */
    private static final float BONUS = 0.1f;
    private static final UUID HEALTH_ID = UUID.fromString("6f0e5b52-6f0a-4f0e-9a0c-6d2a1f6b4c11");

    private Veterancy() {}

    public static int kills(EntityNPCInterface npc) {
        return npc.getPersistentData().getInt(KILLS);
    }

    /** 0 = no rank, 1 to 3 = Veteran, Elite, Champion. */
    public static int rank(EntityNPCInterface npc) {
        int kills = kills(npc);
        int rank = 0;
        while (rank < THRESHOLDS.length && kills >= THRESHOLDS[rank]) rank++;
        return rank;
    }

    /** Forgets kills and rank: for the dead and for fresh recruit copies. */
    static void reset(EntityNPCInterface npc) {
        CompoundTag pd = npc.getPersistentData();
        pd.remove(KILLS);
        apply(npc);
    }

    /** Brings the health bonus in line with the rank. */
    private static void apply(EntityNPCInterface npc) {
        AttributeInstance health = npc.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) return;
        double want = rank(npc) * BONUS;
        AttributeModifier old = health.getModifier(HEALTH_ID);
        if (old != null && Math.abs(old.getAmount() - want) < 1e-6) return;
        if (old != null) health.removeModifier(HEALTH_ID);
        if (want > 0) {
            health.addPermanentModifier(new AttributeModifier(HEALTH_ID, "npcaddon veteran", want,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        if (npc.getHealth() > npc.getMaxHealth()) npc.setHealth(npc.getMaxHealth());
    }

    private static boolean counts(LivingEntity victim) {
        return victim instanceof Enemy || victim instanceof EntityNPCInterface || victim instanceof Player
            || AwRelations.factionOf(victim) != null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        if (e.getSource().getEntity() instanceof EntityNPCInterface killer && killer != victim && killer.isAlive()
                && counts(victim)) {
            credit(level.getServer(), level, killer);
        }
        if (victim instanceof EntityNPCInterface dead && dead.getPersistentData().contains(KILLS)) reset(dead);
    }

    private static void credit(MinecraftServer server, ServerLevel level, EntityNPCInterface npc) {
        GroupData data = GroupData.get(server.overworld());
        var squads = data.squadsOf(npc.getUUID());
        if (squads.isEmpty()) return;
        int before = rank(npc);
        npc.getPersistentData().putInt(KILLS, kills(npc) + 1);
        int now = rank(npc);
        if (now == before) return;
        float max = npc.getMaxHealth();
        apply(npc);
        npc.heal(Math.max(0, npc.getMaxHealth() - max));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + npc.getBbHeight(), npc.getZ(),
            20, 0.4, 0.4, 0.4, 0);
        Set<UUID> owners = new LinkedHashSet<>();
        for (String squad : squads) if (data.owner(squad) != null) owners.add(data.owner(squad));
        String text = npc.getName().getString() + " of squad '" + squads.get(0) + "' is now " + NAMES[now - 1];
        for (UUID owner : owners) {
            ServerPlayer p = server.getPlayerList().getPlayer(owner);
            if (p != null) p.sendSystemMessage(Component.literal(text));
        }
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (e.getSource().getEntity() instanceof EntityNPCInterface npc && !npc.level().isClientSide) {
            int rank = rank(npc);
            if (rank > 0) e.setAmount(e.getAmount() * (1 + rank * BONUS));
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getEntity() instanceof EntityNPCInterface npc && !e.getLevel().isClientSide
                && npc.getPersistentData().contains(KILLS)) {
            apply(npc);
        }
    }
}
