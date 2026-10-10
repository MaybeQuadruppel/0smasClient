package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Blank screen: frees the cursor. All drawing happens in {@link EmoteWheel#frame()}. */
public class EmoteWheelScreen extends Screen {

    public EmoteWheelScreen() {
        super(Component.literal("Emote Wheel"));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean shouldCloseOnEsc() { return false; } // handled in EmoteWheel.keyPressed

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        EmoteWheel.INSTANCE.mouseClicked(event.button());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        EmoteWheel.INSTANCE.keyPressed(event.key());
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double hAmount, double vAmount) {
        EmoteWheel.INSTANCE.mouseScrolled(vAmount);
        return true;
    }

    @Override
    public void removed() {
        EmoteWheel.INSTANCE.onScreenRemoved();
    }
}