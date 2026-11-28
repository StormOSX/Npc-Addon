package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.TerritoryActionPacket;
import dev.storm.npcaddon.network.TerritoryActionPacket.Action;
import dev.storm.npcaddon.network.TerritoryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Territory claims: the list the player manages, with radius, faction (operators) and removal. */
public class TerritoryScreen extends Screen {
    private static final int PW = 420;
    private static final int PH = 198;
    private static final int ROWS = 10;
    private static final int ROW_H = 14;
    private static final int LIST_W = 210;

    private static final int PANEL = 0xF0141922;
    private static final int HEADER = 0xFF1F2733;
    private static final int BORDER = 0xFF3A4556;
    private static final int WELL = 0xFF0E1218;
    private static final int LABEL = 0xFF8B98A8;
    private static final int SELECTED = 0xFF2B4A73;

    private static List<TerritoryPacket.Entry> claims = List.of();

    private int selected;
    private int scroll;
    private int px;
    private int py;
    private int by;
    private int colA;
    private int colB;

    private TerritoryScreen(int selected) {
        super(Component.literal("NPC Territory"));
        this.selected = selected;
    }

    public static void receive(TerritoryPacket p) {
        claims = List.copyOf(p.entries());
        if (p.open() == 0) return;
        Minecraft.getInstance().setScreen(new TerritoryScreen(Math.max(0, p.open())));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = Tabs.top(height);
        by = py + 30;
        colA = px + 8;
        colB = colA + LIST_W + 10;
        int w = PW - (colB - px) - 8;

        if (entry() == null && !claims.isEmpty()) selected = claims.get(0).id();
        for (int i = 0; i < claims.size(); i++) {
            if (claims.get(i).id() == selected) scroll = Math.max(0, Math.min(i, claims.size() - ROWS));
        }

        addRenderableWidget(new FlatButton(colB, by + 58, w / 2 - 2, 18, "Radius -", null, 0x4F6B8F,
            "Shrink the claim", () -> send(Action.RADIUS, -1)));
        addRenderableWidget(new FlatButton(colB + w / 2 + 2, by + 58, w / 2 - 2, 18, "Radius +", null, 0x4F6B8F,
            "Grow the claim, as far as the neighbours allow", () -> send(Action.RADIUS, 1)));
        if (minecraft.player != null && minecraft.player.hasPermissions(2)) {
            addRenderableWidget(new FlatButton(colB, by + 80, w / 2 - 2, 18, "< Faction", null, 0x6B4FA0,
                "Give the claim to the previous faction", () -> send(Action.FACTION, -1)));
            addRenderableWidget(new FlatButton(colB + w / 2 + 2, by + 80, w / 2 - 2, 18, "Faction >", null, 0x6B4FA0,
                "Give the claim to the next faction", () -> send(Action.FACTION, 1)));
        }
        addRenderableWidget(new FlatButton(colB, by + 104, w, 18, "Show border", null, 0x3F7F8F,
            "Mark the edge with particles (in your dimension, nearby)", () -> send(Action.SHOW, 0)));
        addRenderableWidget(new FlatButton(colB, by + 126, w, 18, "Remove claim", null, 0x8A2F2F,
            "End the claim and take the banner down", () -> send(Action.REMOVE, 0)));
    }

    private void send(Action action, int value) {
        if (entry() != null) Net.CHANNEL.sendToServer(new TerritoryActionPacket(action, selected, value));
    }

    private TerritoryPacket.Entry entry() {
        for (TerritoryPacket.Entry e : claims) if (e.id() == selected) return e;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (entry() == null && !claims.isEmpty()) selected = claims.get(0).id();
        scroll = Math.max(0, Math.min(scroll, claims.size() - ROWS));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean overList(double mx, double my) {
        return mx >= colA && mx < colA + LIST_W && my >= by + 12 && my < by + 12 + ROWS * ROW_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overList(mx, my)) {
            int i = scroll + (int) ((my - (by + 12)) / ROW_H);
            if (i < claims.size()) selected = claims.get(i).id();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (overList(mx, my)) {
            int max = Math.max(0, claims.size() - ROWS);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(delta)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + PW, py + PH, PANEL);
        g.renderOutline(px, py, PW, PH, BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 22, HEADER);
        g.drawString(font, "NPC Territory", px + 8, py + 7, 0xFFFFFFFF);

        g.drawString(font, "CLAIMS", colA, by, LABEL);
        g.fill(colA, by + 10, colA + LIST_W, by + 14 + ROWS * ROW_H, WELL);
        g.renderOutline(colA, by + 10, LIST_W, ROWS * ROW_H + 4, BORDER);
        if (claims.isEmpty()) g.drawString(font, "No claims", colA + 4, by + 16, 0xFF707070);
        for (int r = 0; r < ROWS && scroll + r < claims.size(); r++) {
            TerritoryPacket.Entry e = claims.get(scroll + r);
            int y = by + 12 + r * ROW_H;
            if (e.id() == selected) g.fill(colA + 1, y - 1, colA + LIST_W - 1, y + ROW_H - 1, SELECTED);
            g.drawString(font, font.plainSubstrByWidth(e.faction(), 100), colA + 4, y + 1, 0xFFFFFFFF);
            String at = e.x() + ", " + e.z();
            g.drawString(font, at, colA + LIST_W - 5 - font.width(at), y + 1, LABEL);
        }

        TerritoryPacket.Entry e = entry();
        int w = PW - (colB - px) - 8;
        if (e != null) {
            g.drawString(font, font.plainSubstrByWidth(e.faction(), w), colB, by, 0xFFFFFFFF);
            g.drawString(font, "Radius " + e.radius() + " blocks", colB, by + 14, 0xFFE0B04B);
            String dim = e.dim().substring(e.dim().indexOf(':') + 1);
            g.drawString(font, font.plainSubstrByWidth(e.x() + " " + e.y() + " " + e.z() + "  " + dim, w), colB,
                by + 28, LABEL);
            g.drawString(font, font.plainSubstrByWidth("Placed by " + e.owner(), w), colB, by + 42, LABEL);
        }
        super.render(g, mx, my, pt);
    }
}
