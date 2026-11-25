package dev.storm.npcaddon.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.storm.npcaddon.group.WandAim;
import dev.storm.npcaddon.item.WandItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * While the wand is held with Ctrl (area attack) or Alt (bombard) down, draws where the order would land: a post at the
 * aim point and a ring on the ground showing the radius.
 */
@Mod.EventBusSubscriber(modid = "npcaddon", value = Dist.CLIENT)
public final class WandMarker {
    private static final int SEGMENTS = 72;
    private static final double POST_HEIGHT = 3;

    private WandMarker() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.screen != null || !ClientHud.hasSquad()) return;
        if (!(p.getMainHandItem().getItem() instanceof WandItem) || p.isShiftKeyDown()) return;
        boolean bombard = Screen.hasAltDown();
        if (!bombard && !Screen.hasControlDown()) return;

        WandAim.Aim aim = WandAim.of(p, e.getPartialTick());
        // Ctrl + right-click on a siege weapon crews it instead of attacking an area.
        if (!bombard && aim.weapon() != null) return;
        Vec3 at = aim.point();
        if (at == null) return;
        double radius = bombard ? WandAim.bombardRadius(ClientHud.wandRadius())
            : WandAim.areaRadius(ClientHud.wandRadius());
        float r = 1f;
        float g = bombard ? 0.6f : 0.2f;
        float b = bombard ? 0.1f : 0.15f;

        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        line(pose, lines, at, at.add(0, POST_HEIGHT, 0), r, g, b);
        line(pose, lines, at.add(-0.7, 0.05, 0), at.add(0.7, 0.05, 0), r, g, b);
        line(pose, lines, at.add(0, 0.05, -0.7), at.add(0, 0.05, 0.7), r, g, b);
        if (radius > 0) {
            Vec3 prev = onGround(mc, p, at, radius, 0);
            for (int i = 1; i <= SEGMENTS; i++) {
                Vec3 next = onGround(mc, p, at, radius, Math.PI * 2 * i / SEGMENTS);
                // Two stacked rings read better than one thin line.
                line(pose, lines, prev, next, r, g, b);
                line(pose, lines, prev.add(0, 0.25, 0), next.add(0, 0.25, 0), r, g, b);
                prev = next;
            }
        }

        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** A point of the ring, dropped onto the terrain if there is any near the centre's height. */
    private static Vec3 onGround(Minecraft mc, LocalPlayer p, Vec3 centre, double radius, double angle) {
        double x = centre.x + Math.cos(angle) * radius;
        double z = centre.z + Math.sin(angle) * radius;
        BlockHitResult hit = mc.level.clip(new ClipContext(new Vec3(x, centre.y + 4, z), new Vec3(x, centre.y - 8, z),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        double y = hit.getType() == HitResult.Type.MISS ? centre.y : hit.getLocation().y;
        return new Vec3(x, y + 0.1, z);
    }

    private static void line(PoseStack pose, VertexConsumer out, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 n = b.subtract(a).normalize();
        for (Vec3 v : new Vec3[]{a, b}) {
            out.vertex(pose.last().pose(), (float) v.x, (float) v.y, (float) v.z)
                .color(r, g, bl, 1f)
                .normal(pose.last().normal(), (float) n.x, (float) n.y, (float) n.z)
                .endVertex();
        }
    }
}
