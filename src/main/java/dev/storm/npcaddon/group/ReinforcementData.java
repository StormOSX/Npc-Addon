package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The reinforcements each player has in the field. */
public class ReinforcementData extends SavedData {
    private static final String NAME = "npcaddon_reinforcements";

    static final class Force {
        final String squad;
        /** Everyone who came, so they can be sent home even if the squad is deleted. */
        final List<UUID> members = new ArrayList<>();
        final List<UUID> weapons = new ArrayList<>();
        boolean marching = true;
        boolean fought;
        /** Seconds since they arrived, and seconds without any fighting. */
        int age;
        int quiet;

        Force(String squad) {
            this.squad = squad;
        }
    }

    final Map<UUID, Force> forces = new HashMap<>();

    static ReinforcementData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
            .computeIfAbsent(ReinforcementData::load, ReinforcementData::new, NAME);
    }

    boolean ownsWeapon(UUID id) {
        for (Force f : forces.values()) if (f.weapons.contains(id)) return true;
        return false;
    }

    private static ReinforcementData load(CompoundTag tag) {
        ReinforcementData data = new ReinforcementData();
        CompoundTag all = tag.getCompound("forces");
        for (String id : all.getAllKeys()) {
            CompoundTag one = all.getCompound(id);
            Force f = new Force(one.getString("squad"));
            for (Tag t : one.getList("members", Tag.TAG_INT_ARRAY)) f.members.add(NbtUtils.loadUUID(t));
            for (Tag t : one.getList("weapons", Tag.TAG_INT_ARRAY)) f.weapons.add(NbtUtils.loadUUID(t));
            f.marching = one.getBoolean("marching");
            f.fought = one.getBoolean("fought");
            f.age = one.getInt("age");
            f.quiet = one.getInt("quiet");
            data.forces.put(UUID.fromString(id), f);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        forces.forEach((id, f) -> {
            CompoundTag one = new CompoundTag();
            one.putString("squad", f.squad);
            ListTag weapons = new ListTag();
            for (UUID w : f.weapons) weapons.add(NbtUtils.createUUID(w));
            one.put("weapons", weapons);
            ListTag members = new ListTag();
            for (UUID m : f.members) members.add(NbtUtils.createUUID(m));
            one.put("members", members);
            one.putBoolean("marching", f.marching);
            one.putBoolean("fought", f.fought);
            one.putInt("age", f.age);
            one.putInt("quiet", f.quiet);
            all.put(id.toString(), one);
        });
        tag.put("forces", all);
        return tag;
    }
}
