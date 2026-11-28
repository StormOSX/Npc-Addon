package dev.storm.npcaddon.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** World-saved territory claims, one per claim banner. */
public class TerritoryData extends SavedData {
    private static final String NAME = "npcaddon_territory";
    private final Map<Integer, Claim> claims = new LinkedHashMap<>();
    private int nextId = 1;

    public static final class Claim {
        public final int id;
        /** Dimension id, e.g. "minecraft:overworld". */
        public final String dim;
        public final BlockPos pos;
        public int faction;
        public int radius;
        /** The player who placed the banner. */
        public final UUID owner;
        public final String ownerName;

        Claim(int id, String dim, BlockPos pos, int faction, int radius, UUID owner, String ownerName) {
            this.id = id;
            this.dim = dim;
            this.pos = pos;
            this.faction = faction;
            this.radius = radius;
            this.owner = owner;
            this.ownerName = ownerName;
        }
    }

    public static TerritoryData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TerritoryData::load, TerritoryData::new, NAME);
    }

    /** A copy, safe to change claims while going through it. */
    public List<Claim> claims() {
        return new ArrayList<>(claims.values());
    }

    public Claim claim(int id) {
        return claims.get(id);
    }

    public Claim at(String dim, BlockPos pos) {
        for (Claim c : claims.values()) if (c.dim.equals(dim) && c.pos.equals(pos)) return c;
        return null;
    }

    Claim add(String dim, BlockPos pos, int faction, int radius, UUID owner, String ownerName) {
        Claim c = new Claim(nextId++, dim, pos.immutable(), faction, radius, owner, ownerName);
        claims.put(c.id, c);
        setDirty();
        return c;
    }

    void remove(int id) {
        if (claims.remove(id) != null) setDirty();
    }

    public static TerritoryData load(CompoundTag tag) {
        TerritoryData data = new TerritoryData();
        for (Tag t : tag.getList("claims", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (!c.hasUUID("owner")) continue;
            Claim claim = new Claim(c.getInt("id"), c.getString("dim"), BlockPos.of(c.getLong("pos")),
                c.getInt("faction"), c.getInt("radius"), c.getUUID("owner"), c.getString("ownerName"));
            data.claims.put(claim.id, claim);
            data.nextId = Math.max(data.nextId, claim.id + 1);
        }
        data.nextId = Math.max(data.nextId, tag.getInt("nextId"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Claim c : claims.values()) {
            CompoundTag one = new CompoundTag();
            one.putInt("id", c.id);
            one.putString("dim", c.dim);
            one.putLong("pos", c.pos.asLong());
            one.putInt("faction", c.faction);
            one.putInt("radius", c.radius);
            one.putUUID("owner", c.owner);
            one.putString("ownerName", c.ownerName);
            list.add(one);
        }
        tag.put("claims", list);
        tag.putInt("nextId", nextId);
        return tag;
    }
}
