package dev.storm.npcaddon.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Which factions players may join, and who is in which. */
public class MembershipData extends SavedData {
    private static final String NAME = "npcaddon_members";

    /** A player's faction, and the standings they had before membership changed them. */
    public static final class Member {
        public final int faction;
        public final Map<Integer, Integer> points = new HashMap<>();
        public final Map<String, Integer> aw = new HashMap<>();

        Member(int faction) {
            this.faction = faction;
        }
    }

    /** Joinable faction -> faction points needed (0 = none). */
    final Map<Integer, Integer> open = new LinkedHashMap<>();
    final Map<UUID, Member> members = new HashMap<>();

    public static MembershipData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(MembershipData::load, MembershipData::new, NAME);
    }

    public static MembershipData load(CompoundTag tag) {
        MembershipData data = new MembershipData();
        CompoundTag open = tag.getCompound("open");
        for (String id : open.getAllKeys()) data.open.put(Integer.parseInt(id), open.getInt(id));
        CompoundTag all = tag.getCompound("members");
        for (String id : all.getAllKeys()) {
            CompoundTag one = all.getCompound(id);
            Member m = new Member(one.getInt("faction"));
            CompoundTag points = one.getCompound("points");
            for (String f : points.getAllKeys()) m.points.put(Integer.parseInt(f), points.getInt(f));
            CompoundTag aw = one.getCompound("aw");
            for (String f : aw.getAllKeys()) m.aw.put(f, aw.getInt(f));
            data.members.put(UUID.fromString(id), m);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag o = new CompoundTag();
        open.forEach((id, min) -> o.putInt(String.valueOf(id), min));
        tag.put("open", o);
        CompoundTag all = new CompoundTag();
        members.forEach((id, m) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("faction", m.faction);
            CompoundTag points = new CompoundTag();
            m.points.forEach((f, v) -> points.putInt(String.valueOf(f), v));
            one.put("points", points);
            CompoundTag aw = new CompoundTag();
            m.aw.forEach(aw::putInt);
            one.put("aw", aw);
            all.put(id.toString(), one);
        });
        tag.put("members", all);
        return tag;
    }
}
