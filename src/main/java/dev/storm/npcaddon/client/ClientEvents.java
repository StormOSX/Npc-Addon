package dev.storm.npcaddon.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.storm.npcaddon.item.WandItem;
import dev.storm.npcaddon.network.Net;
import dev.storm.npcaddon.network.WandPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.InputEvent;
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

    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.npcaddon.hud", InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_H, "key.categories.npcaddon");

    private ClientEvents() {}

    @Mod.EventBusSubscriber(modid = "npcaddon", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(OPEN_GUI);
            e.register(TOGGLE_HUD);
        }

        @SubscribeEvent
        public static void overlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent e) {
            e.registerAboveAll("squad_hud", HudOverlay::render);
        }
    }

    @Mod.EventBusSubscriber(modid = "npcaddon", value = Dist.CLIENT)
    public static final class ForgeBus {
        /** Wand clicks are sent to the server (which does its own long-range pick) instead of acting locally. */
        @SubscribeEvent
        public static void wandClick(InputEvent.InteractionKeyMappingTriggered e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || e.getHand() != InteractionHand.MAIN_HAND) return;
            if (!(mc.player.getMainHandItem().getItem() instanceof WandItem)) return;
            if (!e.isAttack() && !e.isUseItem()) return;
            e.setCanceled(true);
            e.setSwingHand(true);
            Net.CHANNEL.sendToServer(new WandPacket(e.isUseItem(), net.minecraft.client.gui.screens.Screen.hasControlDown(),
                net.minecraft.client.gui.screens.Screen.hasAltDown()));
        }

        @SubscribeEvent
        public static void tick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            while (TOGGLE_HUD.consumeClick()) {
                ClientHud.enabled = !ClientHud.enabled;
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Squad HUD " + (ClientHud.enabled ? "on" : "off")), true);
                }
            }
            while (OPEN_GUI.consumeClick()) {
                if (mc.screen == null && mc.player != null) mc.setScreen(new SquadScreen());
            }
        }
    }
}
