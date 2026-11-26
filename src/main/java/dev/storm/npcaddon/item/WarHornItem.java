package dev.storm.npcaddon.item;

import dev.storm.npcaddon.group.Reinforcements;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Blowing it calls reinforcements, and uses it up. */
public class WarHornItem extends Item {
    public WarHornItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer p) {
            if (!Reinforcements.call(p)) return InteractionResultHolder.fail(stack);
            if (!p.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("Right-click: call reinforcements").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("They march in, fight, and leave after the battle").withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal("One use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
