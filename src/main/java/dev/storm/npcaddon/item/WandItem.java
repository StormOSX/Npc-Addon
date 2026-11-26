package dev.storm.npcaddon.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The command wand. It does nothing by itself: the client turns clicks into a WandPacket (so orders reach far
 * beyond normal block/entity reach) and the server runs them in WandController.
 */
public class WandItem extends Item {
    public WandItem(Properties props) {
        super(props);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Left-click an NPC: select its squad").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Left-click elsewhere: deselect").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click ground: move there and hold").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Right-click entity: attack it").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Ctrl + right-click: attack the area around it").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("Sneak + right-click: follow / stop").withStyle(ChatFormatting.GRAY));
    }
}
