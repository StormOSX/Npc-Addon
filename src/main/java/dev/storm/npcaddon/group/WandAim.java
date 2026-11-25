package dev.storm.npcaddon.group;

import dev.storm.npcaddon.aw.AwSiege;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What the command wand is pointing at. The server uses it to give orders and the client to draw the marker, so both
 * always agree.
 */
public final class WandAim {
    private static final double REACH = 64;

    /** @param entity the living entity or siege weapon under the crosshair, or null */
    public record Aim(HitResult hit, Entity entity) {
        public LivingEntity living() {
            return entity instanceof LivingEntity le ? le : null;
        }

        public Entity weapon() {
            return AwSiege.isWeapon(entity) ? entity : null;
        }

        public Entity gate() {
            return AwSiege.isGate(entity) ? entity : null;
        }

        /** Where an area or bombard order would be centred: the entity's feet or the space in front of the block. */
        public Vec3 point() {
            if (living() != null) return entity.position();
            if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
                return Vec3.atBottomCenterOf(bh.getBlockPos().relative(bh.getDirection()));
            }
            return null;
        }
    }

    private WandAim() {}

    public static Aim of(Player p, float partialTick) {
        Vec3 eye = p.getEyePosition(partialTick);
        Vec3 look = p.getViewVector(partialTick);
        HitResult hit = p.pick(REACH, partialTick, false);
        Vec3 end = hit.getType() == HitResult.Type.MISS ? eye.add(look.scale(REACH)) : hit.getLocation();
        AABB box = p.getBoundingBox().expandTowards(look.scale(REACH)).inflate(1.0);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, eye, end, box,
            e -> (e instanceof LivingEntity || AwSiege.isWeapon(e) || AwSiege.isGate(e)) && e.isAlive() && !e.isSpectator() && e.isPickable()
                && e != p.getVehicle(), REACH * REACH);
        Entity entity = eh == null ? null : eh.getEntity();
        if (entity == null && hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            entity = AwSiege.gateAt(p.level(), bh.getBlockPos());
        }
        return new Aim(hit, entity);
    }

    /** Radius an area attack uses for the player's chosen radius. */
    public static double areaRadius(double chosen) {
        return Math.max(2, Math.min(48, chosen));
    }

    /** Radius a bombardment uses for the player's chosen radius. */
    public static double bombardRadius(double chosen) {
        return Math.max(0, Math.min(32, chosen));
    }
}
