package dev.storm.npcaddon.client;

import dev.storm.npcaddon.network.RecruitMenuPacket;

import java.util.List;

/** The latest recruit menu sent by the server. */
public final class ClientRecruits {
    private static volatile RecruitMenuPacket menu = new RecruitMenuPacket(List.of(), 0, List.of(), 0);

    private ClientRecruits() {}

    public static void set(RecruitMenuPacket p) {
        menu = p;
    }

    static RecruitMenuPacket menu() {
        return menu;
    }
}
