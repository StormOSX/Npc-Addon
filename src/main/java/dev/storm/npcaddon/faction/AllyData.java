package dev.storm.npcaddon.faction;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

/** World-saved list of allied faction pairs. Pairs are stored as "min:max" so order does not matter. */
public class AllyData extends SavedData {
    private static final String NAME = "npcaddon_allies";
    private final Set<Long> pairs = new HashSet<>();

    public static AllyData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(AllyData::load, AllyData::new, NAME);
    }

    private static long key(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return ((long) lo << 32) | (hi & 0xFFFFFFFFL);
    }

    public boolean areAllied(int a, int b) {
        return pairs.contains(key(a, b));
    }

    public void setAllied(int a, int b, boolean allied) {
        boolean changed = allied ? pairs.add(key(a, b)) : pairs.remove(key(a, b));
        if (changed) setDirty();
    }

    /** Drops every pair involving a (deleted) faction id. */
    public void removeFaction(int id) {
        if (pairs.removeIf(k -> (int) (k >> 32) == id || k.intValue() == id)) setDirty();
    }

    public static AllyData load(CompoundTag tag) {
        AllyData data = new AllyData();
        for (Tag t : tag.getList("pairs", Tag.TAG_LONG)) {
            data.pairs.add(((net.minecraft.nbt.LongTag) t).getAsLong());
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (long k : pairs) list.add(net.minecraft.nbt.LongTag.valueOf(k));
        tag.put("pairs", list);
        return tag;
    }
}
