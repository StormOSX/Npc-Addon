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
        net.minecraftforge.fml.ModLoadingContext.get().registerConfig(
            net.minecraftforge.fml.config.ModConfig.Type.COMMON, Config.SPEC);
        dev.storm.npcaddon.network.Net.init();
        dev.storm.npcaddon.territory.ModBlocks.init(
            net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        dev.storm.npcaddon.item.ModItems.init(
            net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext.get().getModEventBus());
        LOGGER.info("NPC Command Addon loaded; CustomNPCs API present: {}", NpcAPI.Instance() != null);
    }
}
