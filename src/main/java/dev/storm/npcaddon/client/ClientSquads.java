package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.FactionListPacket;
import dev.storm.npcaddon.network.SquadListPacket;

import java.util.List;

/** Latest squad, faction and player lists sent by the server; only read by the addon screens. */
public final class ClientSquads {
    private static volatile List<SquadListPacket.Entry> entries = List.of();
    private static volatile List<FactionListPacket.Info> factions = List.of();
    private static volatile List<String> players = List.of();

    private ClientSquads() {}

    public static void set(List<SquadListPacket.Entry> list) {
        entries = List.copyOf(list);
    }

    public static List<SquadListPacket.Entry> get() {
        return entries;
    }

    public static void setFactions(List<FactionListPacket.Info> list, List<String> playerNames) {
        factions = List.copyOf(list);
        players = List.copyOf(playerNames);
    }

    public static List<FactionListPacket.Info> factions() {
        return factions;
    }

    public static List<String> players() {
        return players;
    }
}
