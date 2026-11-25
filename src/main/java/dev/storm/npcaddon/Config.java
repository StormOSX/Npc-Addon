package dev.storm.npcaddon;

import net.minecraftforge.common.ForgeConfigSpec;

/** config/npcaddon-common.toml */
public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue UNLIMITED_SIEGE_AMMO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("siege");
        UNLIMITED_SIEGE_AMMO = b
            .comment("NPC crews of Ancient Warfare siege weapons never run out of ammo.",
                "When false, the weapon only fires ammo loaded into it, and uses it up.")
            .define("unlimitedAmmo", true);
        b.pop();
        SPEC = b.build();
    }

    private Config() {}
}
