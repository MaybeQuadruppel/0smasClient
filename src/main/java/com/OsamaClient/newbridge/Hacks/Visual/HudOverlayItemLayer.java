package com.OsamaClient.newbridge.Hacks.Visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.resources.Identifier;

/**
 * Draws the real item icons and player faces collected in {@link HudIcons}. This is the only place in the mod
 * that may do that: {@code GuiGraphicsExtractor#item(...)} only hands a render state to the vanilla GUI renderer,
 * which works with the extractor that Fabric's {@link HudElementRegistry} passes during the extraction phase.
 * Everything else (panels, badges, text, bars, dragging) is drawn by our own SDF/text pipeline after the vanilla
 * GUI. The icons are one frame behind their badges, which is not visible at normal frame rates.
 */
public final class HudOverlayItemLayer {

    private HudOverlayItemLayer() {}

    private static boolean registered;
    private static boolean logged;
    public static int drawn, calls;

    /** Idempotent. */
    public static void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("newbridge", "hud_overlay_items"),
                HudOverlayItemLayer::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        calls++;
        if (HudIcons.items.isEmpty() && HudIcons.faces.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0) return;

        for (HudIcons.Item p : HudIcons.items) {
            try {
                g.pose().pushMatrix();
                g.pose().translate((float) (p.fbX() / guiScale), (float) (p.fbY() / guiScale));
                float s = (float) (p.fbSize() / guiScale) / 16f;
                g.pose().scale(s, s);
                g.item(p.stack(), 0, 0);
                g.pose().popMatrix();
                drawn++;
            } catch (RuntimeException ex) {
                if (!logged) { logged = true; System.out.println("[HudIcons] item draw failed: " + ex); }
            }
        }
        for (HudIcons.Face f : HudIcons.faces) {
            try {
                g.pose().pushMatrix();
                g.pose().translate((float) (f.fbX() / guiScale), (float) (f.fbY() / guiScale));
                float s = (float) (f.fbSize() / guiScale) / 8f;
                g.pose().scale(s, s);
                PlayerFaceExtractor.extractRenderState(g, f.skin(), 0, 0, 8);
                g.pose().popMatrix();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
