package dev.storm.npcaddon.client;

import com.mojang.math.Axis;
import dev.storm.npcaddon.group.Stance;
import dev.storm.npcaddon.network.HudPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The squad HUD: a panel (name, stance, health, order per member), a marker above every member in view, and an
 * arrow on the screen edge, with the distance, for members out of view. Purely client side.
 */
public final class HudOverlay {
    private static final int MAX_ROWS = 10;
    private static final int PANEL_W = 150;
    private static final int ROW_H = 11;
    private static final int EDGE = 16;

    private HudOverlay() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (!ClientHud.enabled || !ClientHud.hasSquad()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        Font font = mc.font;

        panel(g, font);
        markers(g, font, mc, partial, w, h);
    }

    private static void panel(GuiGraphics g, Font font) {
        var members = ClientHud.members();
        int rows = Math.min(members.size(), MAX_ROWS);
        int extra = members.size() - rows;
        int height = 14 + rows * ROW_H + (extra > 0 ? ROW_H : 0) + 3;
        g.fill(4, 4, 4 + PANEL_W, 4 + height, 0xA0141922);
        g.renderOutline(4, 4, PANEL_W, height, 0xFF3A4556);
        Stance st = Stance.parse(ClientHud.stance());
        String head = font.plainSubstrByWidth(ClientHud.squad(), 70) + (st == null ? "" : "  " + st.label);
        g.drawString(font, head, 8, 8, 0xFFFFFFFF);
        for (int i = 0; i < rows; i++) {
            HudPacket.Member m = members.get(i);
            int y = 20 + i * ROW_H;
            int nameColor = m.loaded() ? 0xFFFFFFFF : 0xFF707070;
            String stars = SquadScreen.stars(m.rank());
            String name = font.plainSubstrByWidth(m.name(), 54 - font.width(stars));
            g.drawString(font, name, 8, y, nameColor);
            g.drawString(font, stars, 8 + font.width(name), y, 0xFFFFD24A);
            int bx = 64;
            g.fill(bx, y + 1, bx + 36, y + 7, 0xFF2A0F0F);
            if (m.loaded() && m.maxHealth() > 0) {
                float frac = Math.max(0, Math.min(1, m.health() / m.maxHealth()));
                int color = frac > 0.5f ? 0xFF4CAF50 : frac > 0.25f ? 0xFFE0B04B : 0xFFE05A4B;
                g.fill(bx, y + 1, bx + (int) (36 * frac), y + 7, color);
            }
            g.drawString(font, font.plainSubstrByWidth(m.state(), 46), bx + 40, y, SquadScreen.stateColor(m.state()));
        }
        if (extra > 0) g.drawString(font, "+" + extra + " more", 8, 20 + rows * ROW_H, 0xFF8B98A8);
    }

    private static void markers(GuiGraphics g, Font font, Minecraft mc, float partial, int w, int h) {
        Camera cam = mc.gameRenderer.getMainCamera();
        if (!cam.isInitialized()) return;
        String dim = mc.level.dimension().location().toString();

        Set<UUID> wanted = new HashSet<>();
        for (HudPacket.Member m : ClientHud.members()) if (m.loaded() && m.dim().equals(dim)) wanted.add(m.id());
        // Live entities give smooth positions; the synced position (half a second old) covers far-away NPCs.
        Map<UUID, Entity> live = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) if (wanted.contains(e.getUUID())) live.put(e.getUUID(), e);

        Vector3f fwd = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        double tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        double aspect = (double) w / h;
        Vec3 camPos = cam.getPosition();

        for (HudPacket.Member m : ClientHud.members()) {
            if (!wanted.contains(m.id())) continue;
            Entity e = live.get(m.id());
            Vec3 pos = e != null ? e.getPosition(partial).add(0, e.getBbHeight() + 0.5, 0)
                : new Vec3(m.x(), m.y() + 2.3, m.z());
            Vec3 d = pos.subtract(camPos);
            double depth = d.x * fwd.x() + d.y * fwd.y() + d.z * fwd.z();
            double right = -(d.x * left.x() + d.y * left.y() + d.z * left.z());
            double upV = d.x * up.x() + d.y * up.y() + d.z * up.z();
            int color = SquadScreen.stateColor(m.state());

            if (depth > 0.1) {
                double nx = right / (depth * tanHalf * aspect);
                double ny = upV / (depth * tanHalf);
                if (Math.abs(nx) <= 0.96 && Math.abs(ny) <= 0.96) {
                    int sx = (int) (w / 2.0 * (1 + nx));
                    int sy = (int) (h / 2.0 * (1 - ny));
                    drawDown(g, sx, sy, color);
                    continue;
                }
            }
            edgeArrow(g, font, w, h, right, upV, Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z), color);
        }
    }

    private static void edgeArrow(GuiGraphics g, Font font, int w, int h, double right, double up, double dist, int color) {
        double dx = right;
        double dy = -up;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6) {
            dx = 0;
            dy = 1; // straight behind or ahead: point down
        } else {
            dx /= len;
            dy /= len;
        }
        double halfW = w / 2.0 - EDGE;
        double halfH = h / 2.0 - EDGE;
        double t = Math.min(Math.abs(dx) < 1e-6 ? Double.MAX_VALUE : halfW / Math.abs(dx),
            Math.abs(dy) < 1e-6 ? Double.MAX_VALUE : halfH / Math.abs(dy));
        int x = (int) (w / 2.0 + dx * t);
        int y = (int) (h / 2.0 + dy * t);
        float angle = (float) Math.atan2(dy, dx);

        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotation(angle));
        drawRight(g, 1.5f, 0xFF000000);
        drawRight(g, 1.0f, color);
        g.pose().popPose();
        g.drawCenteredString(font, (int) dist + "m", x - (int) (dx * 14), y - (int) (dy * 14) - 4, color);
    }

    /** Triangle pointing +x, centred on the origin (the caller rotates). */
    private static void drawRight(GuiGraphics g, float scale, int color) {
        g.pose().pushPose();
        g.pose().scale(scale, scale, 1);
        for (int i = 0; i < 6; i++) {
            int half = 5 - i;
            g.fill(i - 3, -half, i - 2, half + 1, color);
        }
        g.pose().popPose();
    }

    /** Small triangle pointing down, tip at (x, y). */
    private static void drawDown(GuiGraphics g, int x, int y, int color) {
        for (int i = 0; i < 6; i++) {
            int half = 5 - i;
            g.fill(x - half - 1, y - 7 + i, x + half + 2, y - 6 + i, 0xFF000000);
        }
        for (int i = 0; i < 6; i++) {
            int half = 5 - i;
            g.fill(x - half, y - 7 + i, x + half + 1, y - 6 + i, color);
        }
    }
}
