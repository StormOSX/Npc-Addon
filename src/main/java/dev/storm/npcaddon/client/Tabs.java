package dev.storm.npcaddon.client;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

/** The tab bar shared by the addon screens. */
final class Tabs {
    static final int SQUADS = 0;
    static final int FACTIONS = 1;
    static final int PLAYERS = 2;

    private Tabs() {}

    static void build(Minecraft mc, int px, int py, int panelWidth, int active, Consumer<FlatButton> add) {
        String[] names = {"Squads", "Factions", "Players"};
        int w = 62;
        int start = px + panelWidth - 8 - (3 * w + 2 * 4);
        for (int i = 0; i < 3; i++) {
            int target = i;
            FlatButton b = new FlatButton(start + i * (w + 4), py + 3, w, 16, names[i], null,
                i == active ? 0x3B82F6 : 0x2F3B4D, null, () -> {
                    if (target == active) return;
                    mc.setScreen(switch (target) {
                        case SQUADS -> new SquadScreen();
                        case FACTIONS -> new AddonScreen();
                        default -> new PlayerScreen();
                    });
                }).active(i == active);
            add.accept(b);
        }
    }
}
