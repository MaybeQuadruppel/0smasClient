package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

public class EmoteModule extends Module {
    public static EmoteModule INSTANCE;

    public final Slider wheelScale = new Slider("Wheel Scale", 0.5, 2.0, 1.0, 0.1, null)
            .withDescription("Scale of the emote wheel");

    public EmoteModule() {
        super("Emotes", "Opens the emote wheel.", Category.PLAYER);
        INSTANCE = this;
        key = Keys.KEY_B;
        settings.add(wheelScale);
        EmotePlayer.register(); // <-- neu
    }

    @Override
    public void toggle() {
        enabled = false; // never stays on, it only opens the wheel
        EmoteWheel.INSTANCE.open();
    }
}