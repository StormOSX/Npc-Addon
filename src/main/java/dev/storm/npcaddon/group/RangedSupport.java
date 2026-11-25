package dev.storm.npcaddon.group;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.api.item.IItemStack;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ranged squad members (those with a projectile set) keep their distance: when their target comes close they back
 * away while still facing it, so CustomNPCs keeps shooting. The NPC is moved a step at a time; its own tactic settings
 * are not changed, and no AI goal is interrupted (interrupting one would drop the target).
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class RangedSupport {
    /** Preferred minimum distance to the target; shorter for NPCs with a short shooting range. */
    private static final double KEEP_DISTANCE = 8;
    /** How much room is kept between a backing NPC and a drop. */
    private static final double LEDGE_MARGIN = 0.4;

    private RangedSupport() {}

    public static boolean isRanged(EntityNPCInterface npc) {
        IItemStack projectile = npc.inventory.getProjectile();
        return projectile != null && !projectile.isEmpty();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftServer server = e.getServer();
        GroupData data = GroupData.get(server.overworld());
        Set<UUID> done = new HashSet<>();
        for (String name : data.names()) {
            if (data.stance(name) == Stance.PASSIVE) continue;
            for (UUID id : data.members(name)) {
                if (!done.add(id)) continue;
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof EntityNPCInterface npc && npc.isAlive()) {
                        backAway(npc, level);
                        break;
                    }
                }
            }
        }
    }

    private static void backAway(EntityNPCInterface npc, ServerLevel level) {
        LivingEntity target = npc.getTarget();
        if (target == null || !target.isAlive() || target.level() != level) return;
        // Walking orders, siege crews, riders and flying or swimming NPCs are left alone.
        if (!isRanged(npc) || npc.ais.getNavigationType() != 0 || npc.isPassenger() || npc.isInteracting()
                || SiegeCrew.isCrew(npc) || GroupMover.isBusyMoving(npc)) return;
        double keep = Math.max(2, Math.min(KEEP_DISTANCE, npc.stats.ranged.getRange() / 2.0));
        double distance = npc.distanceTo(target);
        int melee = npc.stats.ranged.getMeleeRange();
        // Inside its melee range the NPC is set to fight hand to hand, so it stands its ground.
        if (distance >= keep || (melee > 0 && distance <= melee) || !npc.hasLineOfSight(target)) return;
        Vec3 away = npc.position().subtract(target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) return;
        away = away.normalize();
        // Moved directly, a step at a time, so the spot is known to be safe before the NPC goes there.
        double step = Math.max(0.05, Math.min(0.15, npc.getAttributeValue(Attributes.MOVEMENT_SPEED) * 0.45));
        if (!npc.onGround() || !isSafe(npc, level, away.scale(step))
                || !isSafe(npc, level, away.scale(step + LEDGE_MARGIN))) return;
        npc.lookAt(target, 30.0F, 30.0F);
        npc.move(MoverType.SELF, new Vec3(away.x * step, -0.08, away.z * step));
    }

    /** Whether the NPC could stand this far from where it is: no wall, no liquid, and ground under all of its feet. */
    private static boolean isSafe(EntityNPCInterface npc, ServerLevel level, Vec3 offset) {
        AABB box = npc.getBoundingBox().move(offset);
        // Raised by the step height, so a slab or stair behind does not count as a wall.
        AABB body = box.deflate(0.05, 0, 0.05).setMinY(box.minY + 0.6);
        if (!level.noCollision(npc, body) || level.containsAnyLiquid(box)) return false;
        for (double x : new double[]{box.minX + 0.05, box.maxX - 0.05}) {
            for (double z : new double[]{box.minZ + 0.05, box.maxZ - 0.05}) {
                AABB under = new AABB(x - 0.01, box.minY - 0.6, z - 0.01, x + 0.01, box.minY, z + 0.01);
                if (level.noCollision(npc, under)) return false;
            }
        }
        return true;
    }
}
