package com.OsamaClient.newbridge.Hacks.Visual;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop-Editor für das {@link HudOverlay}, im Stil des HUD-Editors
 * von Lunar/Badlion: die Welt läuft im Hintergrund weiter
 * ({@link #isPauseScreen()} = false), alle Widgets (auch ausgeblendete)
 * werden mit Auswahlrahmen gezeichnet und lassen sich per Drag
 * verschieben.
 *
 * Steuerung:
 *  - Ziehen (LMB) auf Widget     -> Widget verschieben
 *  - Rechtsklick auf Widget       -> Widget ein-/ausblenden
 *  - Mausrad über Widget          -> Widget individuell größer/kleiner skalieren
 *  - "C" über Widget              -> Hue-Slider ein-/ausblenden, dann per Ziehen Farbe wählen
 *  - Esc / "Done"                 -> speichern & schließen
 *
 * Während des Draggens wird die Zielposition so geklemmt, dass ein Widget
 * NICHT hinter dem "Done"-Button oder der oberen Hinweisleiste landen kann -
 * vorher konnte man ein Widget dort "verlieren", weil beide Elemente über
 * allem anderen gezeichnet werden und Klicks abfangen.
 */
public class HudLayoutScreen extends Screen {

    private String draggingId = null;
    private int dragOffX, dragOffY;

    /** Widget, dessen Hue-Slider gerade per Drag bedient wird (unabhängig
     *  vom Positions-Dragging oben). */
    private String colorDragId = null;

    private int lastMouseX, lastMouseY;

    private static final int DONE_W = 60, DONE_H = 18;
    /** Dünner Streifen unten für den Steuerungs-Hinweis - bewusst schmal und
     *  am unteren Rand, damit er sich nicht mit Widgets nahe der Bildschirm-
     *  Oberkante (fps, ping, coords, ...) oder dem "Done"-Button überschneidet. */
    private static final int HINT_BAR_H = 14;
    /** Zusätzlicher Sicherheitsabstand um die Sperrzonen, damit ein Widget
     *  nicht direkt am Rand des Buttons klebt. */
    private static final int AVOID_PADDING = 4;

    private static final float SCROLL_SCALE_STEP = 0.05f;

    public HudLayoutScreen() {
        super(net.minecraft.network.chat.Component.literal(""));
    }

    // ── Sperrzonen für das Draggen / automatische Kollisionsauflösung ───────

    private List<int[]> buildAvoidRects() {
        List<int[]> rects = new ArrayList<>(2);

        int doneX = this.width - DONE_W - 6;
        int doneY = 4;
        rects.add(new int[]{
                doneX - AVOID_PADDING, doneY - AVOID_PADDING,
                DONE_W + AVOID_PADDING * 2, DONE_H + AVOID_PADDING * 2
        });

        // Hinweisleiste sitzt jetzt unten - ebenfalls sperren, sonst könnte
        // z.B. "keystrokes" (Standardposition nahe unten) dahinter landen.
        int hintY = this.height - HINT_BAR_H;
        rects.add(new int[]{0, hintY - AVOID_PADDING, this.width, HINT_BAR_H + AVOID_PADDING});

        return rects;
    }

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        // Drag-Update passiert hier (statt in einem eigenen mouseDragged-Callback),
        // damit wir uns nicht auf eine nicht bestätigte mouseDragged-Signatur
        // verlassen müssen - dieselbe Technik, mit der früher die
        // Kategorie-Panels in ClickGuiScreen verschoben wurden.
        if (draggingId != null) {
            HudOverlay.updateDragPosition(draggingId, mouseX - dragOffX, mouseY - dragOffY,
                    this.width, this.height, buildAvoidRects());
        }

        // Hue-Slider-Drag: Bar-Bounds stammen vom letzten Render-Frame (siehe
        // HudOverlay#drawEditorFrame), reichen hier also schon aus.
        if (colorDragId != null) {
            HudOverlay.setColorFromBarX(colorDragId, mouseX);
        }

        HudOverlay.drawForEditor(g, mouseX, mouseY);

        // Widgets, die zufällig (per Default-Position oder altem Save) in
        // einer Sperrzone liegen, automatisch herausschieben - nicht nur
        // während des aktiven Draggens oben. Das aktuell gezogene Widget wird
        // ausgenommen, da updateDragPosition das schon selbst übernimmt.
        java.util.Set<String> skip = draggingId != null
                ? java.util.Collections.singleton(draggingId)
                : java.util.Collections.emptySet();
        HudOverlay.resolveOverlaps(this.width, this.height, buildAvoidRects(), skip);

        renderHintBar(g, mouseX, mouseY);
        renderDoneButton(g, mouseX, mouseY);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private void renderHintBar(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        String hint = "Drag = move  \u2022  RMB = show/hide  \u2022  Scroll = size  \u2022  C = color picker  \u2022  Esc = done";
        int hw = this.font.width(hint);
        int hx = this.width / 2 - hw / 2;
        int hy = this.height - HINT_BAR_H;
        g.fill(0, hy - 2, this.width, this.height, 0xB0000000);
        g.text(this.font, hint, hx, hy + (HINT_BAR_H - this.font.lineHeight) / 2, 0xFFFFFFFF, true);
    }

    private void renderDoneButton(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int doneX = this.width - DONE_W - 6, doneY = 4;
        boolean doneHovered = mouseX >= doneX && mouseX <= doneX + DONE_W && mouseY >= doneY && mouseY <= doneY + DONE_H;

        g.fill(doneX, doneY, doneX + DONE_W, doneY + DONE_H, doneHovered ? 0xE0335533 : 0xC0223322);
        g.fill(doneX, doneY, doneX + DONE_W, doneY + 1, 0xFF55FF55);
        String doneLabel = "Done";
        int dlw = this.font.width(doneLabel);
        g.text(this.font, doneLabel, doneX + (DONE_W - dlw) / 2, doneY + (DONE_H - this.font.lineHeight) / 2,
                doneHovered ? 0xFFFFFFFF : 0xFFCCFFCC, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), btn = event.button();

        // Slider zuerst prüfen: er sitzt direkt über dem Widget, teils nah am
        // restlichen UI, und soll Vorrang vor Verschieben/Done haben.
        if (btn == 0) {
            String colorHit = HudOverlay.hitTestColorBar(mx, my);
            if (colorHit != null) {
                colorDragId = colorHit;
                HudOverlay.setColorFromBarX(colorHit, mx);
                return true;
            }
        }

        int doneX = this.width - DONE_W - 6, doneY = 4;
        if (btn == 0 && mx >= doneX && mx <= doneX + DONE_W && my >= doneY && my <= doneY + DONE_H) {
            onClose();
            return true;
        }

        String hit = HudOverlay.hitTest(mx, my);
        if (hit != null) {
            if (btn == 0) {
                int[] pos = HudOverlay.pixelPosition(hit, this.width, this.height);
                draggingId = hit;
                dragOffX = mx - pos[0];
                dragOffY = my - pos[1];
                return true;
            } else if (btn == 1) {
                HudOverlay.toggleElement(hit);
                return true;
            }
        }

        // Klick auf leere Fläche (kein Widget, kein Slider, kein "Done")
        // schließt einen offenen Farbregler wieder - sonst bliebe er
        // unsichtbar "offen" hängen, bis man erneut "C" drückt.
        if (btn == 0) {
            HudOverlay.hideColorPicker();
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingId != null) {
            draggingId = null;
            HudOverlay.saveLayout();
        }
        if (colorDragId != null) {
            colorDragId = null;
            HudOverlay.saveLayout();
        }
        return super.mouseReleased(event);
    }

    /** Mausrad über einem Widget skaliert dieses Widget individuell -
     *  so lässt sich z.B. das Armor-HUD kleiner ziehen, ohne die globale
     *  "Scale"-Einstellung (die alle Widgets betrifft) anzufassen. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        String hit = HudOverlay.hitTest((int) mouseX, (int) mouseY);
        if (hit != null && verticalAmount != 0) {
            float delta = verticalAmount > 0 ? SCROLL_SCALE_STEP : -SCROLL_SCALE_STEP;
            HudOverlay.bumpElementScale(hit, delta);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // "C" über einem Modul blendet dessen Hue-Slider ein/aus - vorher
        // stand der Slider immer offen neben dem (inzwischen entfernten) Label, jetzt nur auf
        // Wunsch, damit der Editor aufgeräumter wirkt.
        if (event.key() == GLFW.GLFW_KEY_C) {
            String hovered = HudOverlay.hitTest(lastMouseX, lastMouseY);
            if (hovered != null) {
                HudOverlay.toggleColorPicker(hovered);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        HudOverlay.hideColorPicker();
        HudOverlay.saveLayout();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
