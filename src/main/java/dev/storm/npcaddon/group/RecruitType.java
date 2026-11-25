package dev.storm.npcaddon.group;

import java.util.Locale;

/** What kind of recruit an NPC is: its price in gold ingots, and whether it may ride or crew siege weapons. */
public enum RecruitType {
    MELEE("Melee", 2),
    RANGED("Ranged", 5),
    MOUNTED("Mounted", 7),
    ENGINEER("Engineer", 8);

    public final String label;
    public final int price;

    RecruitType(String label, int price) {
        this.label = label;
        this.price = price;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RecruitType parse(String s) {
        for (RecruitType t : values()) if (t.key().equalsIgnoreCase(s)) return t;
        return null;
    }
}
