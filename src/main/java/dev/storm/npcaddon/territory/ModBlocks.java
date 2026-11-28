package dev.storm.npcaddon.territory;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "npcaddon");
    /** Breakable by hand, but proof against explosions so that siege fire does not end claims. */
    public static final RegistryObject<Block> CLAIM_BANNER = BLOCKS.register("claim_banner",
        () -> new ClaimBannerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F, 1200.0F)
            .sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.BLOCK)));

    private ModBlocks() {}

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
