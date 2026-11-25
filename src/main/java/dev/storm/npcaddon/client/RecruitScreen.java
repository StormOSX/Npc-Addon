package dev.storm.npcaddon.client;

import dev.storm.npcaddon.group.RecruitType;
import dev.storm.npcaddon.network.ManageRecruitPacket;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.RecruitPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Dismiss one of your recruits. */
public class RecruitScreen extends Screen {
    private static final int PW = 220;
    private static final int PH = 86;
    private static final int PANEL = 0xF0141922;
    private static final int HEADER = 0xFF1F2733;
    private static final int BORDER = 0xFF3A4556;
    private static final int LABEL = 0xFF8B98A8;

    private final ManageRecruitPacket recruit;
    private int px;
    private int py;

    private RecruitScreen(ManageRecruitPacket recruit) {
        super(Component.literal("Recruit"));
        this.recruit = recruit;
    }

    public static void open(ManageRecruitPacket recruit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new RecruitScreen(recruit));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        addRenderableWidget(new FlatButton(px + 8, py + 56, 100, 22, "Dismiss", null, 0x8A2F2F,
            "Let this recruit go (no refund)", () -> {
                Net.CHANNEL.sendToServer(new RecruitPacket(RecruitPacket.Action.DISMISS, recruit.entity(), "", "",
                    RecruitType.MELEE));
                onClose();
            }));
        addRenderableWidget(new FlatButton(px + PW - 108, py + 56, 100, 22, "Cancel", null, 0x3A4556,
            "Close this window", this::onClose));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + PW, py + PH, PANEL);
        g.renderOutline(px, py, PW, PH, BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 22, HEADER);
        g.drawString(font, font.plainSubstrByWidth(recruit.name(), PW - 16), px + 8, py + 7, 0xFFFFFFFF);
        g.drawString(font, "In your squad " + recruit.squad(), px + 8, py + 34, LABEL);
        super.render(g, mx, my, pt);
    }
}
