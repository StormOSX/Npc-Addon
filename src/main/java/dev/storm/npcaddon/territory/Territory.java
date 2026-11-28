package dev.storm.npcaddon.territory;

import dev.storm.npcaddon.aw.AwRelations;
import dev.storm.npcaddon.faction.FactionRelations;
import dev.storm.npcaddon.faction.Membership;
import dev.storm.npcaddon.faction.Relation;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.TerritoryPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import noppes.npcs.controllers.FactionController;
import noppes.npcs.controllers.data.Faction;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * Territory rules. A claim banner claims a circle for a faction. Members claim for their own faction, operators for any.
 * Claims of different factions never overlap. The owner, operators and enemies of the faction can break the banner,
 * which ends the claim.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Territory {
    public static final int NO_FACTION = -1;
    public static final int MIN_RADIUS = 16;
    public static final int MAX_RADIUS = 64;
    public static final int DEFAULT_RADIUS = 32;
    public static final int RADIUS_STEP = 8;
    public static final int PRICE = 20;
    /** How far above and below the banner intruders are noticed. */
    private static final double HEIGHT = 12;
    private static int clock;

    private Territory() {}

    private static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    public static String factionName(int faction) {
        Faction f = faction == NO_FACTION ? null : FactionController.instance.getFaction(faction);
        return f == null ? "no faction" : f.name;
    }

    public static ServerLevel level(MinecraftServer server, TerritoryData.Claim claim) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().toString().equals(claim.dim)) return level;
        }
        return null;
    }

    public static AABB box(TerritoryData.Claim c) {
        return new AABB(c.pos).inflate(c.radius, HEIGHT, c.radius);
    }

    public static boolean inside(TerritoryData.Claim c, Vec3 at) {
        double dx = at.x - (c.pos.getX() + 0.5);
        double dz = at.z - (c.pos.getZ() + 0.5);
        return dx * dx + dz * dz <= (double) c.radius * c.radius && Math.abs(at.y - c.pos.getY()) <= HEIGHT + 2;
    }

    /** Hostile monsters and NPCs (CustomNPCs or Ancient Warfare) of a hostile faction inside the claim. */
    public static List<LivingEntity> intruders(ServerLevel level, TerritoryData.Claim claim) {
        return level.getEntitiesOfClass(LivingEntity.class, box(claim),
            e -> e.isAlive() && inside(claim, e.position()) && isIntruder(level.getServer(), claim.faction, e));
    }

    private static boolean isIntruder(MinecraftServer server, int faction, LivingEntity e) {
        String aw = AwRelations.factionOf(e);
        if (aw != null) return AwRelations.get(server, faction, aw) == Relation.HOSTILE;
        if (e instanceof EntityNPCInterface npc) {
            return npc.faction.id != faction
                && FactionRelations.get(server, faction, npc.faction.id) == Relation.HOSTILE;
        }
        return e instanceof Enemy;
    }

    /** Whether a claim of this faction and radius would run into another faction's claim. */
    private static boolean overlaps(TerritoryData data, String dim, int x, int z, int faction, int radius, int self) {
        if (faction == NO_FACTION) return false;
        for (TerritoryData.Claim c : data.claims()) {
            if (c.id == self || c.faction == NO_FACTION || c.faction == faction || !c.dim.equals(dim)) continue;
            double dx = c.pos.getX() - x;
            double dz = c.pos.getZ() - z;
            double reach = c.radius + radius;
            if (dx * dx + dz * dz < reach * reach) return true;
        }
        return false;
    }

    /** The largest radius up to {@code wanted} that fits, or -1 if not even the smallest does. */
    private static int fit(TerritoryData data, String dim, int x, int z, int faction, int wanted, int self) {
        for (int r = wanted; r >= MIN_RADIUS; r--) {
            if (!overlaps(data, dim, x, z, faction, r, self)) return r;
        }
        return -1;
    }

    /** Operators manage every claim, players their own. */
    public static boolean manages(ServerPlayer p, TerritoryData.Claim c) {
        return p.hasPermissions(2) || p.getUUID().equals(c.owner);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !e.getPlacedBlock().is(ModBlocks.CLAIM_BANNER.get())) return;
        if (!(e.getEntity() instanceof ServerPlayer p)) {
            e.setCanceled(true);
            return;
        }
        int faction = Membership.factionOf(p.server, p.getUUID());
        if (faction == Membership.NONE) {
            if (!p.hasPermissions(2)) {
                e.setCanceled(true);
                tell(p, "Join a faction before claiming territory");
                p.inventoryMenu.sendAllDataToRemote();
                return;
            }
            faction = NO_FACTION;
        }
        TerritoryData data = TerritoryData.get(p.server);
        String dim = level.dimension().location().toString();
        int radius = fit(data, dim, e.getPos().getX(), e.getPos().getZ(), faction, DEFAULT_RADIUS, 0);
        if (radius < 0) {
            e.setCanceled(true);
            tell(p, "Too close to another faction's territory");
            p.inventoryMenu.sendAllDataToRemote();
            return;
        }
        TerritoryData.Claim claim = data.add(dim, e.getPos(), faction, radius, p.getUUID(),
            p.getGameProfile().getName());
        tell(p, faction == NO_FACTION ? "Banner placed: choose its faction"
            : "Claimed " + radius + " blocks around the banner for " + factionName(faction));
        send(p, claim.id);
        outline(p, claim);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel level)
                || !e.getState().is(ModBlocks.CLAIM_BANNER.get())) return;
        TerritoryData.Claim c = TerritoryData.get(p.server).at(level.dimension().location().toString(), e.getPos());
        if (c == null || manages(p, c)) return;
        Faction f = c.faction == NO_FACTION ? null : FactionController.instance.getFaction(c.faction);
        if (f != null && f.isAggressiveToPlayer(p)) return;
        e.setCanceled(true);
        tell(p, "Only the owner, operators and enemies of " + factionName(c.faction) + " can pull down this banner");
    }

    /** The banner block is gone, however that happened. */
    static void removed(ServerLevel level, net.minecraft.core.BlockPos pos) {
        MinecraftServer server = level.getServer();
        TerritoryData data = TerritoryData.get(server);
        TerritoryData.Claim c = data.at(level.dimension().location().toString(), pos);
        if (c == null) return;
        data.remove(c.id);
        ServerPlayer owner = server.getPlayerList().getPlayer(c.owner);
        if (owner != null) {
            tell(owner, "Your " + factionName(c.faction) + " banner at " + pos.getX() + " " + pos.getY() + " "
                + pos.getZ() + " is gone; the claim has ended");
        }
    }

    /** Right-click on a banner: its window for those who manage it, otherwise whose ground this is. */
    static void open(ServerPlayer p, ServerLevel level, net.minecraft.core.BlockPos pos) {
        TerritoryData data = TerritoryData.get(p.server);
        TerritoryData.Claim c = data.at(level.dimension().location().toString(), pos);
        if (c == null) {
            // A banner placed by a command: adopt it for operators.
            if (!p.hasPermissions(2)) return;
            c = data.add(level.dimension().location().toString(), pos, NO_FACTION, DEFAULT_RADIUS, p.getUUID(),
                p.getGameProfile().getName());
        }
        if (manages(p, c)) {
            send(p, c.id);
            outline(p, c);
        } else {
            tell(p, "This ground belongs to " + factionName(c.faction) + " (" + c.radius + " blocks around the banner)");
        }
    }

    /** Sends the claims the player manages; the client opens the window if {@code selected} is not 0. */
    public static void send(ServerPlayer p, int selected) {
        List<TerritoryPacket.Entry> list = new ArrayList<>();
        for (TerritoryData.Claim c : TerritoryData.get(p.server).claims()) {
            if (!manages(p, c)) continue;
            list.add(new TerritoryPacket.Entry(c.id, clip(factionName(c.faction)), c.radius, c.dim,
                c.pos.getX(), c.pos.getY(), c.pos.getZ(), clip(c.ownerName)));
        }
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TerritoryPacket(selected, list));
    }

    private static String clip(String s) {
        return s.length() > 48 ? s.substring(0, 48) : s;
    }

    public static void changeRadius(ServerPlayer p, int id, int step) {
        TerritoryData data = TerritoryData.get(p.server);
        TerritoryData.Claim c = data.claim(id);
        if (c == null || !manages(p, c)) return;
        int want = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, c.radius + Integer.signum(step) * RADIUS_STEP));
        // Growing stops short of a neighbour; shrinking always works.
        int radius = want <= c.radius ? want
            : fit(data, c.dim, c.pos.getX(), c.pos.getZ(), c.faction, want, c.id);
        if (radius <= c.radius && step > 0) {
            if (c.radius < MAX_RADIUS) tell(p, "Another faction's territory is in the way");
            return;
        }
        c.radius = radius;
        data.setDirty();
        outline(p, c);
    }

    /** Operators step through the factions. A faction whose claim would not fit is skipped. */
    public static void changeFaction(ServerPlayer p, int id, int step) {
        TerritoryData data = TerritoryData.get(p.server);
        TerritoryData.Claim c = data.claim(id);
        if (c == null || !p.hasPermissions(2)) return;
        List<Integer> ids = new ArrayList<>(FactionController.instance.factions.keySet());
        ids.sort(null);
        if (ids.isEmpty()) return;
        int dir = step < 0 ? -1 : 1;
        int at = ids.indexOf(c.faction);
        if (at < 0) at = dir > 0 ? -1 : 0;
        for (int i = 1; i <= ids.size(); i++) {
            int next = ids.get(Math.floorMod(at + dir * i, ids.size()));
            if (next == c.faction) continue;
            int radius = fit(data, c.dim, c.pos.getX(), c.pos.getZ(), next, c.radius, c.id);
            if (radius < 0) continue;
            c.faction = next;
            c.radius = radius;
            data.setDirty();
            return;
        }
        tell(p, "No other faction can claim this spot");
    }

    /** Ends a claim and takes its banner down. */
    public static void abandon(ServerPlayer p, int id) {
        TerritoryData data = TerritoryData.get(p.server);
        TerritoryData.Claim c = data.claim(id);
        if (c == null || !manages(p, c)) return;
        ServerLevel level = level(p.server, c);
        if (level != null && level.getBlockState(c.pos).is(ModBlocks.CLAIM_BANNER.get())) {
            level.destroyBlock(c.pos, !p.getAbilities().instabuild);
        }
        data.remove(id);
    }

    /** A ring of particles along the border, shown to this player only. */
    public static void outline(ServerPlayer p, TerritoryData.Claim c) {
        ServerLevel level = p.serverLevel();
        if (!level.dimension().location().toString().equals(c.dim)) return;
        int points = c.radius * 3;
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            double x = c.pos.getX() + 0.5 + Math.cos(a) * c.radius;
            double z = c.pos.getZ() + 0.5 + Math.sin(a) * c.radius;
            for (int h = 0; h < 3; h++) {
                level.sendParticles(p, ParticleTypes.END_ROD, true, x, c.pos.getY() + 0.5 + h, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** A claim whose faction was deleted goes quiet; one whose banner vanished unnoticed ends. */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 100 != 0) return;
        TerritoryData data = TerritoryData.get(e.getServer());
        for (TerritoryData.Claim c : data.claims()) {
            if (c.faction != NO_FACTION && FactionController.instance.getFaction(c.faction) == null) {
                c.faction = NO_FACTION;
                data.setDirty();
            }
            ServerLevel level = level(e.getServer(), c);
            if (level != null && level.hasChunkAt(c.pos)
                    && !level.getBlockState(c.pos).is(ModBlocks.CLAIM_BANNER.get())) {
                removed(level, c.pos);
            }
        }
    }
}
