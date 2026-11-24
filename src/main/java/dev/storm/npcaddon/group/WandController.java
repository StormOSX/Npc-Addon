package dev.storm.npcaddon.group;

import dev.storm.npcaddon.item.WandItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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
    private static final double REACH = 64;
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

    /** The radius last used from the GUI is what the wand's area attack uses. */
    public static void rememberRadius(ServerPlayer p, double radius) {
        AREA_RADIUS.put(p.getUUID(), radius);
    }

    public static void click(ServerPlayer p, boolean right, boolean ctrl) {
        if (!holdingWand(p)) return;
        if (!p.hasPermissions(2)) {
            say(p, "The command wand needs operator permission");
            return;
        }
        GroupData data = GroupData.get(p.server.overworld());

        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        HitResult hit = p.pick(REACH, 1.0F, false);
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(REACH)) : hit.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, box,
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable(), REACH * REACH);
        LivingEntity target = eh != null && eh.getEntity() instanceof LivingEntity le ? le : null;

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
        } else if (ctrl && (target != null || hit.getType() == HitResult.Type.BLOCK)) {
            Vec3 at = target != null ? target.position()
                : Vec3.atBottomCenterOf(((BlockHitResult) hit).getBlockPos().relative(((BlockHitResult) hit).getDirection()));
            double radius = AREA_RADIUS.getOrDefault(p.getUUID(), DEFAULT_AREA_RADIUS);
            cmd = String.format(Locale.ROOT, "npcaddon group attackarea %s %.1f %.2f %.2f %.2f", squad, radius,
                at.x, at.y, at.z);
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
                if (level.getEntity(id) instanceof EntityNPCInterface n && n.isAlive()) {
                    return "Following".equals(GroupMover.describe(n));
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
