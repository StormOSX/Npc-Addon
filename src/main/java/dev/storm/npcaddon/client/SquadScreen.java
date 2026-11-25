package dev.storm.npcaddon.client;

import dev.storm.npcaddon.aw.AwCompat;
import dev.storm.npcaddon.group.Formation;
import dev.storm.npcaddon.group.Stance;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.SquadListPacket;
import dev.storm.npcaddon.network.SquadOrderPacket;
import dev.storm.npcaddon.network.SquadOrderPacket.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;

/** Squad management and orders. Everything goes to the server as one SquadOrderPacket. */
public class SquadScreen extends Screen {
    private static final int PW = 420;
    private static final int PH = 242;
    /** Retreat thresholds the button cycles through, in percent of health; 0 = off. */
    private static final int[] RETREAT_STEPS = {0, 10, 25, 50};
    private static final int LIST_ROWS = 5;
    private static final int LIST_ROW_H = 13;
    private static final int MEMBER_ROWS = 6;
    private static final int MEMBER_ROW_H = 26;

    private static final int PANEL = 0xF0141922;
    private static final int HEADER = 0xFF1F2733;
    private static final int BORDER = 0xFF3A4556;
    private static final int WELL = 0xFF0E1218;
    private static final int LABEL = 0xFF8B98A8;
    private static final int SELECTED = 0xFF2B4A73;

    private EditBox name;
    private EditBox radius;
    private EditBox patrolRadius;
    private EditBox spacing;
    private EditBox facing;
    private FlatButton formButton;
    private FlatButton stanceButton;
    private FlatButton retreatButton;
    private int formIndex;
    private int listScroll;
    private int memberScroll;
    private int refreshTimer;
    private int px;
    private int py;
    private int colA;
    private int colB;
    private int colC;
    private int by;
    private boolean pickSquad;

    public SquadScreen() {
        super(Component.literal("NPC Squads"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = Tabs.top(height);
        colA = px + 8;
        colB = px + 124;
        colC = px + 282;
        by = py + 30;

        // Tabs
        Tabs.build(minecraft, px, py, PW, Tabs.SQUADS, this::addRenderableWidget);

        // Column A: squad name, list, setup
        String typed = name == null ? "" : name.getValue();
        name = new EditBox(font, colA, by + 10, 108, 16, Component.literal("Squad name"));
        name.setMaxLength(32);
        name.setFilter(s -> s.matches("[A-Za-z0-9_]{0,32}"));
        name.setHint(Component.literal("new or existing"));
        name.setValue(typed);
        pickSquad = typed.isEmpty();
        addRenderableWidget(name);

        add(new FlatButton(colA, by + 102, 108, 16, "Delete squad", null, 0x8A2F2F,
            "Delete the squad named above (the NPCs are not harmed)", () -> order(Action.DELETE, 0, "")));
        boolean operator = minecraft.player != null && minecraft.player.hasPermissions(2);
        radius = new EditBox(font, colA, by + 134, 28, 16, Component.literal("Radius"));
        radius.setValue("10");
        radius.setFilter(s -> s.matches("\\d{0,2}"));
        if (operator) addRenderableWidget(radius);
        if (operator) add(new FlatButton(colA + 30, by + 134, 36, 16, "Add", null, 0x2F6B3F,
            "Add every NPC within the radius to this squad (creates it if new)", () -> order(Action.ADD, radius(), "")));
        if (operator) add(new FlatButton(colA + 68, by + 134, 40, 16, "Remove", null, 0x6B4F2F,
            "Remove NPCs within the radius from this squad", () -> order(Action.REMOVE, radius(), "")));

        // Column B: orders
        int x = colB;
        add(new FlatButton(x, by + 10, 72, 22, "Follow", new ItemStack(Items.LEATHER_BOOTS), 0x3F8F3F,
            "Follow you until stopped; face your way, or the facing below", () -> order(Action.FOLLOW, 0, facing())));
        add(new FlatButton(x + 78, by + 10, 72, 22, "Defend", new ItemStack(Items.IRON_CHESTPLATE), 0x2F5F9F,
            "Hold your current position, facing the direction below", () -> order(Action.DEFEND, 0, facing())));
        add(new FlatButton(x, by + 36, 72, 22, "Attack", new ItemStack(Items.IRON_SWORD), 0xB03A2E,
            "Attack whatever you are aiming at", () -> order(Action.ATTACK, 0, "")));
        add(new FlatButton(x + 78, by + 36, 72, 22, "Patrol", new ItemStack(Items.COMPASS), 0xB7791F,
            "Patrol a square around you", () -> order(Action.PATROL, parse(patrolRadius.getValue(), 8), "")));
        add(new FlatButton(x, by + 62, 72, 22, "Stop", new ItemStack(Items.BARRIER), 0xA03030,
            "Cancel every order; stand where you are", () -> order(Action.STOP, 0, "")));
        add(new FlatButton(x + 78, by + 62, 72, 22, "Area", new ItemStack(Items.BOW), 0x9F3A2E,
            "Attack everything hostile within the radius below, around where you aim; hold it afterwards",
            () -> order(Action.AREA, parse(patrolRadius.getValue(), 10), "")));

        patrolRadius = new EditBox(font, x + 110, by + 90, 40, 16, Component.literal("Patrol radius"));
        patrolRadius.setValue("8");
        patrolRadius.setFilter(s -> s.matches("\\d{0,2}"));
        addRenderableWidget(patrolRadius);

        formButton = add(new FlatButton(x, by + 122, 72, 22, formationLabel(), new ItemStack(Items.ARMOR_STAND), 0x6B4FA0,
            "Click to change the formation", () -> {
                formIndex = (formIndex + 1) % Formation.values().length;
                formButton.setMessage(Component.literal(formationLabel()));
            }));
        add(new FlatButton(x + 78, by + 122, 72, 22, "Form up", null, 0x7E5BBF,
            "Form up in front of you, facing the direction below", () -> order(Action.FORM, parse(spacing.getValue(), 2),
                (Formation.values()[formIndex].name().toLowerCase(Locale.ROOT) + " " + facing()).trim())));
        spacing = new EditBox(font, x + 110, by + 150, 40, 16, Component.literal("Spacing"));
        spacing.setValue("2");
        spacing.setFilter(s -> s.matches("[0-9.]{0,3}"));
        addRenderableWidget(spacing);

        facing = new EditBox(font, x + 110, by + 170, 40, 16, Component.literal("Facing"));
        facing.setMaxLength(3);
        facing.setFilter(s -> s.matches("[0-9A-Za-z]{0,3}"));
        facing.setHint(Component.literal("mine"));
        addRenderableWidget(facing);

        if (AwCompat.loaded()) {
            add(new FlatButton(x, by + 190, 72, 18, "Man", null, 0x7A5C3A,
                "Free squad members crew the free siege weapons near you; also ends a bombardment",
                () -> order(Action.MAN, 0, "")));
            add(new FlatButton(x + 78, by + 190, 72, 18, "Bombard", null, 0x9F5A2E,
                "Crews shoot around where you aim, within the patrol/area radius (max 32)",
                () -> order(Action.BOMBARD, parse(patrolRadius.getValue(), 8), "")));
        }

        // Column C, under the member list: the selected squad's stance, click to cycle
        stanceButton = add(new FlatButton(colC, by + 172, 130, 16, "Stance", null, 0x4F6B8F,
            "Cycle the squad's stance: aggressive, defensive, hold fire, passive", () -> {
                SquadListPacket.Entry e = selectedEntry();
                Stance cur = e == null ? null : Stance.parse(e.stance());
                if (cur != null) order(Action.STANCE, 0, cur.next().key);
            }));

        retreatButton = add(new FlatButton(colC, by + 190, 130, 16, "Retreat", null, 0x6B5A3A,
            "Health at which members flee until they recover: off, 10%, 25% or 50%", () -> {
                SquadListPacket.Entry e = selectedEntry();
                if (e == null) return;
                int next = RETREAT_STEPS[0];
                for (int step : RETREAT_STEPS) {
                    if (step > e.retreat()) {
                        next = step;
                        break;
                    }
                }
                order(Action.RETREAT, next, "");
            }));
        add(new FlatButton(colA, by + 154, 108, 16, "Rally", null, 0x3F7F8F,
            "Call retreating members back to you", () -> order(Action.RALLY, 0, "")));

        add(new FlatButton(colA, by + 174, 52, 16, "Mount", null, 0x6B5530,
            "Members mount the free horses (and CustomNPCs with mount control) near you",
            () -> order(Action.MOUNT, 0, "")));
        add(new FlatButton(colA + 54, by + 174, 54, 16, "Dismount", null, 0x4A3F30,
            "Riders get off their mounts; siege crews leave their weapons", () -> order(Action.DISMOUNT, 0, "")));

        refresh();
    }

    private FlatButton add(FlatButton b) {
        addRenderableWidget(b);
        return b;
    }

    private void refresh() {
        Net.CHANNEL.sendToServer(new SquadOrderPacket(Action.REFRESH, "", 0, ""));
    }

    private String formationLabel() {
        String n = Formation.values()[formIndex].name();
        return n.charAt(0) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Compass facing typed by the player (degrees, 0 = north, or n/ne/e/...), empty = the way they face. */
    private String facing() {
        return facing.getValue().trim();
    }

    private double radius() {
        return parse(radius.getValue(), 10);
    }

    private static double parse(String s, double fallback) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private void order(Action action, double value, String text) {
        String g = name.getValue().trim();
        if (g.isEmpty()) return;
        Net.CHANNEL.sendToServer(new SquadOrderPacket(action, g, value, text));
    }

    /**
     * Once the squad list is there, selects a squad so the screen is ready to use: the one shown on the HUD, else the
     * first. Does nothing if the player already typed or picked a name.
     */
    private void pickFirstSquad() {
        if (!pickSquad) return;
        List<SquadListPacket.Entry> list = ClientSquads.get();
        if (!name.getValue().isEmpty()) {
            pickSquad = false;
            return;
        }
        if (list.isEmpty()) return;
        int index = 0;
        for (int i = 0; i < list.size(); i++) if (list.get(i).name().equals(ClientHud.squad())) index = i;
        name.setValue(list.get(index).name());
        listScroll = Math.max(0, Math.min(index, list.size() - LIST_ROWS));
        pickSquad = false;
    }

    @Override
    public void tick() {
        super.tick();
        pickFirstSquad();
        if (++refreshTimer >= 20) {
            refreshTimer = 0;
            refresh();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private SquadListPacket.Entry selectedEntry() {
        String sel = name.getValue();
        for (SquadListPacket.Entry e : ClientSquads.get()) if (e.name().equals(sel)) return e;
        return null;
    }

    private boolean overList(double mx, double my) {
        return mx >= colA && mx < colA + 108 && my >= by + 30 && my < by + 30 + LIST_ROWS * LIST_ROW_H;
    }

    private boolean overMembers(double mx, double my) {
        return mx >= colC && mx < colC + 130 && my >= by + 10 && my < by + 10 + MEMBER_ROWS * MEMBER_ROW_H;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (overList(mx, my)) {
            List<SquadListPacket.Entry> list = ClientSquads.get();
            int i = listScroll + (int) ((my - (by + 30)) / LIST_ROW_H);
            if (i < list.size()) {
                name.setValue(list.get(i).name());
                memberScroll = 0;
                order(Action.SELECT, 0, ""); // the HUD follows the squad picked here
            }
            return true;
        }
        if (overMembers(mx, my)) {
            SquadListPacket.Entry e = selectedEntry();
            int i = memberScroll + (int) Math.floor((my - (by + 12)) / MEMBER_ROW_H);
            if (e != null && i >= 0 && i < e.members().size()) order(Action.MEDIC, i, "");
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) -Math.signum(delta);
        if (overList(mx, my)) {
            int max = Math.max(0, ClientSquads.get().size() - LIST_ROWS);
            listScroll = Math.max(0, Math.min(max, listScroll + step));
            return true;
        }
        if (overMembers(mx, my)) {
            SquadListPacket.Entry e = selectedEntry();
            int max = e == null ? 0 : Math.max(0, e.members().size() - MEMBER_ROWS);
            memberScroll = Math.max(0, Math.min(max, memberScroll + step));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    static int stateColor(String state) {
        return switch (state) {
            case "Attacking" -> 0xFFE05A4B;
            case "Area" -> 0xFFE08A4B;
            case "Bombard" -> 0xFFE06A2B;
            case "Retreat" -> 0xFFD070D0;
            case "Healing" -> 0xFF7CFC9A;
            case "Crewing", "To weapon", "Driving", "To mount", "Mounted" -> 0xFFD0A878;
            case "Ramming" -> 0xFFE05A4B;
            case "Following" -> 0xFF6FCF6F;
            case "Defending", "Holding" -> 0xFF6FA8F0;
            case "Patrolling" -> 0xFFE0B04B;
            case "Moving" -> 0xFF8FD6C8;
            case "Unloaded" -> 0xFF707070;
            default -> 0xFFB0B8C4;
        };
    }

    private void panel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL);
        g.renderOutline(x, y, w, h, BORDER);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        g.fill(px, py, px + PW, py + PH, PANEL);
        g.renderOutline(px, py, PW, PH, BORDER);
        g.fill(px + 1, py + 1, px + PW - 1, py + 22, HEADER);
        g.drawString(font, "NPC Squads", px + 8, py + 7, 0xFFFFFFFF);

        // Column A
        g.drawString(font, "SQUAD", colA, by, LABEL);
        panel(g, colA, by + 28, 108, LIST_ROWS * LIST_ROW_H + 4);
        List<SquadListPacket.Entry> list = ClientSquads.get();
        String selected = name.getValue();
        if (list.isEmpty()) g.drawString(font, "No squads yet", colA + 4, by + 34, 0xFF707070);
        for (int r = 0; r < LIST_ROWS && listScroll + r < list.size(); r++) {
            SquadListPacket.Entry e = list.get(listScroll + r);
            int y = by + 30 + r * LIST_ROW_H;
            if (e.name().equals(selected)) g.fill(colA + 1, y - 1, colA + 107, y + LIST_ROW_H - 1, SELECTED);
            String label = font.plainSubstrByWidth(e.name(), 70);
            g.drawString(font, label, colA + 4, y + 1, 0xFFFFFFFF);
            String count = e.loaded() + "/" + e.total();
            g.drawString(font, count, colA + 104 - font.width(count), y + 1, e.loaded() < e.total() ? 0xFFE0B04B : 0xFF8B98A8);
        }
        if (list.size() > LIST_ROWS) {
            int trackH = LIST_ROWS * LIST_ROW_H;
            int barH = Math.max(8, trackH * LIST_ROWS / list.size());
            int barY = by + 30 + (trackH - barH) * listScroll / (list.size() - LIST_ROWS);
            g.fill(colA + 105, barY, colA + 107, barY + barH, 0xFF5A6678);
        }
        if (minecraft.player != null && minecraft.player.hasPermissions(2)) {
            g.drawString(font, "NEARBY RADIUS", colA, by + 123, LABEL);
        } else if (ClientSquads.get().isEmpty()) {
            g.drawString(font, "Hire in the", colA, by + 123, LABEL);
            g.drawString(font, "Recruit tab", colA, by + 135, LABEL);
        }

        // Column B
        g.drawString(font, "ORDERS", colB, by, LABEL);
        g.drawString(font, "Patrol/area radius", colB, by + 94, LABEL);
        g.drawString(font, "FORMATION", colB, by + 112, LABEL);
        g.drawString(font, "Spacing", colB, by + 154, LABEL);
        g.drawString(font, "Facing (0=N)", colB, by + 174, LABEL);

        // Column C
        SquadListPacket.Entry sel = selectedEntry();
        g.drawString(font, sel == null ? "MEMBERS" : "MEMBERS (" + sel.loaded() + "/" + sel.total() + ")", colC, by, LABEL);
        panel(g, colC, by + 10, 130, MEMBER_ROWS * MEMBER_ROW_H + 2);
        if (sel == null) {
            g.drawString(font, "Select a squad", colC + 6, by + 18, 0xFF707070);
        } else {
            List<SquadListPacket.Member> members = sel.members();
            for (int r = 0; r < MEMBER_ROWS && memberScroll + r < members.size(); r++) {
                SquadListPacket.Member m = members.get(memberScroll + r);
                int y = by + 12 + r * MEMBER_ROW_H;
                String label = (m.medic() ? "+ " : "") + m.name();
                g.drawString(font, font.plainSubstrByWidth(label, 70), colC + 4, y,
                    m.medic() ? 0xFF7CFC9A : 0xFFFFFFFF);
                String state = m.state();
                g.drawString(font, state, colC + 126 - font.width(state), y, stateColor(state));
                int barX = colC + 4;
                int barW = 90;
                int barY = y + 12;
                g.fill(barX, barY, barX + barW, barY + 6, 0xFF2A0F0F);
                if (m.maxHealth() > 0) {
                    float frac = Math.max(0, Math.min(1, m.health() / m.maxHealth()));
                    int color = frac > 0.5f ? 0xFF4CAF50 : frac > 0.25f ? 0xFFE0B04B : 0xFFE05A4B;
                    g.fill(barX, barY, barX + (int) (barW * frac), barY + 6, color);
                    String hp = Math.round(m.health()) + "/" + Math.round(m.maxHealth());
                    g.drawString(font, hp, barX + barW + 4, barY - 1, 0xFF8B98A8);
                }
            }
            if (members.size() > MEMBER_ROWS) {
                int trackH = MEMBER_ROWS * MEMBER_ROW_H;
                int barH = Math.max(8, trackH * MEMBER_ROWS / members.size());
                int barY = by + 11 + (trackH - barH) * memberScroll / (members.size() - MEMBER_ROWS);
                g.fill(colC + 127, barY, colC + 129, barY + barH, 0xFF5A6678);
            }
        }
        Stance st = sel == null ? null : Stance.parse(sel.stance());
        stanceButton.setMessage(Component.literal(st == null ? "Stance" : "Stance: " + st.label));
        retreatButton.setMessage(Component.literal(sel == null ? "Retreat"
            : sel.retreat() == 0 ? "Retreat: off" : "Retreat: at " + sel.retreat() + "%"));
        super.render(g, mx, my, pt);
        if (sel != null && !sel.members().isEmpty() && overMembers(mx, my)) {
            g.renderTooltip(font, Component.literal("Click a member to make it a medic (+) or not"), mx, my);
        }
    }
}
