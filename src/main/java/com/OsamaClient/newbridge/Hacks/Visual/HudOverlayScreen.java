package com.OsamaClient.newbridge.Hacks.Visual;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Blank screen, nur da um Maus/Tastatur abzufangen, während {@link HudOverlay}'s Layout-Editor aktiv ist
 * - exakt dasselbe Muster wie {@code ClickGuiScreen} für die ClickGUI. Gezeichnet wird ausschließlich in
 * {@link HudOverlay#draw()}, das vom {@code GameRendererUiMixin} sowieso jeden Frame aufgerufen wird -
 * dieser Screen sorgt nur dafür, dass Klicks nicht mehr am Spiel (Blockabbau etc.) landen, und dass wir
 * Escape zum Verlassen des Edit-Modus bekommen. Kein eigenes {@code render()}: ein Override würde hier
 * nur das eh schon leere Default-Verhalten wiederholen (und war Ursache des GuiGraphics-Fehlers).
 *
 * Mausposition wird bewusst NICHT aus dem Event gelesen (das wäre in GUI-Scale-Pixeln), sondern wie bei
 * ClickGui aus {@code UiInput.mouseFbX()/mouseFbY()} - demselben Koordinatensystem, in dem auch die
 * {@code Ui}-Klasse rechnet.
 */
public class HudOverlayScreen extends Screen {

    public HudOverlayScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        HudOverlay.instance.mouseClicked(event.buttonInfo().button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        HudOverlay.instance.mouseReleased(event.buttonInfo().button());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        HudOverlay.instance.keyPressed(event.key(), event.modifiers());
        return true;
    }

    @Override
    public void removed() {
        HudOverlay.instance.screenClosed();
    }
}