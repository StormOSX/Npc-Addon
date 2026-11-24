package dev.storm.npcaddon.client;

import dev.storm.npcaddon.faction.Relation;
import dev.storm.npcaddon.network.FactionListPacket;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.SetRelationPacket;
import dev.storm.npcaddon.network.SquadOrderPacket;
import dev.storm.npcaddon.network.SquadOrderPacket.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** Faction relations: pick two factions from the lists, then set hostile / neutral / allied between them. */
public class AddonScreen extends Screen {
    private static final int PW = 420;
    private static final int PH = 220;
    private static final int ROWS = 9;
    private static final int ROW_H = 14;
    private static final int LIST_W = 150;

    private static final int PANEL = 0xF0141922;
    private static final int HEADER = 0xFF1F2733;
    private static final int BORDER = 0xFF3A4556;
    private static final int WELL = 0xFF0E1218;
    private static final int LABEL = 0xFF8B98A8;
    private static final int SELECTED = 0xFF2B4A73;

    private int px;
    private int py;
    private int by;
    private int colA;
    private int colB;
    private int colC;
    private String selA = "";
    private String selB = "";
    private int scrollA;
    private int scrollB;

    public AddonScreen() {
        super(Component.literal("NPC Factions"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        by = py + 30;
        colA = px + 8;
        colB = px + 8 + LIST_W + 8;
        colC = colB + LIST_W + 8;

        Tabs.build(minecraft, px, py, PW, Tabs.FACTIONS, this::addRenderableWidget);

        int x = colC;
        int w = PW - (x - px) - 8;
        addRenderableWidget(new FlatButton(x, by + 70, w, 22, "Hostile", new ItemStack(Items.IRON_SWORD), 0xB03A2E,
            "Both factions attack each other", () -> send(Relation.HOSTILE)));
        addRenderableWidget(new FlatButton(x, by + 96, w, 22, "Neutral", new ItemStack(Items.PAPER), 0x4A5568,
            "Remove hostility and any alliance", () -> send(Relation.NEUTRAL)));
        addRenderableWidget(new FlatButton(x, by + 122, w, 22, "Allied", new ItemStack(Items.EMERALD), 0x3F8F3F,
            "No hostility; recorded as allies by the addon", () -> send(Relation.ALLIED)));

        Net.CHANNEL.sendToServer(new SquadOrderPacket(Action.FACTIONS, "", 0, ""));
    }

    private static FactionListPacket.Info find(String key) {
        for (FactionListPacket.Info f : ClientSquads.factions()) if (f.key().equals(key)) return f;
        return null;
    }

    private void send(Relation r) {
        FactionListPacket.Info a = find(selA);
        FactionListPacket.Info b = find(selB);
        if (a == null || b == null) return;
        Net.CHANNEL.sendToServer(new SetRelationPacket(a.id(), a.aw() ? a.name() : "", b.id(), b.aw() ? b.name() : "", r));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean overList(double mx, double my, int x) {
        return mx >= x && mx < x + LIST_W && my >= by + 12 && my < by + 12 + ROWS * ROW_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        List<FactionListPacket.Info> list = ClientSquads.factions();
        for (int side = 0; side < 2; side++) {
            int x = side == 0 ? colA : colB;
            if (!overList(mx, my, x)) continue;
            int i = (side == 0 ? scrollA : scrollB) + (int) ((my - (by + 12)) / ROW_H);
            if (i < list.size()) {
                if (side == 0) selA = list.get(i).key();
                else selB = list.get(i).key();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) -Math.signum(delta);
        int max = Math.max(0, ClientSquads.factions().size() - ROWS);
        if (overList(mx, my, colA)) {
            scrollA = Math.max(0, Math.min(max, scrollA + step));
            return true;
        }
        if (overList(mx, my, colB)) {
            scrollB = Math.max(0, Math.min(max, scrollB + step));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private static String nameOf(String key) {
        FactionListPacket.Info f = find(key);
        if (f == null) return "none";
        return f.aw() ? f.name() + " (AW)" : f.name() + " (" + f.id() + ")";
    }

    private void drawList(GuiGraphics g, int x, int scroll, String selected, String label) {
        g.drawString(font, label, x, by, LABEL);
        g.fill(x, by + 10, x + LIST_W, by + 14 + ROWS * ROW_H, WELL);
        g.renderOutline(x, by + 10, LIST_W, ROWS * ROW_H + 4, BORDER);
        List<FactionListPacket.Info> list = ClientSquads.factions();
        if (list.isEmpty()) g.drawString(font, "No factions found", x + 4, by + 16, 0xFF707070);
        for (int r = 0; r < ROWS && scroll + r < list.size(); r++) {
            FactionListPacket.Info f = list.get(scroll + r);
            int y = by + 12 + r * ROW_H;
            if (f.key().equals(selected)) g.fill(x + 1, y - 1, x + LIST_W - 1, y + ROW_H - 1, SELECTED);
            g.fill(x + 4, y + 1, x + 10, y + 9, 0xFF000000 | (f.color() & 0xFFFFFF));
            g.drawString(font, font.plainSubstrByWidth(f.name(), LIST_W - 40), x + 14, y + 1, 0xFFFFFFFF);
            String id = f.aw() ? "AW" : String.valueOf(f.id());
            g.drawString(font, id, x + LIST_W - 4 - font.width(id), y + 1, 0xFF8B98A8);
        }
        if (list.size() > ROWS) {
            int trackH = ROWS * ROW_H;
            int barH = Math.max(8, trackH * ROWS / list.size());
            int barY = by + 12 + (trackH - barH) * scroll / (list.size() - ROWS);
            g.fill(x + LIST_W - 3, barY, x + LIST_W - 1, barY + barH, 0xFF5A6678);
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + PW, py + PH, PANEL);
        g.renderOutline(px, py, PW, PH, BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 22, HEADER);
        g.drawString(font, "NPC Factions", px + 8, py + 7, 0xFFFFFFFF);

        drawList(g, colA, scrollA, selA, "FACTION A");
        drawList(g, colB, scrollB, selB, "FACTION B");

        g.drawString(font, "RELATION", colC, by, LABEL);
        int w = PW - (colC - px) - 8;
        g.drawString(font, font.plainSubstrByWidth(nameOf(selA), w), colC, by + 14, 0xFFFFFFFF);
        g.drawString(font, "and", colC, by + 28, LABEL);
        g.drawString(font, font.plainSubstrByWidth(nameOf(selB), w), colC, by + 42, 0xFFFFFFFF);
        FactionListPacket.Info fa = find(selA);
        FactionListPacket.Info fb = find(selB);
        if (fa == null || fb == null) g.drawString(font, "Pick one from each list", colC, by + 152, 0xFF707070);
        else if (fa.aw() && fb.aw()) g.drawString(font, font.plainSubstrByWidth("AW keeps its own", w), colC, by + 152, 0xFFE0B04B);
        super.render(g, mx, my, pt);
    }
}
