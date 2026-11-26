package dev.storm.npcaddon.group;

import dev.storm.npcaddon.faction.Membership;
import dev.storm.npcaddon.item.ModItems;
import dev.storm.npcaddon.item.WandItem;
import dev.storm.npcaddon.network.ManageRecruitPacket;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.RecruitMenuPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.packets.server.SPacketToolMobSpawner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Hiring NPCs. Operators save NPCs into the {@link RecruitCatalog}; players pay gold ingots in the Recruit tab for a
 * copy, which travels to them and joins a squad of their own. Players command only the squads they own; operators
 * command all of them. A recruit leaves when its owner dismisses it or when it dies.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Recruits {
    private static final String TYPE = "npcaddon_recruit_type";
    private static final double HIRE_REACH_SQ = 8 * 8;
    private static final int COOLDOWN_TICKS = 20 * 30;
    /** Marks the copies created by hiring; only these leave the world when dismissed. */
    private static final String HIRED = "npcaddon_hired";
    /** CustomNPCs' respawn setting "no". */
    private static final int NEVER_RESPAWN = 3;
    private static final Map<UUID, Long> READY_AT = new HashMap<>();
    private static int clock;

    private Recruits() {}

    /** The NPC's recruit type, or null for an ordinary NPC (which has no limits). */
    public static RecruitType type(EntityNPCInterface npc) {
        return RecruitType.parse(npc.getPersistentData().getString(TYPE));
    }

    public static void setType(EntityNPCInterface npc, RecruitType type) {
        if (type == null) npc.getPersistentData().remove(TYPE);
        else npc.getPersistentData().putString(TYPE, type.key());
    }

    public static boolean canRide(EntityNPCInterface npc) {
        RecruitType t = type(npc);
        return t == null || t == RecruitType.MOUNTED;
    }

    public static boolean canCrew(EntityNPCInterface npc) {
        RecruitType t = type(npc);
        return t == null || t == RecruitType.ENGINEER;
    }

    /** Whether the player may give the squad orders: operators, its owner, or members of its faction. */
    public static boolean canUse(ServerPlayer p, String squad) {
        return manages(p, squad) || isFactionSquad(p, squad);
    }

    /** Whether the squad is theirs to hire into or delete: operators and the owner. */
    public static boolean manages(ServerPlayer p, String squad) {
        GroupData data = GroupData.get(p.server.overworld());
        return data.exists(squad) && (p.hasPermissions(2) || p.getUUID().equals(data.owner(squad)));
    }

    /** A squad no player owns whose (loaded) NPCs all belong to the player's faction. */
    private static boolean isFactionSquad(ServerPlayer p, String squad) {
        GroupData data = GroupData.get(p.server.overworld());
        if (!data.exists(squad) || data.owner(squad) != null) return false;
        int own = Membership.factionOf(p.server, p.getUUID());
        if (own == Membership.NONE) return false;
        boolean any = false;
        for (UUID id : data.members(squad)) {
            for (ServerLevel level : p.server.getAllLevels()) {
                if (level.getEntity(id) instanceof EntityNPCInterface npc) {
                    if (npc.faction.id != own) return false;
                    any = true;
                    break;
                }
            }
        }
        return any;
    }

    public static boolean canUse(CommandSourceStack s, String squad) {
        if (s.hasPermission(2)) return GroupData.get(s.getServer().overworld()).exists(squad);
        return s.getEntity() instanceof ServerPlayer p && canUse(p, squad);
    }

    /** A squad of this player that contains the NPC, or null. */
    private static String ownSquad(GroupData data, UUID npc, ServerPlayer p) {
        for (String squad : data.squadsOf(npc)) if (p.getUUID().equals(data.owner(squad))) return squad;
        return null;
    }

    private static int gold(ServerPlayer p) {
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.GOLD_INGOT)) n += stack.getCount();
        }
        return n;
    }

    /** Sneak + right-click on your own recruit opens the window to dismiss it. */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof EntityNPCInterface npc)) return;
        if (type(npc) == null || !p.isShiftKeyDown()) return;
        ItemStack held = p.getMainHandItem();
        // CustomNPCs' own tools keep working on recruits.
        var id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (held.getItem() instanceof WandItem || (id != null && id.getNamespace().equals("customnpcs"))) return;
        String mine = ownSquad(GroupData.get(p.server.overworld()), npc.getUUID(), p);
        if (mine == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        String name = npc.getName().getString();
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ManageRecruitPacket(npc.getId(),
            name.length() > 48 ? name.substring(0, 48) : name, mine));
    }

    private static EntityNPCInterface near(ServerPlayer p, int entityId) {
        return p.level().getEntity(entityId) instanceof EntityNPCInterface npc && npc.distanceToSqr(p) <= HIRE_REACH_SQ
            ? npc : null;
    }

    private static void tell(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal(text));
    }

    private static void say(ServerPlayer p, EntityNPCInterface npc, String text) {
        tell(p, "<" + npc.getName().getString() + "> " + text);
    }

    private static int cooldown(ServerPlayer p) {
        long left = READY_AT.getOrDefault(p.getUUID(), 0L) - p.server.getTickCount();
        // A time far ahead is left over from another world in the same game session.
        return left > 0 && left <= COOLDOWN_TICKS ? (int) Math.ceil(left / 20.0) : 0;
    }

    /** Sends the player what the Recruit tab shows. */
    public static void sendMenu(ServerPlayer p) {
        List<RecruitMenuPacket.Entry> list = new ArrayList<>();
        RecruitCatalog.get(p.server).entries()
            .forEach((name, e) -> list.add(new RecruitMenuPacket.Entry(name, e.type())));
        GroupData data = GroupData.get(p.server.overworld());
        List<String> squads = new ArrayList<>();
        for (String name : data.names()) if (p.getUUID().equals(data.owner(name))) squads.add(name);
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
            new RecruitMenuPacket(list, gold(p), squads, cooldown(p)));
    }

    /**
     * Takes the price and creates a copy of the catalog NPC some way off, which joins the squad (new squads become the
     * player's) and travels to them. Mounted recruits come on a horse of their own.
     */
    public static void hire(ServerPlayer p, String template, String squad) {
        RecruitCatalog.Entry entry = RecruitCatalog.get(p.server).entries().get(template);
        if (entry == null) {
            tell(p, "That recruit is no longer available");
            return;
        }
        if (!squad.matches("[A-Za-z0-9_]{1,32}")) {
            tell(p, "Squad names use letters, digits and underscores only");
            return;
        }
        GroupData data = GroupData.get(p.server.overworld());
        if (data.exists(squad) && !manages(p, squad)) {
            tell(p, "The squad name '" + squad + "' is taken");
            return;
        }
        int wait = cooldown(p);
        if (wait > 0) {
            tell(p, "You can hire again in " + wait + " seconds");
            return;
        }
        RecruitType type = entry.type();
        boolean free = p.getAbilities().instabuild;
        if (!free && gold(p) < type.price) {
            tell(p, "You need " + type.price + " gold ingots");
            return;
        }
        boolean mounted = type == RecruitType.MOUNTED;
        ServerLevel level = p.serverLevel();
        Vec3 at = Arrivals.farSpot(p, mounted);
        if (at == null) {
            tell(p, "No recruit can reach you here: find more open ground");
            return;
        }
        EntityNPCInterface npc = spawnCopy(level, entry, at);
        if (npc == null) {
            tell(p, "That recruit could not be created");
            return;
        }
        if (npc.faction.isAggressiveToPlayer(p)) {
            npc.discard();
            tell(p, "That recruit's faction is hostile to you");
            return;
        }
        if (!free) {
            p.getInventory().clearOrCountMatchingItems(s -> s.is(Items.GOLD_INGOT), type.price,
                p.inventoryMenu.getCraftSlots());
            p.inventoryMenu.broadcastChanges();
        }
        if (mounted) giveHorse(level, npc, at);

        boolean created = !data.exists(squad);
        data.addAll(squad, List.of(npc.getUUID()));
        if (created) data.setOwner(squad, p.getUUID());
        StanceManager.apply(npc, data.stance(squad));
        GroupMover.follow(npc, p, data.members(squad).size() - 1);
        GroupMover.faceLeader(npc, p);
        Arrivals.track(npc, p);

        if (!p.getInventory().contains(new ItemStack(ModItems.WAND.get()))) {
            ItemStack wand = new ItemStack(ModItems.WAND.get());
            if (!p.getInventory().add(wand)) p.drop(wand, false);
        }
        WandController.setSelected(p, squad);
        READY_AT.put(p.getUUID(), (long) p.server.getTickCount() + COOLDOWN_TICKS);
        tell(p, type.label + " recruit hired for squad '" + squad + "' and on the way");
    }

    /** A new NPC made from a catalog entry, standing at the spot; null if it cannot be made. */
    static EntityNPCInterface spawnCopy(ServerLevel level, RecruitCatalog.Entry entry, Vec3 at) {
        Entity spawned = SPacketToolMobSpawner.spawnClone(entry.npc().copy(), at.x, at.y, at.z, level);
        if (!(spawned instanceof EntityNPCInterface npc)) {
            if (spawned != null) spawned.discard();
            return null;
        }
        setType(npc, entry.type());
        npc.getPersistentData().putBoolean(HIRED, true);
        npc.ais.setStartPos(npc.blockPosition());
        npc.stats.spawnCycle = NEVER_RESPAWN; // a dead recruit stays dead
        return npc;
    }

    static void giveHorse(ServerLevel level, EntityNPCInterface npc, Vec3 at) {
        Horse horse = EntityType.HORSE.create(level);
        if (horse == null) return;
        horse.moveTo(at.x, at.y, at.z, npc.getYRot(), 0);
        horse.finalizeSpawn(level, level.getCurrentDifficultyAt(horse.blockPosition()), MobSpawnType.EVENT, null, null);
        horse.setAge(0);
        horse.setTamed(true);
        horse.setPersistenceRequired();
        level.addFreshEntity(horse);
        npc.getPersistentData().putUUID(Departures.HORSE, horse.getUUID());
        npc.startRiding(horse, true);
    }

    /** Sells a war horn for gold ingots. */
    public static void buyHorn(ServerPlayer p) {
        if (!p.getAbilities().instabuild) {
            if (gold(p) < Reinforcements.HORN_PRICE) {
                tell(p, "A war horn costs " + Reinforcements.HORN_PRICE + " gold ingots");
                return;
            }
            p.getInventory().clearOrCountMatchingItems(s -> s.is(Items.GOLD_INGOT), Reinforcements.HORN_PRICE,
                p.inventoryMenu.getCraftSlots());
            p.inventoryMenu.broadcastChanges();
        }
        ItemStack horn = new ItemStack(ModItems.WAR_HORN.get());
        if (!p.getInventory().add(horn)) p.drop(horn, false);
        tell(p, "You bought a war horn");
    }

    /** The NPC under the crosshair, also when the aim lands on the siege weapon or mount it is sitting on. */
    private static EntityNPCInterface aimedNpc(ServerPlayer p) {
        Entity aimed = WandAim.of(p, 1.0F).entity();
        if (aimed instanceof EntityNPCInterface npc) return npc;
        if (aimed != null) {
            for (Entity rider : aimed.getPassengers()) {
                if (rider instanceof EntityNPCInterface npc) return npc;
            }
        }
        return null;
    }

    /** Saves the NPC the operator is looking at as a recruit. */
    public static void saveAimed(ServerPlayer p, String name, RecruitType type) {
        if (!p.hasPermissions(2)) return;
        if (!name.matches("[A-Za-z0-9_]{1,32}")) {
            tell(p, "Recruit names use letters, digits and underscores only");
        } else if (aimedNpc(p) == null) {
            tell(p, "Look at the NPC you want to save, then open this window");
        } else if (RecruitCatalog.get(p.server).save(name, type, aimedNpc(p))) {
            tell(p, "Saved " + aimedNpc(p).getName().getString() + " as " + type.label.toLowerCase(Locale.ROOT)
                + " recruit '" + name + "' (" + type.price + " gold ingots)");
        } else {
            tell(p, "The recruit list is full or that NPC cannot be saved");
        }
    }

    public static void delete(ServerPlayer p, String name) {
        if (p.hasPermissions(2) && RecruitCatalog.get(p.server).remove(name)) tell(p, "Recruit '" + name + "' removed");
    }

    /** The owner lets a recruit go; it can be hired again. */
    public static void dismiss(ServerPlayer p, int entityId) {
        EntityNPCInterface npc = near(p, entityId);
        GroupData data = GroupData.get(p.server.overworld());
        if (npc == null || ownSquad(data, npc.getUUID(), p) == null) return;
        say(p, npc, "Farewell");
        boolean hired = npc.getPersistentData().getBoolean(HIRED);
        // A recruit leaving the world rides off on the horse it came with.
        leave(npc, data, hired && Departures.onOwnHorse(npc));
        if (hired && data.squadsOf(npc.getUUID()).isEmpty()) Departures.start(npc, p);
    }

    /** Takes the NPC out of every player-owned squad and ends whatever it was doing for them. */
    private static void leave(EntityNPCInterface npc, GroupData data, boolean stayMounted) {
        for (String squad : data.squadsOf(npc.getUUID())) {
            if (data.owner(squad) != null) data.removeMember(squad, npc.getUUID());
        }
        GroupMover.cancel(npc);
        if (!stayMounted) Mounts.dismount(npc);
        npc.setTarget(null);
        GroupMover.haltPatrol(npc);
        npc.ais.setStartPos(npc.blockPosition());
        StanceManager.apply(npc, data.stanceOfMember(npc.getUUID()));
    }

    /** Recruits that died leave their squad (CustomNPCs may respawn them; they can then be hired again). */
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 20 != 0) return;
        MinecraftServer server = e.getServer();
        GroupData data = GroupData.get(server.overworld());
        for (String squad : new ArrayList<>(data.names())) {
            UUID owner = data.owner(squad);
            if (owner == null) continue;
            for (UUID id : new ArrayList<>(data.members(squad))) {
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof EntityNPCInterface npc && !npc.isAlive()) {
                        leave(npc, data, false);
                        ServerPlayer p = server.getPlayerList().getPlayer(owner);
                        if (p != null) tell(p, npc.getName().getString() + " of squad '" + squad + "' has died");
                        break;
                    }
                }
            }
        }
    }
}
