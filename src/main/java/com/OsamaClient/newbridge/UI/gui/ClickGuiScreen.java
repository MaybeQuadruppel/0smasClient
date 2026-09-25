package com.OsamaClient.newbridge.UI.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * Blank screen that only exists so Minecraft frees the cursor and stops treating input as game input.
 * It draws nothing; all drawing happens in {@link ClickGui#frame()} with our own renderer.
 * Event coordinates are ignored, ClickGui reads the raw window mouse position itself.
 */
public class ClickGuiScreen extends Screen {

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal("ClickGui"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        ClickGui.INSTANCE.mouseClicked(event.button(), event.modifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        ClickGui.INSTANCE.mouseReleased(event.button());
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }

    @Override
    public boolean mouseScrolled(double x, double y, double hAmount, double vAmount) {
        ClickGui.INSTANCE.mouseScrolled(vAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        ClickGui.INSTANCE.keyPressed(event.key(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        ClickGui.INSTANCE.charTyped(event.codepoint());
        return true;
    }

    @Override
    public void removed() {
        ClickGui.INSTANCE.onScreenRemoved();
    }
}
