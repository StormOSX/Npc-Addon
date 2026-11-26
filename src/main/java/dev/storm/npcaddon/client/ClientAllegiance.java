package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.AllegiancePacket;

import java.util.List;

/** The latest faction membership list sent by the server. */
public final class ClientAllegiance {
    private static volatile AllegiancePacket data = new AllegiancePacket(List.of(), -1);

    private ClientAllegiance() {}

    public static void set(AllegiancePacket p) {
        data = p;
    }

    static AllegiancePacket get() {
        return data;
    }
}
