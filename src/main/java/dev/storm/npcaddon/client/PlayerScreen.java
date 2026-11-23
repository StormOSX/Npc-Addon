package dev.storm.npcaddon.client;

import dev.storm.npcaddon.faction.Standing;
import dev.storm.npcaddon.network.FactionListPacket;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.PlayerStandingPacket;
import dev.storm.npcaddon.network.PlayerStandingPacket.Mode;
import dev.storm.npcaddon.network.SquadOrderPacket;
import dev.storm.npcaddon.network.SquadOrderPacket.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/** How a faction's NPCs treat players: per player, or the starting standing for new players. */
public class PlayerScreen extends Screen {
    private static final int PW = 420;
    private static final int PH = 220;
    private static final int ROWS = 9;
    private static final int ROW_H = 14;
    private static final int PLAYER_W = 120;
    private static final int FACTION_W = 150;

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
    private String selPlayer = "";
    private int selFaction = -1;
    private int scrollP;
    private int scrollF;
    private int refreshTimer;
    private boolean applyOnline;
    private FlatButton applyButton;

    public PlayerScreen() {
        super(Component.literal("NPC Players"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        by = py + 30;
        colA = px + 8;
        colB = colA + PLAYER_W + 8;
        colC = colB + FACTION_W + 8;
        int w = PW - (colC - px) - 8;

        Tabs.build(minecraft, px, py, PW, Tabs.PLAYERS, this::addRenderableWidget);

        addRenderableWidget(new FlatButton(colC, by + 12, w, 22, "Hostile", new ItemStack(Items.IRON_SWORD), 0xB03A2E,
            "This faction's NPCs attack the selected player", () -> player(Standing.HOSTILE)));
        addRenderableWidget(new FlatButton(colC, by + 38, w, 22, "Neutral", new ItemStack(Items.PAPER), 0x4A5568,
            "This faction's NPCs ignore the selected player", () -> player(Standing.NEUTRAL)));
        addRenderableWidget(new FlatButton(colC, by + 64, w, 22, "Friendly", new ItemStack(Items.EMERALD), 0x3F8F3F,
            "This faction's NPCs are friendly to the selected player", () -> player(Standing.FRIENDLY)));

        addRenderableWidget(new FlatButton(colC, by + 106, w, 16, "Default: Hostile", null, 0x8A2F2F,
            "New players start hostile to this faction", () -> defaults(Standing.HOSTILE)));
        addRenderableWidget(new FlatButton(colC, by + 124, w, 16, "Default: Neutral", null, 0x3A4556,
            "New players start neutral to this faction", () -> defaults(Standing.NEUTRAL)));
        addRenderableWidget(new FlatButton(colC, by + 142, w, 16, "Default: Friendly", null, 0x2F6B3F,
            "New players start friendly to this faction", () -> defaults(Standing.FRIENDLY)));
        applyButton = new FlatButton(colC, by + 162, w, 16, applyLabel(), null, 0x3A4556,
            "Also change players who are online now. Players with saved points keep them otherwise.", () -> {
                applyOnline = !applyOnline;
                applyButton.setMessage(Component.literal(applyLabel()));
                applyButton.active(applyOnline);
            });
        addRenderableWidget(applyButton);

        refresh();
    }

    private String applyLabel() {
        return "Apply to online: " + (applyOnline ? "ON" : "OFF");
    }

    private void refresh() {
        Net.CHANNEL.sendToServer(new SquadOrderPacket(Action.FACTIONS, "", 0, ""));
    }

    private void player(Standing s) {
        if (selPlayer.isEmpty() || selFaction < 0) return;
        Net.CHANNEL.sendToServer(new PlayerStandingPacket(Mode.PLAYER, selPlayer, selFaction, s));
    }

    private void defaults(Standing s) {
        if (selFaction < 0) return;
        Net.CHANNEL.sendToServer(new PlayerStandingPacket(
            applyOnline ? Mode.DEFAULT_AND_APPLY : Mode.DEFAULT, "", selFaction, s));
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTimer >= 100) {
            refreshTimer = 0;
            refresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean overList(double mx, double my, int x, int w) {
        return mx >= x && mx < x + w && my >= by + 12 && my < by + 12 + ROWS * ROW_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overList(mx, my, colA, PLAYER_W)) {
            List<String> players = ClientSquads.players();
            int i = scrollP + (int) ((my - (by + 12)) / ROW_H);
            if (i < players.size()) selPlayer = players.get(i);
            return true;
        }
        if (overList(mx, my, colB, FACTION_W)) {
            List<FactionListPacket.Info> list = ClientSquads.factions();
            int i = scrollF + (int) ((my - (by + 12)) / ROW_H);
            if (i < list.size()) selFaction = list.get(i).id();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) -Math.signum(delta);
        if (overList(mx, my, colA, PLAYER_W)) {
            int max = Math.max(0, ClientSquads.players().size() - ROWS);
            scrollP = Math.max(0, Math.min(max, scrollP + step));
            return true;
        }
        if (overList(mx, my, colB, FACTION_W)) {
            int max = Math.max(0, ClientSquads.factions().size() - ROWS);
            scrollF = Math.max(0, Math.min(max, scrollF + step));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    private void well(GuiGraphics g, int x, int w) {
        g.fill(x, by + 10, x + w, by + 14 + ROWS * ROW_H, WELL);
        g.renderOutline(x, by + 10, w, ROWS * ROW_H + 4, BORDER);
    }

    private String factionName(int id) {
        for (FactionListPacket.Info f : ClientSquads.factions()) if (f.id() == id) return f.name() + " (" + id + ")";
        return id < 0 ? "none" : "#" + id;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + PW, py + PH, PANEL);
        g.renderOutline(px, py, PW, PH, BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 22, HEADER);
        g.drawString(font, "NPC Players", px + 8, py + 7, 0xFFFFFFFF);

        // Players
        g.drawString(font, "PLAYER", colA, by, LABEL);
        well(g, colA, PLAYER_W);
        List<String> players = ClientSquads.players();
        if (players.isEmpty()) g.drawString(font, "Nobody online", colA + 4, by + 16, 0xFF707070);
        for (int r = 0; r < ROWS && scrollP + r < players.size(); r++) {
            String n = players.get(scrollP + r);
            int y = by + 12 + r * ROW_H;
            if (n.equals(selPlayer)) g.fill(colA + 1, y - 1, colA + PLAYER_W - 1, y + ROW_H - 1, SELECTED);
            g.drawString(font, font.plainSubstrByWidth(n, PLAYER_W - 8), colA + 4, y + 1, 0xFFFFFFFF);
        }

        // Factions
        g.drawString(font, "FACTION", colB, by, LABEL);
        well(g, colB, FACTION_W);
        List<FactionListPacket.Info> list = ClientSquads.factions();
        if (list.isEmpty()) g.drawString(font, "No factions found", colB + 4, by + 16, 0xFF707070);
        for (int r = 0; r < ROWS && scrollF + r < list.size(); r++) {
            FactionListPacket.Info f = list.get(scrollF + r);
            int y = by + 12 + r * ROW_H;
            if (f.id() == selFaction) g.fill(colB + 1, y - 1, colB + FACTION_W - 1, y + ROW_H - 1, SELECTED);
            g.fill(colB + 4, y + 1, colB + 10, y + 9, 0xFF000000 | (f.color() & 0xFFFFFF));
            g.drawString(font, font.plainSubstrByWidth(f.name(), FACTION_W - 40), colB + 14, y + 1, 0xFFFFFFFF);
            String id = String.valueOf(f.id());
            g.drawString(font, id, colB + FACTION_W - 4 - font.width(id), y + 1, 0xFF8B98A8);
        }

        g.drawString(font, "Player: " + (selPlayer.isEmpty() ? "none" : selPlayer), colA, by + 148, 0xFFFFFFFF);
        g.drawString(font, font.plainSubstrByWidth("Faction: " + factionName(selFaction), 270), colA, by + 160, 0xFFFFFFFF);

        g.drawString(font, "PLAYER STANDING", colC, by, LABEL);
        g.drawString(font, "FACTION DEFAULT", colC, by + 94, LABEL);
        super.render(g, mx, my, pt);
    }
}
