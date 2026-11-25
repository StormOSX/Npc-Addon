package dev.storm.npcaddon.aw;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.shadowmage.ancientwarfare.core.common.registry.AWCoreIds;
import net.shadowmage.ancientwarfare.core.common.registry.AWCoreRegistryAccess;
import net.shadowmage.ancientwarfare.core.common.siege.SiegeWeaponControl;
import net.shadowmage.ancientwarfare.core.entity.BallistaMobileEntity;
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

    /** Ranged siege weapons. The battering ram only works on AW gates and is left out. */
    static boolean isWeapon(Entity e) {
        return e instanceof SiegeWeaponControl && !(e instanceof BatteringRamEntity);
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
