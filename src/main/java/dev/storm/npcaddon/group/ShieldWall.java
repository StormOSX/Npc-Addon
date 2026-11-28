package dev.storm.npcaddon.group;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A squad formed as a shield wall holds its line: a member with a shield who stands in its spot is braced. A braced NPC
 * does not leave the spot (it still strikes whatever comes into reach), is hard to knock back and takes much less
 * damage from the front. It turns towards its target only within an arc ahead, so the flanks and rear stay open.
 * The spot is kept in the NPC's persistent data until another order.
 */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class ShieldWall {
    static final String TAG = "npcaddon_wall";
    private static final double HOLD_DIST = 1.3;
    private static final double HOLD_HEIGHT = 2.5;
    private static final float TURN_ARC = 60;
    private static final double GUARD_ARC = 70;
    /** Share of frontal damage that gets through. */
    private static final float THROUGH = 0.3f;
    private static final UUID SPEED_ID = UUID.fromString("0b6c8a1e-3d57-4a53-9b53-5c3f0d9b2e21");
    private static final UUID KNOCKBACK_ID = UUID.fromString("9d2f4c73-8a15-4a8e-8f2a-1e7b6c0a5d32");
    /** Braced NPCs and the yaw each guards. */
    private static final Map<UUID, Float> BRACED = new HashMap<>();
    private static int clock;

    private ShieldWall() {}

    /** Gives the NPC its place in the wall. Call after the move order that takes it there. */
    public static void assign(EntityNPCInterface npc, Vec3 spot, float yaw) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Vec3 ground = GroupMover.grounded(level, spot);
        CompoundTag tag = new CompoundTag();
        Retreat.putVec(tag, "spot", ground);
        tag.putFloat("yaw", yaw);
        npc.getPersistentData().put(TAG, tag);
    }

    /** Takes the NPC out of the wall: used before every new order. */
    public static void end(EntityNPCInterface npc) {
        npc.getPersistentData().remove(TAG);
        relax(npc);
    }

    static boolean isAssigned(EntityNPCInterface npc) {
        return npc.getPersistentData().contains(TAG);
    }

    static boolean isBraced(EntityNPCInterface npc) {
        return BRACED.containsKey(npc.getUUID());
    }

    private static boolean hasShield(EntityNPCInterface npc) {
        return isShield(npc.getOffhandItem()) || isShield(npc.getMainHandItem());
    }

    private static boolean isShield(ItemStack stack) {
        return stack.getItem() instanceof ShieldItem || stack.canPerformAction(ToolActions.SHIELD_BLOCK);
    }

    private static void brace(EntityNPCInterface npc) {
        add(npc.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_ID, -1,
            AttributeModifier.Operation.MULTIPLY_TOTAL);
        add(npc.getAttribute(Attributes.KNOCKBACK_RESISTANCE), KNOCKBACK_ID, 0.8,
            AttributeModifier.Operation.ADDITION);
    }

    private static void add(AttributeInstance attribute, UUID id, double amount, AttributeModifier.Operation op) {
        if (attribute != null && attribute.getModifier(id) == null) {
            // Transient: never saved, so a braced NPC cannot get stuck if the addon is removed.
            attribute.addTransientModifier(new AttributeModifier(id, "npcaddon shield wall", amount, op));
        }
    }

    private static void relax(EntityNPCInterface npc) {
        BRACED.remove(npc.getUUID());
        AttributeInstance speed = npc.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
        AttributeInstance knockback = npc.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) knockback.removeModifier(KNOCKBACK_ID);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        clock++;
        MinecraftServer server = e.getServer();
        GroupData data = GroupData.get(server.overworld());
        Set<UUID> done = new HashSet<>();
        for (String name : data.names()) {
            for (UUID id : data.members(name)) {
                if (!done.add(id)) continue;
                for (ServerLevel level : server.getAllLevels()) {
                    if (level.getEntity(id) instanceof EntityNPCInterface npc && npc.isAlive()) {
                        if (isAssigned(npc)) update(npc, level, data);
                        break;
                    }
                }
            }
        }
    }

    private static void update(EntityNPCInterface npc, ServerLevel level, GroupData data) {
        CompoundTag tag = npc.getPersistentData().getCompound(TAG);
        Vec3 spot = Retreat.vec(tag, "spot");
        double flat = npc.position().subtract(spot).multiply(1, 0, 1).length();
        boolean free = !Retreat.isRetreating(npc) && !SiegeCrew.isCrew(npc) && !npc.isPassenger()
            && !data.isMedic(npc.getUUID()) && !npc.isInteracting();
        boolean inPlace = flat <= HOLD_DIST && Math.abs(npc.getY() - spot.y) <= HOLD_HEIGHT;
        if (!free || !hasShield(npc) || !inPlace) {
            if (isBraced(npc)) relax(npc);
            // Pulled or pushed out of the line: back into it.
            if (free && !inPlace && hasShield(npc) && !GroupMover.isWalking(npc) && clock % 10 == 0
                    && npc.getTarget() == null && npc.getNavigation().isDone()) {
                GroupMover.head(npc, level, spot, 0, 1.1);
            }
            return;
        }
        // Still walking up: brace once the move order has finished or the spot is reached.
        if (GroupMover.isWalking(npc) && flat > 0.9) return;
        float yaw = tag.getFloat("yaw");
        LivingEntity target = npc.getTarget();
        if (target != null && target.isAlive()) {
            Vec3 to = target.position().subtract(npc.position());
            float at = (float) (Mth.atan2(to.z, to.x) * 180 / Math.PI) - 90;
            if (Math.abs(Mth.wrapDegrees(at - yaw)) <= TURN_ARC) yaw = at;
        }
        brace(npc);
        npc.getNavigation().stop();
        GroupMover.turn(npc, yaw);
        BRACED.put(npc.getUUID(), yaw);
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        if (!(e.getEntity() instanceof EntityNPCInterface npc) || npc.level().isClientSide) return;
        Float yaw = BRACED.get(npc.getUUID());
        if (yaw == null || !isAssigned(npc)) return;
        var attacker = e.getSource().getEntity();
        Vec3 from = attacker != null ? attacker.position() : e.getSource().getSourcePosition();
        if (from == null) return;
        Vec3 way = from.subtract(npc.position()).multiply(1, 0, 1);
        if (way.lengthSqr() < 1e-4) return;
        double rad = Math.toRadians(yaw);
        Vec3 ahead = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
        if (way.normalize().dot(ahead) < Math.cos(Math.toRadians(GUARD_ARC))) return;
        e.setAmount(e.getAmount() * THROUGH);
        npc.level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.NEUTRAL,
            0.8f, 0.9f + npc.getRandom().nextFloat() * 0.2f);
    }
}
