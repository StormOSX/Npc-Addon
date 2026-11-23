package dev.storm.npcaddon.faction;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.entity.IPlayer;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;

import java.util.Locale;

/**
 * Player-vs-faction relations. CustomNPCs has no such setting directly: a player has faction points, and the
 * faction's thresholds turn them into hostile (below neutralPoints), neutral (below friendlyPoints) or friendly.
 * "Setting a standing" therefore means moving the points into the right band, well inside it so one small
 * change from kills or quests does not flip it.
 */
public final class PlayerStandings {
    public record Result(boolean ok, String message) {}

    private PlayerStandings() {}

    /** Points that put a player firmly inside the band, or null if the faction's thresholds leave no such band. */
    private static Integer target(Faction f, Standing s) {
        int n = f.neutralPoints;
        int fr = f.friendlyPoints;
        return switch (s) {
            case HOSTILE -> n - Math.max(1, n / 2);
            case NEUTRAL -> fr > n ? (n + fr) / 2 : null;
            case FRIENDLY -> fr + Math.max(1, (fr - n) / 4);
        };
    }

    private static String label(Standing s) {
        return s.name().toLowerCase(Locale.ROOT);
    }

    public static Result setPlayer(ServerPlayer player, int factionId, Standing s) {
        Faction f = FactionController.instance.getFaction(factionId);
        if (f == null) return new Result(false, "Unknown faction id " + factionId);
        Integer t = target(f, s);
        if (t == null) {
            return new Result(false, "Faction '" + f.name + "' has no neutral range (neutral points >= friendly points)");
        }
        IPlayer ip = (IPlayer) NpcAPI.Instance().getIEntity(player);
        int current = ip.getFactionPoints(factionId);
        ip.addFactionPoints(factionId, t - current);
        String note = s == Standing.HOSTILE && player.getAbilities().instabuild
            ? " (creative players are never attacked)" : "";
        return new Result(true, player.getGameProfile().getName() + " is now " + label(s) + " to '" + f.name
            + "' (" + t + " points)" + note);
    }

    /**
     * Sets the starting standing of every player who has no stored points for the faction yet.
     * With applyOnline the players currently online are moved as well; others keep what they have.
     */
    public static Result setDefault(MinecraftServer server, int factionId, Standing s, boolean applyOnline) {
        Faction f = FactionController.instance.getFaction(factionId);
        if (f == null) return new Result(false, "Unknown faction id " + factionId);
        Integer t = target(f, s);
        if (t == null) {
            return new Result(false, "Faction '" + f.name + "' has no neutral range (neutral points >= friendly points)");
        }
        f.setDefaultPoints(t);
        f.save();
        int moved = 0;
        if (applyOnline) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (setPlayer(p, factionId, s).ok()) moved++;
            }
        }
        return new Result(true, "New players start " + label(s) + " to '" + f.name + "'"
            + (applyOnline ? "; " + moved + " online player(s) updated" : "; existing players keep their points"));
    }
}
