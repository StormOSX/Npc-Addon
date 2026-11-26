package dev.storm.npcaddon.aw;

import dev.storm.npcaddon.faction.PlayerStandings;
import dev.storm.npcaddon.faction.Relation;
import dev.storm.npcaddon.faction.Standing;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import noppes.npcs.api.NpcAPI;

import java.util.List;
import java.util.Locale;

/** Relations and player standings with Ancient Warfare factions. Safe to call without AW (everything just fails). */
public final class AwRelations {
    /** AW's own rule is "hostile below 0"; these put a player well inside each band. */
    private static final int HOSTILE_STANDING = -50;
    private static final int NEUTRAL_STANDING = 0;
    private static final int FRIENDLY_STANDING = 50;

    private AwRelations() {}

    public static boolean available() {
        return AwCompat.loaded();
    }

    public static List<String> names() {
        return AwCompat.loaded() ? AwBridge.names() : List.of();
    }

    public static boolean exists(String aw) {
        return AwCompat.loaded() && AwBridge.exists(aw);
    }

    public static int defaultStanding(String aw) {
        return AwCompat.loaded() ? AwBridge.defaultStanding(aw) : 0;
    }

    public static Relation get(MinecraftServer server, int customFaction, String aw) {
        if (!AwCompat.loaded()) return Relation.NEUTRAL;
        return AwRelationData.get(server.overworld()).get(customFaction, AwBridge.normalize(aw));
    }

    /** @return false if AW is missing or either faction does not exist. Mutual by construction. */
    public static boolean set(MinecraftServer server, int customFaction, String aw, Relation relation) {
        if (!AwCompat.loaded() || !AwBridge.exists(aw)) return false;
        if (NpcAPI.Instance().getFactions().get(customFaction) == null) return false;
        AwRelationData.get(server.overworld()).set(customFaction, AwBridge.normalize(aw), relation);
        return true;
    }

    /** Sets a player's standing with an AW faction (AW stores one number per player and faction). */
    public static PlayerStandings.Result setPlayer(ServerPlayer player, String aw, Standing s) {
        if (!AwCompat.loaded()) return new PlayerStandings.Result(false, "Ancient Warfare is not installed");
        if (!AwBridge.exists(aw)) return new PlayerStandings.Result(false, "Unknown Ancient Warfare faction '" + aw + "'");
        int value = switch (s) {
            case HOSTILE -> HOSTILE_STANDING;
            case NEUTRAL -> NEUTRAL_STANDING;
            case FRIENDLY -> FRIENDLY_STANDING;
        };
        AwBridge.setStanding(player.server, player.getUUID(), aw, value);
        return new PlayerStandings.Result(true, player.getGameProfile().getName() + " is now "
            + s.name().toLowerCase(Locale.ROOT) + " to Ancient Warfare faction '" + AwBridge.normalize(aw)
            + "' (standing " + value + ")");
    }

    /** Puts back a stored AW standing value. */
    public static void restoreStanding(MinecraftServer server, java.util.UUID player, String aw, int value) {
        if (AwCompat.loaded() && AwBridge.exists(aw)) AwBridge.setStanding(server, player, aw, value);
    }

    public static int standing(MinecraftServer server, java.util.UUID player, String aw) {
        return AwCompat.loaded() ? AwBridge.standing(server, player, aw) : 0;
    }

    /** Faction name of an AW faction NPC, null for anything else (and when AW is missing). */
    public static String factionOf(net.minecraft.world.entity.Entity e) {
        return AwCompat.loaded() ? AwBridge.factionOf(e) : null;
    }
}
