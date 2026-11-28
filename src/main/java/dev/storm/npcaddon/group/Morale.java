package dev.storm.npcaddon.group;

import dev.storm.npcaddon.faction.FactionRelations;
import dev.storm.npcaddon.faction.Relation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Squad morale. It starts at 100 and drops during a fight with the share of members lost, with medics lost and when
 * enemies outnumber the squad and its friends nearby. A squad whose morale breaks routs: everyone flees, and rallies
 * once it has been safe for a while (or on a rally order). Kept in memory only, so a reload steadies every squad.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Morale {
    private static final int ROUT_AT = 40;
    private static final double LOSS_WEIGHT = 130;
    private static final int PER_MEDIC = 20;
    private static final int MAX_MEDIC_PENALTY = 40;
    private static final int OUTNUMBERED_PENALTY = 65;
    private static final int PRESSED_PENALTY = 30;
    /** Fewer enemies than this never count as outnumbering. */
    private static final int MIN_ENEMIES = 4;
    private static final double NEAR = 24;
    private static final int SUSTAIN_TICKS = 60;
    private static final int FIGHT_OVER_TICKS = 20 * 30;
    private static final int STEADY_TICKS = 20 * 90;
    private static final int HURT_TICKS = 100;

    private static final class State {
        /** Strength at the start of the fight; 0 while there is none. */
        int start;
        final Set<UUID> lost = new HashSet<>();
        int medicsLost;
        long lastCombat;
        long outnumberedSince = -1;
        /** The squad cannot rout again before this time. */
        long steadyUntil;
        int value = 100;

        void calm() {
            start = 0;
            lost.clear();
            medicsLost = 0;
            outnumberedSince = -1;
            value = 100;
        }
    }

    private static final Map<String, State> STATES = new HashMap<>();
    private static MinecraftServer world;
    private static int clock;

    private Morale() {}

    /** 0 to 100, or -1 when morale is switched off for the squad. */
    public static int value(MinecraftServer server, String squad) {
        if (!GroupData.get(server.overworld()).morale(squad)) return -1;
        State s = state(server, squad);
        return s.value;
    }

    /** A rally steadies the squad. */
    public static void rallied(MinecraftServer server, String squad) {
        State s = state(server, squad);
        s.calm();
        s.steadyUntil = server.overworld().getGameTime() + STEADY_TICKS;
    }

    private static State state(MinecraftServer server, String squad) {
        if (world != server) {
            STATES.clear();
            world = server;
        }
        return STATES.computeIfAbsent(squad, k -> new State());
    }

    private static List<EntityNPCInterface> alive(MinecraftServer server, GroupData data, String squad) {
        List<EntityNPCInterface> out = new ArrayList<>();
        for (UUID id : data.members(squad)) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getEntity(id) instanceof EntityNPCInterface npc && npc.isAlive()) {
                    out.add(npc);
                    break;
                }
            }
        }
        return out;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof EntityNPCInterface dead) || !(dead.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        GroupData data = GroupData.get(server.overworld());
        for (String squad : data.squadsOf(dead.getUUID())) {
            if (!data.morale(squad)) continue;
            State s = state(server, squad);
            if (s.start == 0) {
                s.start = (int) alive(server, data, squad).stream().filter(n -> n != dead).count() + 1;
            }
            s.lastCombat = server.overworld().getGameTime();
            if (s.lost.add(dead.getUUID()) && data.isMedic(dead.getUUID())) s.medicsLost++;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 20 != 0) return;
        MinecraftServer server = e.getServer();
        GroupData data = GroupData.get(server.overworld());
        if (world == server) STATES.keySet().removeIf(name -> !data.exists(name));
        for (String squad : new ArrayList<>(data.names())) {
            if (data.morale(squad)) update(server, data, squad);
        }
    }

    private static void update(MinecraftServer server, GroupData data, String squad) {
        List<EntityNPCInterface> npcs = alive(server, data, squad);
        State s = state(server, squad);
        long now = server.overworld().getGameTime();
        boolean combat = npcs.stream().anyMatch(n -> !Retreat.isRetreating(n) && (n.getTarget() != null
            || n.getLastHurtByMob() != null && n.tickCount - n.getLastHurtByMobTimestamp() < HURT_TICKS));
        if (combat) s.lastCombat = now;
        if (s.start == 0) {
            if (!combat) {
                if (npcs.stream().noneMatch(Retreat::isRetreating)) s.value = 100;
                return;
            }
            s.start = npcs.size();
        } else if (now - s.lastCombat > FIGHT_OVER_TICKS || now < s.lastCombat) {
            s.calm();
            return;
        }
        if (npcs.isEmpty()) return;
        // Reinforced mid-fight: the squad is as large as the living and the lost together.
        s.start = Math.max(s.start, npcs.size() + s.lost.size());

        double penalty = LOSS_WEIGHT * s.lost.size() / s.start
            + Math.min(MAX_MEDIC_PENALTY, PER_MEDIC * s.medicsLost);
        double ratio = combat ? odds(npcs) : 0;
        if (ratio >= 2) {
            if (s.outnumberedSince < 0) s.outnumberedSince = now;
            penalty += now - s.outnumberedSince >= SUSTAIN_TICKS ? OUTNUMBERED_PENALTY : PRESSED_PENALTY;
        } else {
            s.outnumberedSince = -1;
            if (ratio >= 1.5) penalty += PRESSED_PENALTY;
        }
        s.value = (int) Math.max(0, Math.round(100 - penalty));
        if (s.value > ROUT_AT || now < s.steadyUntil) return;

        boolean any = false;
        for (EntityNPCInterface n : npcs) {
            if (n.isInteracting()) continue;
            Retreat.rout(n);
            any = true;
        }
        if (!any) return;
        int broke = s.value;
        s.calm();
        s.value = broke;
        s.steadyUntil = now + STEADY_TICKS;
        UUID owner = data.owner(squad);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (owner != null ? p.getUUID().equals(owner) : p.hasPermissions(2)) {
                p.sendSystemMessage(Component.literal("Squad '" + squad + "' is routing!"));
            }
        }
    }

    /** Enemies near the squad for each friend there (squad members and allied NPCs); 0 when the enemies are few. */
    private static double odds(List<EntityNPCInterface> npcs) {
        EntityNPCInterface first = npcs.get(0);
        if (!(first.level() instanceof ServerLevel level)) return 0;
        Set<LivingEntity> enemies = new HashSet<>();
        Set<LivingEntity> friends = new HashSet<>();
        for (EntityNPCInterface n : npcs) {
            if (n.level() != level) continue;
            friends.add(n);
            AABB box = n.getBoundingBox().inflate(NEAR, 8, NEAR);
            for (LivingEntity other : level.getEntitiesOfClass(LivingEntity.class, box,
                    o -> o != n && o.isAlive())) {
                if (MobDefense.isThreat(n, other, level)) {
                    // Monsters in caves or behind walls do not frighten anyone.
                    if (!enemies.contains(other) && n.hasLineOfSight(other)) enemies.add(other);
                } else if (other instanceof EntityNPCInterface o && (o.faction.id == n.faction.id
                        || FactionRelations.get(level.getServer(), n.faction.id, o.faction.id) == Relation.ALLIED)) {
                    friends.add(o);
                }
            }
        }
        return enemies.size() < MIN_ENEMIES ? 0 : enemies.size() / (double) friends.size();
    }
}
