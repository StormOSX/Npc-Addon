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

    public boolean removeGroup(String group) {
        boolean had = groups.remove(group) != null;
        if (had) setDirty();
        return had;
    }

    public boolean removeMember(String group, UUID id) {
        Set<UUID> s = groups.get(group);
        boolean had = s != null && s.remove(id);
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
        return tag;
    }
}
