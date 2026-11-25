package dev.storm.npcaddon.group;

import dev.storm.npcaddon.network.HudPacket;
import dev.storm.npcaddon.network.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Sends each player their selected squad twice a second for the HUD, and one empty packet when it goes away. */
@Mod.EventBusSubscriber(modid = "npcaddon")
public final class HudSync {
    private static final Set<UUID> ACTIVE = new HashSet<>();
    private static int clock;

    private HudSync() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ++clock % 10 != 0) return;
        GroupData data = GroupData.get(e.getServer().overworld());
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            String squad = WandController.selected(p);
            if (squad != null && !data.exists(squad)) {
                WandController.clearSelected(p);
                squad = null;
            }
            if (squad == null) {
                if (ACTIVE.remove(p.getUUID())) send(p, HudPacket.empty());
                continue;
            }
            ACTIVE.add(p.getUUID());
            send(p, HudPacket.build(e.getServer(), data, squad, WandController.radius(p)));
        }
    }

    private static void send(ServerPlayer p, HudPacket packet) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
    }
}
