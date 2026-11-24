package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.HudPacket;

import java.util.List;

/** Client-side state of the squad HUD: what the server last sent, and whether the player switched it on. */
public final class ClientHud {
    public static boolean enabled = true;
    private static HudPacket data = HudPacket.empty();

    private ClientHud() {}

    public static void set(HudPacket p) {
        data = p;
    }

    public static boolean hasSquad() {
        return !data.squad().isEmpty();
    }

    public static String squad() {
        return data.squad();
    }

    public static String stance() {
        return data.stance();
    }

    public static List<HudPacket.Member> members() {
        return data.members();
    }
}
