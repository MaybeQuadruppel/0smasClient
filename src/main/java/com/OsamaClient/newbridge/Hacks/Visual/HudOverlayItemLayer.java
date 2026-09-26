package com.OsamaClient.newbridge.Hacks.Visual;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Zeichnet die von {@link HudOverlay#pendingIcons} eingereihten Item-Icons - der einzige Ort im Mod, der
 * das noch darf: {@code GuiGraphicsExtractor#item(...)} ist kein Sofort-Zeichnen mehr, sondern reicht nur
 * einen {@code GuiItemRenderState} in den {@code GuiRenderState} des aktuellen Frames weiter, den
 * {@code GuiRenderer#render()} danach abarbeitet. Das funktioniert deshalb nur mit dem
 * {@code GuiGraphicsExtractor}, den die Fabric-{@link HudElementRegistry} selbst während der
 * Extraktionsphase durchreicht - nicht mit einem selbst gebauten {@code GuiGraphics}, das (wie vorher)
 * erst nach {@code GuiRenderer#render()} existiert und dessen Frame damit schon "vorbei" ist.
 *
 * Alles andere (Panel-Hintergrund, Badge-Rahmen, Text, Haltbarkeits-Bar, Drag) bleibt exakt wie gehabt in
 * {@link HudOverlay}'s eigener SDF/Text-Pipeline, die weiterhin erst NACH {@code GuiRenderer#render()} im
 * {@code GameRendererUiMixin} läuft - dieser Layer zeichnet nur die eigentlichen Item-Icons obendrauf...
 * genauer: eine Extraktion früher, sodass sie unter unseren Badges landen. Ein Frame Versatz zwischen
 * Badge-Position und Icon-Position ist bei normaler Framerate nicht wahrnehmbar.
 */
public final class HudOverlayItemLayer {

    private HudOverlayItemLayer() {}

    private static boolean registered;

    /**
     * Registriert sich selbst - idempotent, damit man das einfach aus {@link HudOverlay}'s Konstruktor
     * aufrufen kann, statt einen eigenen {@code ClientModInitializer}-Eintrag dafür anzulegen. Falls ihr
     * ohnehin schon einen habt, könnt ihr das genauso gut von dort aus aufrufen - schadet nicht doppelt,
     * es wird trotzdem nur einmal bei {@code HudElementRegistry} eingehängt.
     */
    public static void register() {
        if (registered) return;
        registered = true;
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("newbridge", "hud_overlay_items"),
                HudOverlayItemLayer::render);
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        if (HudOverlay.pendingIcons.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        if (guiScale <= 0) return;

        for (HudOverlay.PendingIcon p : HudOverlay.pendingIcons) {
            try {
                float x = (float) (p.fbX() / guiScale);
                float y = (float) (p.fbY() / guiScale);
                float size = (float) (p.fbSize() / guiScale);
                g.pose().pushMatrix();
                g.pose().translate(x, y);
                g.pose().scale(size / 16f, size / 16f);
                g.item(p.stack(), 0, 0);
                g.pose().popMatrix();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
