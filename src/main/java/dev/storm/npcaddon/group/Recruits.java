package dev.storm.npcaddon.group;

import dev.storm.npcaddon.item.ModItems;
import dev.storm.npcaddon.item.WandItem;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.RecruitOfferPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hiring NPCs. An operator gives an NPC a recruit type; while it is in no squad, a player can right-click it and pay
 * gold ingots to add it to a squad of their own. Players command only the squads they own; operators command all of
 * them. A recruit leaves when its owner dismisses it or when it dies.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class Recruits {
    private static final String TYPE = "npcaddon_recruit_type";
    private static final double HIRE_REACH_SQ = 8 * 8;
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

    public static boolean canUse(ServerPlayer p, String squad) {
        GroupData data = GroupData.get(p.server.overworld());
        return data.exists(squad) && (p.hasPermissions(2) || p.getUUID().equals(data.owner(squad)));
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

    private static boolean isHireable(ServerPlayer p, EntityNPCInterface npc) {
        return npc.isAlive() && type(npc) != null && !npc.faction.isAggressiveToPlayer(p)
            && GroupData.get(p.server.overworld()).squadsOf(npc.getUUID()).isEmpty();
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

    /** Right-click on a hireable NPC opens the hire window; sneak + right-click on your own recruit manages it. */
    @SubscribeEvent
    public static void interact(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof EntityNPCInterface npc)) return;
        RecruitType type = type(npc);
        if (type == null) return;
        ItemStack held = p.getMainHandItem();
        // CustomNPCs' own tools keep working on recruits.
        var id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (held.getItem() instanceof WandItem || (id != null && id.getNamespace().equals("customnpcs"))) return;
        GroupData data = GroupData.get(p.server.overworld());
        String mine = ownSquad(data, npc.getUUID(), p);
        boolean offer = isHireable(p, npc);
        if (!offer && !(mine != null && p.isShiftKeyDown())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (e.getHand() != InteractionHand.MAIN_HAND) return;
        List<String> squads = new ArrayList<>();
        for (String name : data.names()) if (p.getUUID().equals(data.owner(name))) squads.add(name);
        String name = npc.getName().getString();
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new RecruitOfferPacket(npc.getId(),
            name.length() > 48 ? name.substring(0, 48) : name, type, gold(p), squads, offer ? "" : mine));
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

    /** Takes the price and adds the NPC to the squad, creating it for this player if it is new. */
    public static void hire(ServerPlayer p, int entityId, String squad) {
        EntityNPCInterface npc = near(p, entityId);
        if (npc == null || !isHireable(p, npc)) {
            tell(p, "That NPC cannot be hired right now");
            return;
        }
        if (!squad.matches("[A-Za-z0-9_]{1,32}")) {
            tell(p, "Squad names use letters, digits and underscores only");
            return;
        }
        GroupData data = GroupData.get(p.server.overworld());
        if (data.exists(squad) && !canUse(p, squad)) {
            tell(p, "The squad name '" + squad + "' is taken");
            return;
        }
        RecruitType type = type(npc);
        if (!p.getAbilities().instabuild) {
            if (gold(p) < type.price) {
                tell(p, "You need " + type.price + " gold ingots");
                return;
            }
            p.getInventory().clearOrCountMatchingItems(s -> s.is(Items.GOLD_INGOT), type.price,
                p.inventoryMenu.getCraftSlots());
            p.inventoryMenu.broadcastChanges();
        }
        boolean created = !data.exists(squad);
        data.addAll(squad, List.of(npc.getUUID()));
        if (created) data.setOwner(squad, p.getUUID());
        StanceManager.apply(npc, data.stance(squad));
        if (!p.getInventory().contains(new ItemStack(ModItems.WAND.get()))) {
            ItemStack wand = new ItemStack(ModItems.WAND.get());
            if (!p.getInventory().add(wand)) p.drop(wand, false);
        }
        WandController.setSelected(p, squad);
        say(p, npc, "At your service");
        tell(p, type.label + " recruit joined squad '" + squad + "'");
    }

    /** The owner lets a recruit go; it can be hired again. */
    public static void dismiss(ServerPlayer p, int entityId) {
        EntityNPCInterface npc = near(p, entityId);
        GroupData data = GroupData.get(p.server.overworld());
        if (npc == null || ownSquad(data, npc.getUUID(), p) == null) return;
        leave(npc, data);
        say(p, npc, "Farewell");
    }

    /** Takes the NPC out of every player-owned squad and ends whatever it was doing for them. */
    private static void leave(EntityNPCInterface npc, GroupData data) {
        for (String squad : data.squadsOf(npc.getUUID())) {
            if (data.owner(squad) != null) data.removeMember(squad, npc.getUUID());
        }
        GroupMover.cancel(npc);
        Mounts.dismount(npc);
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
                        leave(npc, data);
                        ServerPlayer p = server.getPlayerList().getPlayer(owner);
                        if (p != null) tell(p, npc.getName().getString() + " of squad '" + squad + "' has died");
                        break;
                    }
                }
            }
        }
    }
}
