package dev.storm.npcaddon.aw;

import net.minecraftforge.fml.ModList;

/**
 * Ancient Warfare 2 support is optional. Nothing outside {@link AwBridge} may touch an AW class, and AwBridge must only
 * be used after checking {@link #loaded()}, so the addon still loads when AW is not installed.
 */
public final class AwCompat {
    private static Boolean loaded;

    private AwCompat() {}

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("ancientwarfare");
        return loaded;
    }
}
