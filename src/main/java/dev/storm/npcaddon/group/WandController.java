package dev.storm.npcaddon.group;

import dev.storm.npcaddon.item.WandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the command wand. Each player has one selected squad (kept in memory). Orders run as the matching
 * /npcaddon group command as the player, so permissions and behaviour are the same as typing it.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class WandController {
    private static final Map<UUID, String> SELECTED = new HashMap<>();
    private static final Map<UUID, Double> AREA_RADIUS = new HashMap<>();
    private static final double DEFAULT_AREA_RADIUS = 10;

    private WandController() {}

    private static void say(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    private static boolean holdingWand(ServerPlayer p) {
        return p.getMainHandItem().getItem() instanceof WandItem;
    }

    /** The squad this player has selected (wand or Squads tab), or null. It drives the HUD. */
    public static String selected(ServerPlayer p) {
        return SELECTED.get(p.getUUID());
    }

    public static void clearSelected(ServerPlayer p) {
        SELECTED.remove(p.getUUID());
    }

    public static void setSelected(ServerPlayer p, String squad) {
        if (GroupData.get(p.server.overworld()).exists(squad)) SELECTED.put(p.getUUID(), squad);
    }

    /** The player's chosen radius for the wand's area attack and bombardment. */
    public static double radius(ServerPlayer p) {
        return AREA_RADIUS.getOrDefault(p.getUUID(), DEFAULT_AREA_RADIUS);
    }

    /** The radius last used from the GUI is what the wand's area orders use. */
    public static void rememberRadius(ServerPlayer p, double radius) {
        AREA_RADIUS.put(p.getUUID(), radius);
    }

    public static void click(ServerPlayer p, boolean right, boolean ctrl, boolean alt) {
        if (!holdingWand(p)) return;
        if (!p.hasPermissions(2)) {
            say(p, "The command wand needs operator permission");
            return;
        }
        GroupData data = GroupData.get(p.server.overworld());

        WandAim.Aim aim = WandAim.of(p, 1.0F);
        HitResult hit = aim.hit();
        LivingEntity target = aim.living();
        Entity weapon = aim.weapon();
        Vec3 point = aim.point();

        if (!right) {
            select(p, data, target);
            return;
        }
        String squad = SELECTED.get(p.getUUID());
        if (squad == null || !data.exists(squad)) {
            SELECTED.remove(p.getUUID());
            say(p, "No squad selected: left-click one of its NPCs first");
            return;
        }
        String cmd;
        if (p.isShiftKeyDown()) {
            cmd = "npcaddon group " + (isFollowing(p, data, squad) ? "stop " : "follow ") + squad;
        } else if (alt) {
            if (point == null) {
                say(p, "Nothing there to bombard");
                return;
            }
            cmd = String.format(Locale.ROOT, "npcaddon group bombard %s %.2f %.2f %.2f %.1f", squad, point.x, point.y,
                point.z, WandAim.bombardRadius(radius(p)));
        } else if (weapon != null) {
            cmd = "npcaddon group manweapon " + squad + " " + weapon.getUUID();
        } else if (aim.gate() != null && !ctrl) {
            cmd = "npcaddon group attack " + squad + " " + aim.gate().getUUID();
        } else if (!ctrl && target != null && target.getFirstPassenger() instanceof EntityNPCInterface rider
                && data.members(squad).contains(rider.getUUID())) {
            say(p, "The squad is already riding that");
            return;
        } else if (!ctrl && target != null && Mounts.isClaimed(target)) {
            say(p, "Someone is already on the way to ride that");
            return;
        } else if (!ctrl && Mounts.wandMounts(p.serverLevel(), data, squad, target)) {
            cmd = "npcaddon group mountone " + squad + " " + target.getUUID();
        } else if (ctrl && point != null) {
            cmd = String.format(Locale.ROOT, "npcaddon group attackarea %s %.1f %.2f %.2f %.2f", squad,
                WandAim.areaRadius(radius(p)), point.x, point.y, point.z);
        } else if (target != null) {
            if (data.members(squad).contains(target.getUUID())) {
                say(p, "That NPC is in the selected squad");
                return;
            }
            cmd = "npcaddon group attack " + squad + " " + target.getUUID();
        } else if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos at = bh.getBlockPos().relative(bh.getDirection());
            Vec3 pos = Vec3.atBottomCenterOf(at);
            cmd = String.format(Locale.ROOT, "npcaddon group moveto %s %.2f %.2f %.2f", squad, pos.x, pos.y, pos.z);
        } else {
            say(p, "Nothing there to order");
            return;
        }
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
    }

    private static void select(ServerPlayer p, GroupData data, LivingEntity target) {
        String squad = null;
        // Clicking a mount selects its rider's squad.
        if (target != null && target.getFirstPassenger() instanceof EntityNPCInterface rider
                && !data.squadsOf(rider.getUUID()).isEmpty()) {
            target = rider;
        }
        if (target instanceof EntityNPCInterface npc) {
            var squads = data.squadsOf(npc.getUUID());
            if (!squads.isEmpty()) {
                // Clicking the same NPC again steps through the squads it belongs to.
                String cur = SELECTED.get(p.getUUID());
                int i = squads.indexOf(cur);
                squad = squads.get((i + 1) % squads.size());
            }
        }
        if (squad == null) {
            SELECTED.remove(p.getUUID());
            say(p, "Squad deselected");
        } else {
            SELECTED.put(p.getUUID(), squad);
            say(p, "Selected squad " + squad + " (" + data.stance(squad).label + ")");
        }
    }

    private static boolean isFollowing(ServerPlayer p, GroupData data, String squad) {
        for (UUID id : data.members(squad)) {
            for (ServerLevel level : p.server.getAllLevels()) {
                if (level.getEntity(id) instanceof EntityNPCInterface n && n.isAlive()
                        && "Following".equals(GroupMover.describe(n))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** While holding the wand, the action bar shows the selected squad. */
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 20 != 0 || !holdingWand(p) || !p.hasPermissions(2)) return;
        String squad = SELECTED.get(p.getUUID());
        GroupData data = GroupData.get(p.server.overworld());
        if (squad != null && data.exists(squad)) {
            say(p, "Squad: " + squad + "  |  " + data.stance(squad).label + "  |  " + data.members(squad).size() + " NPC(s)");
        } else {
            say(p, "Command wand: left-click an NPC to select its squad");
        }
    }
}
