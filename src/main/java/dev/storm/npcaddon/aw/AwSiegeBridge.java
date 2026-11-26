package dev.storm.npcaddon.aw;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.shadowmage.ancientwarfare.core.common.registry.AWCoreIds;
import net.shadowmage.ancientwarfare.core.common.registry.AWCoreRegistryAccess;
import net.shadowmage.ancientwarfare.core.common.siege.SiegeWeaponControl;
import net.shadowmage.ancientwarfare.core.entity.BallistaBoatEntity;
import net.shadowmage.ancientwarfare.core.entity.BallistaMobileEntity;
import net.shadowmage.ancientwarfare.core.entity.CatapultBoatEntity;
import net.shadowmage.ancientwarfare.core.entity.GateEntity;
import net.shadowmage.ancientwarfare.core.entity.BatteringRamEntity;
import net.shadowmage.ancientwarfare.core.entity.CannonTurretStandEntity;
import net.shadowmage.ancientwarfare.core.entity.CatapultMobileEntity;
import net.shadowmage.ancientwarfare.core.entity.ChestPushcartEntity;
import net.shadowmage.ancientwarfare.core.entity.HwachaEntity;

import java.util.ArrayList;
import java.util.List;

/** Siege weapon calls into Ancient Warfare. Like {@link AwBridge}: call only when {@link AwCompat#loaded()}. */
final class AwSiegeBridge {
    private AwSiegeBridge() {}

    static boolean isWeapon(Entity e) {
        return e instanceof SiegeWeaponControl;
    }

    static boolean isRam(Entity e) {
        return e instanceof BatteringRamEntity;
    }

    static boolean isMobile(Entity e) {
        return e instanceof ChestPushcartEntity && ((SiegeWeaponControl) e).isMobile();
    }

    static boolean isBoat(Entity e) {
        return e instanceof BallistaBoatEntity || e instanceof CatapultBoatEntity;
    }

    static boolean isGate(Entity e) {
        return e instanceof GateEntity;
    }

    static Entity gateAt(Level level, BlockPos pos) {
        for (GateEntity gate : level.getEntitiesOfClass(GateEntity.class, new AABB(pos).inflate(16))) {
            if (gate.ownsProxyPosition(pos)) return gate;
        }
        return null;
    }

    static void drive(Entity weapon, float forward, float turn) {
        if (weapon instanceof ChestPushcartEntity cart) cart.setVehicleInput(forward, turn);
    }

    /** The same box in front of the ram that Ancient Warfare damages. */
    static boolean ramReaches(Entity ram, Entity target) {
        double yaw = Math.toRadians(ram.getYRot());
        Vec3 c = ram.position().add(-Math.sin(yaw) * 1.75, 0.9, Math.cos(yaw) * 1.75);
        return new AABB(c.x - 1.15, c.y - 0.8, c.z - 1.15, c.x + 1.15, c.y + 0.8, c.z + 1.15)
            .intersects(target.getBoundingBox());
    }

    static boolean ramStrike(Entity ram, PathfinderMob crew, Entity target) {
        if (!ramReaches(ram, target)) return false;
        if (target instanceof LivingEntity living) return ((SiegeWeaponControl) ram).fireAt(crew, living);
        return ((BatteringRamEntity) ram).tryAttack();
    }

    static double minRange(Entity weapon) {
        return ((SiegeWeaponControl) weapon).minimumAttackRange();
    }

    static double maxRange(Entity weapon) {
        return ((SiegeWeaponControl) weapon).maximumAttackRange();
    }

    static boolean begin(Entity weapon, PathfinderMob crew) {
        SiegeWeaponControl w = (SiegeWeaponControl) weapon;
        return w.canBeOperatedBy(crew) && w.beginOperation(crew);
    }

    static void end(Entity weapon, PathfinderMob crew) {
        ((SiegeWeaponControl) weapon).stopOperation(crew);
    }

    static boolean canAttack(Entity weapon, LivingEntity target) {
        return ((SiegeWeaponControl) weapon).canAttackTarget(target);
    }

    /**
     * Fires once if the weapon is ready and can reach the target. With unlimited ammo the weapon's inventory is put
     * back as it was afterwards, and a standard round is lent to it if it holds none.
     */
    static boolean fire(Entity weapon, PathfinderMob crew, LivingEntity target, boolean unlimited) {
        SiegeWeaponControl w = (SiegeWeaponControl) weapon;
        if (!unlimited || !(weapon instanceof ChestPushcartEntity cart)) return w.fireAt(crew, target);

        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < cart.getContainerSize(); i++) saved.add(cart.getItem(i).copy());
        try {
            if (w.fireAt(crew, target)) return true; // loaded ammo (any type), refunded below
            ResourceLocation id = defaultAmmo(weapon);
            if (id == null || cart.getContainerSize() == 0) return false;
            int slot = 0;
            for (int i = 0; i < cart.getContainerSize(); i++) {
                if (cart.getItem(i).isEmpty()) {
                    slot = i;
                    break;
                }
            }
            cart.setItem(slot, AWCoreRegistryAccess.stack(id));
            return w.fireAt(crew, target);
        } finally {
            for (int i = 0; i < saved.size(); i++) cart.setItem(i, saved.get(i));
            cart.setChanged();
        }
    }

    private static ResourceLocation defaultAmmo(Entity weapon) {
        if (weapon instanceof HwachaEntity) return AWCoreIds.AMMO_HWACHA_ROCKET;
        if (weapon instanceof BallistaMobileEntity) return AWCoreIds.AMMO_BALLISTA_BOLT;
        if (weapon instanceof CannonTurretStandEntity) return AWCoreIds.AMMO_IRON_SHOT_10;
        if (weapon instanceof CatapultMobileEntity) return AWCoreIds.AMMO_STONE_SHOT_10;
        return null;
    }
}
