package dev.storm.npcaddon.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Net {
    private static final String PROTOCOL = "13";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation("npcaddon", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void init() {
        CHANNEL.registerMessage(0, SetRelationPacket.class,
            SetRelationPacket::encode, SetRelationPacket::decode, SetRelationPacket::handle);
        CHANNEL.registerMessage(1, SquadOrderPacket.class,
            SquadOrderPacket::encode, SquadOrderPacket::decode, SquadOrderPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, SquadListPacket.class,
            SquadListPacket::encode, SquadListPacket::decode, SquadListPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, FactionListPacket.class,
            FactionListPacket::encode, FactionListPacket::decode, FactionListPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, PlayerStandingPacket.class,
            PlayerStandingPacket::encode, PlayerStandingPacket::decode, PlayerStandingPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, WandPacket.class,
            WandPacket::encode, WandPacket::decode, WandPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, HudPacket.class,
            HudPacket::encode, HudPacket::decode, HudPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(7, VehicleInputPacket.class,
            VehicleInputPacket::encode, VehicleInputPacket::decode, VehicleInputPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, RecruitMenuPacket.class,
            RecruitMenuPacket::encode, RecruitMenuPacket::decode, RecruitMenuPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(9, RecruitPacket.class,
            RecruitPacket::encode, RecruitPacket::decode, RecruitPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(10, ManageRecruitPacket.class,
            ManageRecruitPacket::encode, ManageRecruitPacket::decode, ManageRecruitPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(11, AllegiancePacket.class,
            AllegiancePacket::encode, AllegiancePacket::decode, AllegiancePacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(12, AllegianceActionPacket.class,
            AllegianceActionPacket::encode, AllegianceActionPacket::decode, AllegianceActionPacket::handle,
            Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
}
