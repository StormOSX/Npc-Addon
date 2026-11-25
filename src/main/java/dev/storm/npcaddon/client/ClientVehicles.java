package dev.storm.npcaddon.client;

import dev.storm.npcaddon.aw.AwSiege;
import dev.storm.npcaddon.network.VehicleInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

/** Client side of NPC-driven vehicles and NPC-ridden mounts. */
@Mod.EventBusSubscriber(modid = "npcaddon", value = Dist.CLIENT)
public final class ClientVehicles {
    private ClientVehicles() {}

    public static void apply(VehicleInputPacket p) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        Entity vehicle = level.getEntity(p.entity());
        if (!AwSiege.isWeapon(vehicle)) return;
        if (p.forward() != 0 || p.turn() != 0) vehicle.setMaxUpStep(1.0F);
        AwSiege.drive(vehicle, p.forward(), p.turn());
    }

    /** A standing mount keeps its body where its head points; the game would let it rest up to 50 degrees off. */
    @SubscribeEvent
    public static void straighten(LivingEvent.LivingTickEvent e) {
        LivingEntity mount = e.getEntity();
        if (!mount.level().isClientSide || !(mount.getFirstPassenger() instanceof EntityNPCInterface)) return;
        double dx = mount.getX() - mount.xo;
        double dz = mount.getZ() - mount.zo;
        if (dx * dx + dz * dz < 1.0E-4) mount.yBodyRot = mount.getYRot();
    }
}
