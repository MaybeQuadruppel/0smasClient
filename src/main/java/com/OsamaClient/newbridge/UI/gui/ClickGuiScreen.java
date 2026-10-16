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

    /** Drag a picture / GIF / video onto the window: it becomes the custom sky. */
    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> paths) {
        // pictures named *cape* and ready-made 2:1 cape textures become the cape, everything else the sky
        var capes = new java.util.ArrayList<java.nio.file.Path>();
        var rest = new java.util.ArrayList<java.nio.file.Path>();
        for (var p : paths) (looksLikeCape(p) ? capes : rest).add(p);
        if (!capes.isEmpty()) com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.dropped(capes);
        if (!rest.isEmpty()) com.OsamaClient.newbridge.Hacks.World.sky.CustomSky.dropped(rest);
    }

    private static boolean looksLikeCape(java.nio.file.Path p) {
        if (!com.OsamaClient.newbridge.Hacks.Visual.cape.CustomCape.supported(p)) return false;
        if (p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).contains("cape")) return true;
        try {
            var img = javax.imageio.ImageIO.read(p.toFile());
            return img != null && img.getWidth() % 64 == 0 && img.getWidth() == img.getHeight() * 2 && img.getWidth() <= 1024;
        } catch (Exception e) { return false; }
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
    public boolean keyReleased(KeyEvent event) {
        ClickGui.INSTANCE.keyReleased(event.key());
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
