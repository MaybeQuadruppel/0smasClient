package com.OsamaClient.newbridge.UI.gui.window;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** Leerer Screen (Maus frei, Spiel-Input aus); gezeichnet wird alles von {@link WindowManager}. */
public class WindowScreen extends Screen {

    public WindowScreen() {
        super(net.minecraft.network.chat.Component.literal("NewBridge Window"));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {}
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {}
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        WindowManager.INSTANCE.mouseClicked(event.button(), event.modifiers());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        WindowManager.INSTANCE.mouseReleased(event.button());
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return true; }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {
        WindowManager.INSTANCE.mouseScrolled(v);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        WindowManager.INSTANCE.keyPressed(event.key(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        WindowManager.INSTANCE.charTyped(event.codepoint());
        return true;
    }

    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> paths) {
        WindowManager.INSTANCE.filesDropped(paths);
    }

    @Override
    public void removed() {
        WindowManager.INSTANCE.onScreenRemoved(this);
    }
}
