package dev.storm.npcaddon.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    public static final KeyMapping OPEN_GUI = new KeyMapping("key.npcaddon.open", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_G, "key.categories.npcaddon");

    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = "npcaddon", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_GUI);
        }
    }

    @Mod.EventBusSubscriber(modid = "npcaddon", value = Dist.CLIENT)
    public static final class ForgeBus {
        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (OPEN_GUI.consumeClick()) {
                if (mc.screen == null && mc.player != null) mc.setScreen(new SquadScreen());
            }
        }
    }
}
