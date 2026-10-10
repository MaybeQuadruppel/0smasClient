package com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Blank Minecraft screen. The visible editor UI is drawn by GameRendererUiMixin. */
public final class EmoteEditorScreen extends Screen {

    public EmoteEditorScreen() {
        super(Component.literal("EmoteEditor"));
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        // Only extract the 3D player preview here.
        // The custom editor UI is rendered by GameRendererUiMixin -> EmoteEditor.frame().
        EmoteEditor.INSTANCE.extractPlayerPreview(graphics);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (EmoteEditor.INSTANCE.mouseClicked(event.button(), event.x(), event.y())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (EmoteEditor.INSTANCE.mouseDragged(event.x(), event.y())) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        EmoteEditor.INSTANCE.mouseReleased(event.button());
        return true;
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        EmoteEditor.INSTANCE.mouseScrolled(mouseX, mouseY, verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (EmoteEditor.INSTANCE.keyPressed(event.key(), event.modifiers())) return true;
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        EmoteEditor.INSTANCE.charTyped(event.codepoint());
        return true;
    }

    @Override
    public void removed() {
        EmoteEditor.INSTANCE.onScreenRemoved();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}