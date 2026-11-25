package dev.storm.npcaddon.aw;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;

/** Ancient Warfare siege weapons, safe to call without AW (nothing is ever a weapon then). */
public final class AwSiege {
    private AwSiege() {}

    public static boolean isWeapon(Entity e) {
        return AwCompat.loaded() && e != null && AwSiegeBridge.isWeapon(e);
    }

    public static double minRange(Entity weapon) {
        return isWeapon(weapon) ? AwSiegeBridge.minRange(weapon) : 0;
    }

    public static double maxRange(Entity weapon) {
        return isWeapon(weapon) ? AwSiegeBridge.maxRange(weapon) : 0;
    }

    /** Seats the crew at the weapon; false if it is taken or broken. */
    public static boolean begin(Entity weapon, PathfinderMob crew) {
        return isWeapon(weapon) && AwSiegeBridge.begin(weapon, crew);
    }

    public static void end(Entity weapon, PathfinderMob crew) {
        if (isWeapon(weapon)) AwSiegeBridge.end(weapon, crew);
    }

    public static boolean canAttack(Entity weapon, LivingEntity target) {
        return isWeapon(weapon) && AwSiegeBridge.canAttack(weapon, target);
    }

    public static boolean fire(Entity weapon, PathfinderMob crew, LivingEntity target, boolean unlimited) {
        return isWeapon(weapon) && AwSiegeBridge.fire(weapon, crew, target, unlimited);
    }
}
