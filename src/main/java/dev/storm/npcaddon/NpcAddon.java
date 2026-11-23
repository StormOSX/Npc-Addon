package dev.storm.npcaddon;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.api.NpcAPI;
import org.slf4j.Logger;

@Mod(NpcAddon.MODID)
public class NpcAddon {
    public static final String MODID = "npcaddon";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NpcAddon() {
        dev.storm.npcaddon.network.Net.init();
        LOGGER.info("NPC Command Addon loaded; CustomNPCs API present: {}", NpcAPI.Instance() != null);
    }
}
