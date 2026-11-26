package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.AllegianceActionPacket;
import dev.storm.npcaddon.network.AllegianceActionPacket.Action;
import dev.storm.npcaddon.network.AllegiancePacket;
import dev.storm.npcaddon.network.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The Join tab: join or leave a faction. Operators also choose which factions are open. */
public class JoinScreen extends Screen {
    private static final int PW = 420;
    private static final int PH = 220;
    private static final int ROWS = 11;
    private static final int ROW_H = 14;
    private static final int LIST_W = 180;

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
    private int scroll;
    private int refreshTimer;
    private int selected = -1;
    private EditBox points;

    public JoinScreen() {
        super(Component.literal("Join a Faction"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = Tabs.top(height);
        by = py + 30;
        colA = px + 8;
        colB = colA + LIST_W + 10;
        int w = PW - (colB - px) - 8;

        Tabs.build(minecraft, px, py, PW, Tabs.JOIN, this::addRenderableWidget);

        addRenderableWidget(new FlatButton(colB, by + 62, w, 22, "Join", new ItemStack(Items.WHITE_BANNER),
            0x3F8F3F, "Join the selected faction (leaves your current one)", () -> {
                if (selected >= 0) send(Action.JOIN, 0);
            }));
        addRenderableWidget(new FlatButton(colB, by + 88, w, 22, "Leave", new ItemStack(Items.BARRIER), 0x8A2F2F,
            "Leave your faction; your earlier standings come back", () -> send(Action.LEAVE, 0)));

        if (minecraft.player != null && minecraft.player.hasPermissions(2)) {
            String old = points == null ? "0" : points.getValue();
            points = new EditBox(font, colB + 80, by + 134, 50, 16, Component.literal("Points needed"));
            points.setFilter(s -> s.matches("\\d{0,6}"));
            points.setValue(old);
            addRenderableWidget(points);
            addRenderableWidget(new FlatButton(colB, by + 154, w / 2 - 2, 18, "Open", null, 0x2F6B3F,
                "Let players join the selected faction if they have the points", () -> {
                    if (selected >= 0) send(Action.OPEN, parse(points.getValue()));
                }));
            addRenderableWidget(new FlatButton(colB + w / 2 + 2, by + 154, w / 2 - 2, 18, "Close", null, 0x8A2F2F,
                "Stop players joining the selected faction", () -> {
                    if (selected >= 0) send(Action.OPEN, -1);
                }));
        }
        send(Action.REFRESH, 0);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void send(Action action, int value) {
        Net.CHANNEL.sendToServer(new AllegianceActionPacket(action, selected, value));
    }

    private AllegiancePacket.Entry entry(int id) {
        for (AllegiancePacket.Entry e : ClientAllegiance.get().entries()) if (e.id() == id) return e;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        List<AllegiancePacket.Entry> list = ClientAllegiance.get().entries();
        if (entry(selected) == null) selected = list.isEmpty() ? -1 : list.get(0).id();
        if (++refreshTimer % 40 == 0) send(Action.REFRESH, 0);
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
            List<AllegiancePacket.Entry> list = ClientAllegiance.get().entries();
            int i = scroll + (int) ((my - (by + 12)) / ROW_H);
            if (i < list.size()) selected = list.get(i).id();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (overList(mx, my)) {
            int max = Math.max(0, ClientAllegiance.get().entries().size() - ROWS);
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
        g.drawString(font, "Allegiance", px + 8, py + 7, 0xFFFFFFFF);

        AllegiancePacket data = ClientAllegiance.get();
        List<AllegiancePacket.Entry> list = data.entries();
        g.drawString(font, "FACTIONS", colA, by, LABEL);
        g.fill(colA, by + 10, colA + LIST_W, by + 14 + ROWS * ROW_H, WELL);
        g.renderOutline(colA, by + 10, LIST_W, ROWS * ROW_H + 4, BORDER);
        if (list.isEmpty()) g.drawString(font, "None open to join", colA + 4, by + 16, 0xFF707070);
        for (int r = 0; r < ROWS && scroll + r < list.size(); r++) {
            AllegiancePacket.Entry e = list.get(scroll + r);
            int y = by + 12 + r * ROW_H;
            if (e.id() == selected) g.fill(colA + 1, y - 1, colA + LIST_W - 1, y + ROW_H - 1, SELECTED);
            g.fill(colA + 4, y + 1, colA + 10, y + 9, 0xFF000000 | (e.color() & 0xFFFFFF));
            g.drawString(font, font.plainSubstrByWidth(e.name(), 110), colA + 14, y + 1, 0xFFFFFFFF);
            String info = e.id() == data.current() ? "yours" : e.need() < 0 ? "closed" : "open";
            g.drawString(font, info, colA + LIST_W - 5 - font.width(info), y + 1,
                e.id() == data.current() ? 0xFF7CFC9A : LABEL);
        }

        AllegiancePacket.Entry mine = entry(data.current());
        g.drawString(font, "Your faction: " + (mine == null ? "none" : mine.name()), colB, by, LABEL);
        AllegiancePacket.Entry e = entry(selected);
        if (e != null) {
            g.drawString(font, e.name(), colB, by + 16, 0xFFFFFFFF);
            g.drawString(font, e.need() < 0 ? "Closed to joining" : "Points needed: " + e.need(), colB, by + 30,
                0xFFE0B04B);
            g.drawString(font, "Your points: " + e.points(), colB, by + 44,
                e.need() < 0 || e.points() >= e.need() ? LABEL : 0xFFE05A4B);
        }
        if (points != null) {
            g.drawString(font, "OPEN TO PLAYERS", colB, by + 120, LABEL);
            g.drawString(font, "Points needed", colB, by + 138, LABEL);
        }
        super.render(g, mx, my, pt);
    }
}
