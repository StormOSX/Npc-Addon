package dev.storm.npcaddon.faction;

import net.minecraft.server.MinecraftServer;
import noppes.npcs.api.NpcAPI;
import noppes.npcs.api.handler.data.IFaction;

public final class FactionRelations {
    private FactionRelations() {}

    public static Relation get(MinecraftServer server, int a, int b) {
        if (AllyData.get(server.overworld()).areAllied(a, b)) return Relation.ALLIED;
        IFaction fa = NpcAPI.Instance().getFactions().get(a);
        IFaction fb = NpcAPI.Instance().getFactions().get(b);
        if (fa != null && fb != null && (fa.hasHostile(b) || fb.hasHostile(a))) return Relation.HOSTILE;
        return Relation.NEUTRAL;
    }

    /** @return false if either faction does not exist or a == b. */
    public static boolean set(MinecraftServer server, int a, int b, Relation relation) {
        if (a == b) return false;
        IFaction fa = NpcAPI.Instance().getFactions().get(a);
        IFaction fb = NpcAPI.Instance().getFactions().get(b);
        if (fa == null || fb == null) return false;

        AllyData allies = AllyData.get(server.overworld());
        if (relation == Relation.HOSTILE) {
            fa.addHostile(b);
            fb.addHostile(a);
        } else {
            fa.removeHostile(b);
            fb.removeHostile(a);
        }
        allies.setAllied(a, b, relation == Relation.ALLIED);
        fa.save();
        fb.save();
        return true;
    }
}
