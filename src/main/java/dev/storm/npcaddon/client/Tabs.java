package dev.storm.npcaddon.client;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/** The tab bar shared by the addon screens. */
final class Tabs {
    static final int SQUADS = 0;
    static final int FACTIONS = 1;
    static final int PLAYERS = 2;
    static final int RECRUITS = 3;
    static final int JOIN = 4;

    /** Height of the tallest screen. All screens share its top edge, so the tabs stay put when switching. */
    private static final int TALLEST = 242;

    private Tabs() {}

    static int top(int screenHeight) {
        return (screenHeight - TALLEST) / 2;
    }

    static void build(Minecraft mc, int px, int py, int panelWidth, int active, Consumer<FlatButton> add) {
        String[] names = {"Squads", "Factions", "Players", "Recruit", "Join"};
        // Faction and player settings are for operators.
        boolean operator = mc.player != null && mc.player.hasPermissions(2);
        int[] shown = operator ? new int[]{SQUADS, RECRUITS, JOIN, FACTIONS, PLAYERS}
            : new int[]{SQUADS, RECRUITS, JOIN};
        int w = 60;
        int start = px + panelWidth - 8 - (shown.length * w + (shown.length - 1) * 4);
        for (int i = 0; i < shown.length; i++) {
            int target = shown[i];
            FlatButton b = new FlatButton(start + i * (w + 4), py + 3, w, 16, names[target], null,
                target == active ? 0x3B82F6 : 0x2F3B4D, null, () -> {
                    if (target == active) return;
                    mc.setScreen(switch (target) {
                        case SQUADS -> new SquadScreen();
                        case FACTIONS -> new AddonScreen();
                        case RECRUITS -> new HireScreen();
                        case JOIN -> new JoinScreen();
                        default -> new PlayerScreen();
                    });
                }).active(target == active);
            add.accept(b);
        }
    }
}
