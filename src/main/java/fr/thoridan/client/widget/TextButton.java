package fr.thoridan.client.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.GuiGraphics;


public class TextButton extends Button {
    private int textColor;

    public TextButton(int x, int y, int width, int height, Component message, OnPress onPress, int textColor) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.textColor = textColor;
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        // Draw the text at the button's position
        guiGraphics.drawString(font, this.getMessage(), this.getX(), this.getY(), textColor, false);
    }
}
