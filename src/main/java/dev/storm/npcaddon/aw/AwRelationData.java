package dev.storm.npcaddon.aw;

import dev.storm.npcaddon.faction.Relation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * World-saved relations between a CustomNPCs faction (by id) and an Ancient Warfare faction (by name). Only hostile and
 * allied pairs are stored; anything else is neutral. A pair is one entry, so every relation is mutual by construction.
 * This class has no AW types, so it is safe to load without AW installed.
 */
public class AwRelationData extends SavedData {
    private static final String NAME = "npcaddon_aw_relations";
    private final Map<String, Relation> pairs = new HashMap<>();

    public static AwRelationData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(AwRelationData::load, AwRelationData::new, NAME);
    }

    private static String key(int custom, String aw) {
        return custom + "|" + aw;
    }

    public Relation get(int custom, String aw) {
        return pairs.getOrDefault(key(custom, aw), Relation.NEUTRAL);
    }

    public void set(int custom, String aw, Relation r) {
        boolean changed;
        if (r == Relation.NEUTRAL) changed = pairs.remove(key(custom, aw)) != null;
        else changed = pairs.put(key(custom, aw), r) != r;
        if (changed) setDirty();
    }

    public boolean hasHostile() {
        return pairs.containsValue(Relation.HOSTILE);
    }

    public boolean hasHostileCustom(int custom) {
        String prefix = custom + "|";
        for (Map.Entry<String, Relation> e : pairs.entrySet()) {
            if (e.getValue() == Relation.HOSTILE && e.getKey().startsWith(prefix)) return true;
        }
        return false;
    }

    public boolean hasHostileAw(String aw) {
        String suffix = "|" + aw;
        for (Map.Entry<String, Relation> e : pairs.entrySet()) {
            if (e.getValue() == Relation.HOSTILE && e.getKey().endsWith(suffix)) return true;
        }
        return false;
    }

    /** Drops every pair that involves a (deleted) CustomNPCs faction id. */
    public void removeFaction(int custom) {
        Set<String> gone = new HashSet<>();
        for (String k : pairs.keySet()) if (k.startsWith(custom + "|")) gone.add(k);
        if (!gone.isEmpty()) {
            pairs.keySet().removeAll(gone);
            setDirty();
        }
    }

    public static AwRelationData load(CompoundTag tag) {
        AwRelationData d = new AwRelationData();
        CompoundTag all = tag.getCompound("pairs");
        for (String k : all.getAllKeys()) {
            int i = all.getInt(k);
            if (i >= 0 && i < Relation.values().length && Relation.values()[i] != Relation.NEUTRAL) {
                d.pairs.put(k, Relation.values()[i]);
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        pairs.forEach((k, r) -> all.putInt(k, r.ordinal()));
        tag.put("pairs", all);
        return tag;
    }
}
