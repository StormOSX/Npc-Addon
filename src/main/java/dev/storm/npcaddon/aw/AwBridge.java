package dev.storm.npcaddon.aw;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.shadowmage.ancientwarfare.core.common.npc.FactionRegistry;
import net.shadowmage.ancientwarfare.core.common.npc.FactionStandingData;
import net.shadowmage.ancientwarfare.core.common.npc.NpcFactionAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.UUID;

/** The only place that touches Ancient Warfare classes. Call only when {@link AwCompat#loaded()}. */
final class AwBridge {
    private AwBridge() {}

    static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** Every AW faction name (they come from AW's datapack, so a datapack can add more), sorted. */
    static List<String> names() {
        return new ArrayList<>(new TreeSet<>(FactionRegistry.factionNames()));
    }

    static boolean exists(String name) {
        return FactionRegistry.factionNames().contains(normalize(name));
    }

    /** Faction name of an AW faction NPC, or null for anything else. */
    static String factionOf(Entity e) {
        if (!(e instanceof NpcFactionAccess a)) return null;
        String n = a.getFactionName();
        return n == null ? null : normalize(n);
    }

    /** AW's default player standing for a faction (negative = hostile). */
    static int defaultStanding(String name) {
        return FactionRegistry.get(normalize(name)).defaultStanding();
    }

    static int standing(MinecraftServer server, UUID player, String name) {
        return FactionStandingData.get(server.overworld()).getStanding(player, normalize(name));
    }

    static void setStanding(MinecraftServer server, UUID player, String name, int value) {
        FactionStandingData.get(server.overworld()).setStanding(player, normalize(name), value);
    }
}
