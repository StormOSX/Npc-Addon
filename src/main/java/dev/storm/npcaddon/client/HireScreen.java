package dev.storm.npcaddon.client;

import dev.storm.npcaddon.group.RecruitType;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.RecruitMenuPacket;
import dev.storm.npcaddon.network.RecruitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** The Recruit tab: pick a recruit and a squad and hire it. Operators also manage the list here. */
public class HireScreen extends Screen {
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
    private String selected = "";
    private EditBox squad;
    private EditBox saveName;
    private FlatButton typeButton;
    private RecruitType saveType = RecruitType.MELEE;
    private boolean suggested;

    public HireScreen() {
        super(Component.literal("NPC Recruits"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = Tabs.top(height);
        by = py + 30;
        colA = px + 8;
        colB = colA + LIST_W + 10;
        int w = PW - (colB - px) - 8;

        Tabs.build(minecraft, px, py, PW, Tabs.RECRUITS, this::addRenderableWidget);

        String typed = squad == null ? "" : squad.getValue();
        squad = new EditBox(font, colB + 42, by + 60, w - 42, 16, Component.literal("Squad"));
        squad.setMaxLength(32);
        squad.setFilter(s -> s.matches("[A-Za-z0-9_]{0,32}"));
        squad.setValue(typed);
        suggested = !typed.isEmpty();
        addRenderableWidget(squad);
        addRenderableWidget(new FlatButton(colB, by + 82, w, 22, "Hire", new ItemStack(Items.GOLD_INGOT), 0x8A6D1F,
            "Pay the price; the recruit travels to you and joins the squad", () -> {
                String name = squad.getValue().trim();
                if (!selected.isEmpty() && !name.isEmpty()) send(RecruitPacket.Action.HIRE, selected, name);
            }));

        if (minecraft.player != null && minecraft.player.hasPermissions(2)) {
            String old = saveName == null ? "" : saveName.getValue();
            saveName = new EditBox(font, colB, by + 134, w - 78, 16, Component.literal("Recruit name"));
            saveName.setMaxLength(32);
            saveName.setFilter(s -> s.matches("[A-Za-z0-9_]{0,32}"));
            saveName.setHint(Component.literal("recruit name"));
            saveName.setValue(old);
            addRenderableWidget(saveName);
            typeButton = new FlatButton(colB + w - 74, by + 134, 74, 16, saveType.label, null, 0x4F6B8F,
                "Click to change the recruit type", () -> {
                    saveType = RecruitType.values()[(saveType.ordinal() + 1) % RecruitType.values().length];
                    typeButton.setMessage(Component.literal(saveType.label));
                });
            addRenderableWidget(typeButton);
            addRenderableWidget(new FlatButton(colB, by + 154, w / 2 - 2, 18, "Save NPC", null, 0x2F6B3F,
                "Save the NPC you were looking at as a recruit under this name", () -> {
                    String name = saveName.getValue().trim();
                    if (!name.isEmpty()) send(RecruitPacket.Action.SAVE, name, "");
                }));
            addRenderableWidget(new FlatButton(colB + w / 2 + 2, by + 154, w / 2 - 2, 18, "Delete", null, 0x8A2F2F,
                "Remove the selected recruit from the list", () -> {
                    if (!selected.isEmpty()) send(RecruitPacket.Action.DELETE, selected, "");
                }));
        }
        Net.CHANNEL.sendToServer(RecruitPacket.refresh());
    }

    private void send(RecruitPacket.Action action, String name, String squadName) {
        Net.CHANNEL.sendToServer(new RecruitPacket(action, 0, name, squadName, saveType));
    }

    private RecruitMenuPacket.Entry entry() {
        for (RecruitMenuPacket.Entry e : ClientRecruits.menu().entries()) if (e.name().equals(selected)) return e;
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        RecruitMenuPacket menu = ClientRecruits.menu();
        if (entry() == null) selected = menu.entries().isEmpty() ? "" : menu.entries().get(0).name();
        // Once the player's squads are known, fill in the first, or a name made from their own.
        if (!suggested && refreshTimer > 2 && minecraft.player != null) {
            suggested = true;
            if (squad.getValue().isEmpty()) {
                String n = minecraft.player.getGameProfile().getName().replaceAll("[^A-Za-z0-9_]", "");
                squad.setValue(!menu.squads().isEmpty() ? menu.squads().get(0)
                    : (n.length() > 26 ? n.substring(0, 26) : n) + "_squad");
            }
        }
        if (++refreshTimer % 20 == 0) Net.CHANNEL.sendToServer(RecruitPacket.refresh());
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
            List<RecruitMenuPacket.Entry> list = ClientRecruits.menu().entries();
            int i = scroll + (int) ((my - (by + 12)) / ROW_H);
            if (i < list.size()) selected = list.get(i).name();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (overList(mx, my)) {
            int max = Math.max(0, ClientRecruits.menu().entries().size() - ROWS);
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
        g.drawString(font, "Recruits", px + 8, py + 7, 0xFFFFFFFF);

        RecruitMenuPacket menu = ClientRecruits.menu();
        List<RecruitMenuPacket.Entry> list = menu.entries();
        g.drawString(font, "FOR HIRE", colA, by, LABEL);
        g.fill(colA, by + 10, colA + LIST_W, by + 14 + ROWS * ROW_H, WELL);
        g.renderOutline(colA, by + 10, LIST_W, ROWS * ROW_H + 4, BORDER);
        if (list.isEmpty()) g.drawString(font, "Nobody to hire yet", colA + 4, by + 16, 0xFF707070);
        for (int r = 0; r < ROWS && scroll + r < list.size(); r++) {
            RecruitMenuPacket.Entry e = list.get(scroll + r);
            int y = by + 12 + r * ROW_H;
            if (e.name().equals(selected)) g.fill(colA + 1, y - 1, colA + LIST_W - 1, y + ROW_H - 1, SELECTED);
            g.drawString(font, font.plainSubstrByWidth(e.name(), 100), colA + 4, y + 1, 0xFFFFFFFF);
            String info = e.type().label + " " + e.type().price + "g";
            g.drawString(font, info, colA + LIST_W - 5 - font.width(info), y + 1, LABEL);
        }

        RecruitMenuPacket.Entry e = entry();
        g.drawString(font, "HIRE", colB, by, LABEL);
        if (e != null) {
            boolean afford = minecraft.player.getAbilities().instabuild || menu.gold() >= e.type().price;
            g.drawString(font, e.type().label + " recruit", colB, by + 14, 0xFFFFFFFF);
            g.drawString(font, "Price: " + e.type().price + " gold ingots", colB, by + 28, 0xFFE0B04B);
            g.drawString(font, "You have " + menu.gold(), colB, by + 42, afford ? LABEL : 0xFFE05A4B);
        }
        g.drawString(font, "Squad", colB, by + 64, LABEL);
        if (menu.cooldown() > 0) {
            g.drawString(font, "Hire again in " + menu.cooldown() + " s", colB, by + 108, 0xFFE0B04B);
        }
        if (saveName != null) g.drawString(font, "ADD THE NPC YOU LOOK AT", colB, by + 122, LABEL);
        super.render(g, mx, my, pt);
    }
}
