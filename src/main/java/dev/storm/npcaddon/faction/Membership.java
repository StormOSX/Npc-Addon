package dev.storm.npcaddon.faction;

import dev.storm.npcaddon.aw.AwRelations;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;

import java.util.ArrayList;
import java.util.UUID;

/**
 * A player belongs to at most one CustomNPCs faction. While they do, their own faction and its allies are friendly to
 * them and its enemies hostile, Ancient Warfare factions included; other factions are left alone. Leaving restores the
 * standings they had before.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Membership {
    public static final int NONE = -1;
    private static int clock;

    private Membership() {}

    public static int factionOf(MinecraftServer server, UUID player) {
        MembershipData.Member m = MembershipData.get(server).members.get(player);
        return m == null ? NONE : m.faction;
    }

    /** Whether an NPC of this faction is on the player's side: their own faction or an ally of it. */
    public static boolean sides(MinecraftServer server, UUID player, int faction) {
        int own = factionOf(server, player);
        return own != NONE && (own == faction || FactionRelations.get(server, own, faction) == Relation.ALLIED);
    }

    /** Points needed to join, or -1 if the faction is closed. */
    public static int requirement(MinecraftServer server, int faction) {
        return MembershipData.get(server).open.getOrDefault(faction, -1);
    }

    /** Opens a faction to joining with the points needed (negative: closes it). Members stay either way. */
    public static boolean setOpen(MinecraftServer server, int faction, int points) {
        if (FactionController.instance.getFaction(faction) == null) return false;
        MembershipData data = MembershipData.get(server);
        if (points < 0) data.open.remove(faction);
        else data.open.put(faction, points);
        data.setDirty();
        return true;
    }

    public static int points(ServerPlayer p, int faction) {
        return ((IPlayer) NpcAPI.Instance().getIEntity(p)).getFactionPoints(faction);
    }

    private static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    /** Joins, leaving any earlier faction. Operators may join any faction. */
    public static boolean join(ServerPlayer p, int faction) {
        Faction f = FactionController.instance.getFaction(faction);
        if (f == null) {
            tell(p, "Unknown faction");
            return false;
        }
        if (factionOf(p.server, p.getUUID()) == faction) {
            tell(p, "You are already in " + f.name);
            return false;
        }
        if (!p.hasPermissions(2)) {
            int need = requirement(p.server, faction);
            if (need < 0) {
                tell(p, f.name + " is not open to join");
                return false;
            }
            if (points(p, faction) < need) {
                tell(p, f.name + " asks for " + need + " faction points; you have " + points(p, faction));
                return false;
            }
        }
        leave(p, false);
        MembershipData data = MembershipData.get(p.server);
        data.members.put(p.getUUID(), new MembershipData.Member(faction));
        data.setDirty();
        apply(p);
        tell(p, "You joined " + f.name);
        return true;
    }

    public static boolean leave(ServerPlayer p, boolean announce) {
        MembershipData data = MembershipData.get(p.server);
        MembershipData.Member m = data.members.remove(p.getUUID());
        if (m == null) {
            if (announce) tell(p, "You are not in a faction");
            return false;
        }
        data.setDirty();
        m.points.forEach((faction, value) -> setPoints(p, faction, value));
        m.aw.forEach((name, value) -> AwRelations.restoreStanding(p.server, p.getUUID(), name, value));
        if (announce) tell(p, "You left your faction");
        return true;
    }

    private static void setPoints(ServerPlayer p, int faction, int value) {
        if (FactionController.instance.getFaction(faction) == null) return;
        IPlayer player = (IPlayer) NpcAPI.Instance().getIEntity(p);
        int now = player.getFactionPoints(faction);
        if (now != value) player.addFactionPoints(faction, value - now);
    }

    /** Brings the member's standings in line with their faction's current relations. */
    private static void apply(ServerPlayer p) {
        MembershipData data = MembershipData.get(p.server);
        MembershipData.Member m = data.members.get(p.getUUID());
        if (m == null) return;
        if (FactionController.instance.getFaction(m.faction) == null) {
            leave(p, true); // the faction was deleted
            return;
        }
        for (Faction f : new ArrayList<>(FactionController.instance.factions.values())) {
            Relation r = f.id == m.faction ? Relation.ALLIED : FactionRelations.get(p.server, m.faction, f.id);
            if (r == Relation.NEUTRAL) {
                Integer before = m.points.remove(f.id);
                if (before != null) {
                    setPoints(p, f.id, before);
                    data.setDirty();
                }
                continue;
            }
            int now = points(p, f.id);
            boolean right = r == Relation.ALLIED ? now >= f.friendlyPoints : now < f.neutralPoints;
            if (!m.points.containsKey(f.id)) {
                m.points.put(f.id, now);
                data.setDirty();
            }
            if (!right) PlayerStandings.setPlayer(p, f.id, r == Relation.ALLIED ? Standing.FRIENDLY : Standing.HOSTILE);
        }
        for (String name : AwRelations.names()) {
            Relation r = AwRelations.get(p.server, m.faction, name);
            if (r == Relation.NEUTRAL) {
                Integer before = m.aw.remove(name);
                if (before != null) {
                    AwRelations.restoreStanding(p.server, p.getUUID(), name, before);
                    data.setDirty();
                }
                continue;
            }
            int now = AwRelations.standing(p.server, p.getUUID(), name);
            if (!m.aw.containsKey(name)) {
                m.aw.put(name, now);
                data.setDirty();
            }
            boolean right = r == Relation.ALLIED ? now > 0 : now < 0;
            if (!right) AwRelations.setPlayer(p, name, r == Relation.ALLIED ? Standing.FRIENDLY : Standing.HOSTILE);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 100 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) apply(p);
    }
}
