package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.RecruitOfferPacket;
import dev.storm.npcaddon.network.RecruitPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Hire an NPC (type, price, which squad), or dismiss one of your recruits. */
public class RecruitScreen extends Screen {
    private static final int PW = 220;
    private static final int PH = 126;
    private static final int PANEL = 0xF0141922;
    private static final int HEADER = 0xFF1F2733;
    private static final int BORDER = 0xFF3A4556;
    private static final int LABEL = 0xFF8B98A8;

    private final RecruitOfferPacket offer;
    private EditBox squad;
    private int px;
    private int py;

    private RecruitScreen(RecruitOfferPacket offer) {
        super(Component.literal("Recruit"));
        this.offer = offer;
    }

    public static void open(RecruitOfferPacket offer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.setScreen(new RecruitScreen(offer));
    }

    private boolean hiring() {
        return offer.member().isEmpty();
    }

    private boolean affordable() {
        return minecraft.player.getAbilities().instabuild || offer.gold() >= offer.type().price;
    }

    /** The player's first squad, otherwise a name made from the player's own. */
    private String suggestion() {
        if (!offer.squads().isEmpty()) return offer.squads().get(0);
        String n = minecraft.player.getGameProfile().getName().replaceAll("[^A-Za-z0-9_]", "");
        return (n.length() > 26 ? n.substring(0, 26) : n) + "_squad";
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        if (hiring()) {
            String typed = squad == null ? suggestion() : squad.getValue();
            squad = new EditBox(font, px + 60, py + 68, PW - 68, 16, Component.literal("Squad"));
            squad.setMaxLength(32);
            squad.setFilter(s -> s.matches("[A-Za-z0-9_]{0,32}"));
            squad.setValue(typed);
            addRenderableWidget(squad);
            addRenderableWidget(new FlatButton(px + 8, py + 96, 100, 22, "Hire", new ItemStack(Items.GOLD_INGOT),
                0x8A6D1F, "Pay the price; the NPC joins the squad", () -> send(false)));
        } else {
            addRenderableWidget(new FlatButton(px + 8, py + 96, 100, 22, "Dismiss", null, 0x8A2F2F,
                "Let this recruit go (no refund)", () -> send(true)));
        }
        addRenderableWidget(new FlatButton(px + PW - 108, py + 96, 100, 22, "Cancel", null, 0x3A4556,
            "Close this window", this::onClose));
    }

    private void send(boolean dismiss) {
        String name = squad == null ? "" : squad.getValue().trim();
        if (!dismiss && name.isEmpty()) return;
        Net.CHANNEL.sendToServer(new RecruitPacket(offer.entity(), dismiss, name));
        onClose();
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
        g.drawString(font, font.plainSubstrByWidth(offer.name(), PW - 16), px + 8, py + 7, 0xFFFFFFFF);
        g.drawString(font, offer.type().label + " recruit", px + 8, py + 30, 0xFFFFFFFF);
        if (hiring()) {
            g.drawString(font, "Price: " + offer.type().price + " gold ingots", px + 8, py + 42, 0xFFE0B04B);
            g.drawString(font, "You have " + offer.gold(), px + 8, py + 54, affordable() ? LABEL : 0xFFE05A4B);
            g.drawString(font, "Squad", px + 8, py + 72, LABEL);
        } else {
            g.drawString(font, "In your squad " + offer.member(), px + 8, py + 46, LABEL);
        }
        super.render(g, mx, my, pt);
    }
}
