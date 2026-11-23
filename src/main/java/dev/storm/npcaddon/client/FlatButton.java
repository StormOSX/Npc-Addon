package dev.storm.npcaddon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Flat coloured button with an optional item icon, used by the squad screen. */
final class FlatButton extends Button {
    private final ItemStack icon;
    private final int color;
    private boolean active;

    FlatButton(int x, int y, int w, int h, String label, ItemStack icon, int rgb, String tip, Runnable action) {
        super(x, y, w, h, Component.literal(label), b -> action.run(), DEFAULT_NARRATION);
        this.icon = icon;
        this.color = 0xFF000000 | rgb;
        if (tip != null) setTooltip(Tooltip.create(Component.literal(tip)));
    }

    FlatButton active(boolean value) {
        this.active = value;
        return this;
    }

    private static int shade(int argb, int delta) {
        int r = Math.max(0, Math.min(255, ((argb >> 16) & 0xFF) + delta));
        int g = Math.max(0, Math.min(255, ((argb >> 8) & 0xFF) + delta));
        int b = Math.max(0, Math.min(255, (argb & 0xFF) + delta));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
        boolean hover = isHoveredOrFocused();
        int fill = active ? shade(color, 30) : hover ? shade(color, 24) : color;
        g.fill(getX(), getY(), getX() + width, getY() + height, fill);
        g.renderOutline(getX(), getY(), width, height, active ? 0xFFFFFFFF : shade(color, hover ? 70 : -40));
        Font font = Minecraft.getInstance().font;
        int textLeft = getX();
        int textWidth = width;
        if (icon != null) {
            g.renderItem(icon, getX() + 3, getY() + (height - 16) / 2);
            textLeft += 20;
            textWidth -= 20;
        }
        g.drawCenteredString(font, getMessage(), textLeft + textWidth / 2, getY() + (height - 8) / 2, 0xFFFFFFFF);
    }
}
