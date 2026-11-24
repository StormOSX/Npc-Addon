package dev.storm.npcaddon.group;

import java.util.Locale;

/** How a squad behaves on its own. Explicit attack orders override every stance. */
public enum Stance {
    /** Wider aggro range: notices and chases enemies from further away. */
    AGGRESSIVE("aggressive", "Aggressive"),
    /** The NPCs' own CustomNPCs settings, untouched. */
    DEFENSIVE("defensive", "Defensive"),
    /** Never starts a fight; fights back only against whoever just hurt it. */
    HOLD_FIRE("hold", "Hold fire"),
    /** Never fights, not even when hit. */
    PASSIVE("passive", "Passive");

    public final String key;
    public final String label;

    Stance(String key, String label) {
        this.key = key;
        this.label = label;
    }

    public Stance next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Parses a command/packet key; null when unknown. */
    public static Stance parse(String text) {
        if (text == null) return null;
        String t = text.toLowerCase(Locale.ROOT);
        for (Stance s : values()) if (s.key.equals(t)) return s;
        return t.equals("hold_fire") || t.equals("holdfire") ? HOLD_FIRE : null;
    }
}
