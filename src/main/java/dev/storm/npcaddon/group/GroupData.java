package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** World-saved NPC groups: group name -> NPC entity UUIDs. */
public class GroupData extends SavedData {
    private static final String NAME = "npcaddon_groups";
    private final Map<String, Set<UUID>> groups = new LinkedHashMap<>();
    private final Map<String, Stance> stances = new LinkedHashMap<>();
    private final Map<String, Shape> shapes = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> medics = new LinkedHashMap<>();
    /** Squads' retreat thresholds in percent of health; 0 = never retreat. */
    private final Map<String, Integer> retreats = new LinkedHashMap<>();
    public static final int DEFAULT_RETREAT = 10;
    public static final int MAX_RETREAT = 90;
    private static final Shape DEFAULT_SHAPE = new Shape(Formation.SQUARE, 2.0);

    /** The formation a squad last formed up in; its move, defend and area orders use it too. */
    public record Shape(Formation formation, double spacing) {}

    public static GroupData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(GroupData::load, GroupData::new, NAME);
    }

    public Set<String> names() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    public Set<UUID> members(String group) {
        Set<UUID> s = groups.get(group);
        return s == null ? Set.of() : Collections.unmodifiableSet(s);
    }

    public boolean exists(String group) {
        return groups.containsKey(group);
    }

    /** @return number of NPCs newly added. */
    public int addAll(String group, Iterable<UUID> ids) {
        Set<UUID> s = groups.computeIfAbsent(group, k -> new LinkedHashSet<>());
        int added = 0;
        for (UUID id : ids) if (s.add(id)) added++;
        setDirty();
        return added;
    }

    public Stance stance(String group) {
        return stances.getOrDefault(group, Stance.DEFENSIVE);
    }

    public void setStance(String group, Stance stance) {
        if (!groups.containsKey(group)) return;
        if (stance == Stance.DEFENSIVE) stances.remove(group);
        else stances.put(group, stance);
        setDirty();
    }

    /** Whether the NPC is a medic in any squad. */
    public boolean isMedic(UUID id) {
        for (Set<UUID> s : medics.values()) if (s.contains(id)) return true;
        return false;
    }

    /** Makes a member a medic of this squad, or an ordinary member again (in every squad). */
    public void setMedic(String group, UUID id, boolean medic) {
        if (!members(group).contains(id)) return;
        if (medic) medics.computeIfAbsent(group, k -> new LinkedHashSet<>()).add(id);
        else medics.values().forEach(s -> s.remove(id));
        setDirty();
    }

    public int retreat(String group) {
        return retreats.getOrDefault(group, DEFAULT_RETREAT);
    }

    public void setRetreat(String group, int percent) {
        if (!groups.containsKey(group)) return;
        retreats.put(group, Math.max(0, Math.min(MAX_RETREAT, percent)));
        setDirty();
    }

    /** Retreat threshold of the first squad this NPC belongs to; 0 when it is in none. */
    public int retreatOfMember(UUID id) {
        for (Map.Entry<String, Set<UUID>> e : groups.entrySet()) {
            if (e.getValue().contains(id)) return retreat(e.getKey());
        }
        return 0;
    }

    public Shape shape(String group) {
        return shapes.getOrDefault(group, DEFAULT_SHAPE);
    }

    public void setShape(String group, Formation formation, double spacing) {
        if (!groups.containsKey(group)) return;
        shapes.put(group, new Shape(formation, spacing));
        setDirty();
    }

    /** Stance of the first squad this NPC belongs to; defensive (= untouched) when it is in none. */
    public Stance stanceOfMember(UUID id) {
        for (Map.Entry<String, Set<UUID>> e : groups.entrySet()) {
            if (e.getValue().contains(id)) return stance(e.getKey());
        }
        return Stance.DEFENSIVE;
    }

    /** Names of the squads that contain this NPC. */
    public java.util.List<String> squadsOf(UUID id) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<String, Set<UUID>> e : groups.entrySet()) if (e.getValue().contains(id)) out.add(e.getKey());
        return out;
    }

    public boolean removeGroup(String group) {
        stances.remove(group);
        shapes.remove(group);
        retreats.remove(group);
        medics.remove(group);
        boolean had = groups.remove(group) != null;
        if (had) setDirty();
        return had;
    }

    public boolean removeMember(String group, UUID id) {
        Set<UUID> s = groups.get(group);
        boolean had = s != null && s.remove(id);
        Set<UUID> m = medics.get(group);
        if (m != null) m.remove(id);
        if (had) setDirty();
        return had;
    }

    public static GroupData load(CompoundTag tag) {
        GroupData data = new GroupData();
        CompoundTag all = tag.getCompound("groups");
        for (String name : all.getAllKeys()) {
            Set<UUID> set = new LinkedHashSet<>();
            for (Tag t : all.getList(name, Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(t));
            data.groups.put(name, set);
        }
        CompoundTag st = tag.getCompound("stances");
        for (String name : st.getAllKeys()) {
            int i = st.getInt(name);
            if (data.groups.containsKey(name) && i > 0 && i < Stance.values().length) data.stances.put(name, Stance.values()[i]);
        }
        CompoundTag me = tag.getCompound("medics");
        for (String name : me.getAllKeys()) {
            if (!data.groups.containsKey(name)) continue;
            Set<UUID> set = new LinkedHashSet<>();
            for (Tag t : me.getList(name, Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(t));
            data.medics.put(name, set);
        }
        CompoundTag re = tag.getCompound("retreats");
        for (String name : re.getAllKeys()) {
            int percent = re.getInt(name);
            if (data.groups.containsKey(name) && percent >= 0 && percent <= MAX_RETREAT) data.retreats.put(name, percent);
        }
        CompoundTag sh = tag.getCompound("shapes");
        for (String name : sh.getAllKeys()) {
            CompoundTag one = sh.getCompound(name);
            int i = one.getInt("formation");
            double spacing = one.getDouble("spacing");
            if (data.groups.containsKey(name) && i >= 0 && i < Formation.values().length && spacing >= 1 && spacing <= 8) {
                data.shapes.put(name, new Shape(Formation.values()[i], spacing));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        groups.forEach((name, set) -> {
            ListTag list = new ListTag();
            for (UUID id : set) list.add(NbtUtils.createUUID(id));
            all.put(name, list);
        });
        tag.put("groups", all);
        CompoundTag st = new CompoundTag();
        stances.forEach((name, s) -> st.putInt(name, s.ordinal()));
        tag.put("stances", st);
        CompoundTag sh = new CompoundTag();
        shapes.forEach((name, shape) -> {
            CompoundTag one = new CompoundTag();
            one.putInt("formation", shape.formation().ordinal());
            one.putDouble("spacing", shape.spacing());
            sh.put(name, one);
        });
        tag.put("shapes", sh);
        CompoundTag re = new CompoundTag();
        retreats.forEach(re::putInt);
        tag.put("retreats", re);
        CompoundTag me = new CompoundTag();
        medics.forEach((name, set) -> {
            ListTag list = new ListTag();
            for (UUID id : set) list.add(NbtUtils.createUUID(id));
            me.put(name, list);
        });
        tag.put("medics", me);
        return tag;
    }
}
