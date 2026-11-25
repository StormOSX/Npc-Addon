package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** The NPCs players can hire: saved copies of NPCs, each with a recruit type. Stored with the world. */
public class RecruitCatalog extends SavedData {
    private static final String NAME = "npcaddon_recruits";
    public static final int MAX_ENTRIES = 64;

    public record Entry(RecruitType type, CompoundTag npc) {}

    private final Map<String, Entry> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public static RecruitCatalog get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RecruitCatalog::load, RecruitCatalog::new, NAME);
    }

    public Map<String, Entry> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * Saves a copy of the NPC as it is now under this name, replacing an earlier one.
     *
     * @return false if the catalog is full or the NPC cannot be saved
     */
    public boolean save(String name, RecruitType type, EntityNPCInterface npc) {
        if (!entries.containsKey(name) && entries.size() >= MAX_ENTRIES) return false;
        CompoundTag tag = new CompoundTag();
        if (!npc.saveAsPassenger(tag)) return false;
        // Strips identity and position, the same way CustomNPCs' cloner does.
        ServerCloneController.Instance.cleanTags(tag);
        tag.remove("Passengers");
        // Squad state (stance, crew, retreat...) belongs to the original, not to the copies.
        CompoundTag extra = tag.getCompound("ForgeData");
        for (String key : new ArrayList<>(extra.getAllKeys())) if (key.startsWith("npcaddon_")) extra.remove(key);
        entries.put(name, new Entry(type, tag));
        setDirty();
        return true;
    }

    public boolean remove(String name) {
        boolean had = entries.remove(name) != null;
        if (had) setDirty();
        return had;
    }

    public static RecruitCatalog load(CompoundTag tag) {
        RecruitCatalog data = new RecruitCatalog();
        CompoundTag all = tag.getCompound("recruits");
        for (String name : all.getAllKeys()) {
            CompoundTag one = all.getCompound(name);
            RecruitType type = RecruitType.parse(one.getString("type"));
            if (type != null) data.entries.put(name, new Entry(type, one.getCompound("npc")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        entries.forEach((name, e) -> {
            CompoundTag one = new CompoundTag();
            one.putString("type", e.type().key());
            one.put("npc", e.npc());
            all.put(name, one);
        });
        tag.put("recruits", all);
        return tag;
    }
}
