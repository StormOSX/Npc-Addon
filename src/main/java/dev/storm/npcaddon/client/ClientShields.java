package dev.storm.npcaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.client.model.animation.AnimationBase;
import noppes.npcs.client.model.animation.AnimationHandler;
import noppes.npcs.entity.EntityNPCInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * The second rank of a shield wall holds its shields angled over the front rank. CustomNPCs applies an animation to its
 * humanoid models from the NPC's current animation number, so the pose is registered as one more animation and switched
 * on only while such an NPC is being drawn.
 */
@Mod.EventBusSubscriber(modid = "npcaddon", value = Dist.CLIENT)
public final class ClientShields {
    private static final int ANIMATION = 7301;
    /** How far the shield arm is raised (radians about the shoulder; vanilla blocking is about -0.94). */
    private static final float ARM_PITCH = -2.3f;
    private static final float ARM_YAW = 0.25f;
    /** The server repeats the pose every second; one that stops arriving lapses. */
    private static final int LASTS = 50;
    /** Entity id -> client game time until which the pose holds. */
    private static final Map<Integer, Long> RAISED = new HashMap<>();
    /** Animation numbers to put back after drawing. */
    private static final Map<Integer, Integer> BEFORE = new HashMap<>();
    private static boolean registered;

    private ClientShields() {}

    public static void set(int entity, boolean raised) {
        var level = Minecraft.getInstance().level;
        if (raised && level != null) RAISED.put(entity, level.getGameTime() + LASTS);
        else RAISED.remove(entity);
    }

    private static void register() {
        if (registered) return;
        registered = true;
        AnimationHandler.addAnimation(ANIMATION, new AnimationBase() {
            @Override
            public void animatePre(float swing, float amount, float age, float yaw, float pitch, Entity entity,
                    HumanoidModel model, int start) {
            }

            @Override
            public void animatePost(float swing, float amount, float age, float yaw, float pitch, Entity entity,
                    HumanoidModel model, int start) {
                if (!(entity instanceof LivingEntity living)) return;
                HumanoidArm arm = living.getUsedItemHand() == InteractionHand.MAIN_HAND ? living.getMainArm()
                    : living.getMainArm().getOpposite();
                boolean left = arm == HumanoidArm.LEFT;
                ModelPart part = left ? model.leftArm : model.rightArm;
                part.xRot = ARM_PITCH;
                part.yRot = left ? ARM_YAW : -ARM_YAW;
                part.zRot = 0;
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void before(RenderLivingEvent.Pre<?, ?> e) {
        if (!(e.getEntity() instanceof EntityNPCInterface npc)) return;
        Long until = RAISED.get(npc.getId());
        if (until == null) return;
        long now = npc.level().getGameTime();
        // A time far ahead is left over from another world.
        if (now > until || until - now > LASTS) {
            RAISED.remove(npc.getId());
            return;
        }
        register();
        BEFORE.putIfAbsent(npc.getId(), npc.currentAnimation);
        npc.currentAnimation = ANIMATION;
    }

    @SubscribeEvent
    public static void after(RenderLivingEvent.Post<?, ?> e) {
        if (!(e.getEntity() instanceof EntityNPCInterface npc)) return;
        Integer was = BEFORE.remove(npc.getId());
        if (was != null) npc.currentAnimation = was;
    }
}
