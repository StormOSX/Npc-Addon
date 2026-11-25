package dev.storm.npcaddon.aw;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** Ancient Warfare siege weapons, safe to call without AW (nothing is ever a weapon then). */
public final class AwSiege {
    private AwSiege() {}

    public static boolean isWeapon(Entity e) {
        return AwCompat.loaded() && e != null && AwSiegeBridge.isWeapon(e);
    }

    public static boolean isRam(Entity e) {
        return isWeapon(e) && AwSiegeBridge.isRam(e);
    }

    /** Whether the weapon can be driven over land. */
    public static boolean isMobile(Entity e) {
        return isWeapon(e) && AwSiegeBridge.isMobile(e);
    }

    public static boolean isGate(Entity e) {
        return AwCompat.loaded() && e != null && AwSiegeBridge.isGate(e);
    }

    /** The gate that this block belongs to, or null. Gates are made of invisible blocks owned by one gate entity. */
    public static Entity gateAt(Level level, BlockPos pos) {
        return AwCompat.loaded() ? AwSiegeBridge.gateAt(level, pos) : null;
    }

    /** Steers a mobile weapon that has a rider: forward and turn are each -1..1; the input stays until changed. */
    public static void drive(Entity weapon, float forward, float turn) {
        if (isWeapon(weapon)) AwSiegeBridge.drive(weapon, forward, turn);
    }

    public static boolean ramReaches(Entity ram, Entity target) {
        return isRam(ram) && AwSiegeBridge.ramReaches(ram, target);
    }

    /** Swings the ram at a gate or creature in front of it; false while it is still recovering or out of reach. */
    public static boolean ramStrike(Entity ram, PathfinderMob crew, Entity target) {
        return isRam(ram) && AwSiegeBridge.ramStrike(ram, crew, target);
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
