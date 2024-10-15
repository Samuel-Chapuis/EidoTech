package fr.thoridan.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.*;

public class TextButton extends Button {
    public TextButton(int x, int y, int width, int height, Component message, net.minecraft.client.gui.components.Button.OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        // Check if mouse is over the button
        boolean hovered = isHoveredOrFocused();

        // Change text color when hovered
        int textColor = hovered ? 0xFFFFA0 : 0xFFFFFF; // Yellow when hovered, white otherwise

        // Draw the text at the button's position
        guiGraphics.drawString(font, this.getMessage(), this.getX(), this.getY(), textColor, false);
    }
}