package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;

/**
 * Viewmodel: moves, scales and rotates the items / hands in first person.
 * Based on the Viewmodel module of the Autism client (GPL-3.0). Applied by FirstPersonHandsMixin.
 */
public class Viewmodel extends Module {

    public static Viewmodel INSTANCE;

    public boolean mainOn = true;
    public double mainX, mainY, mainZ, mainScale, mainRotX, mainRotY, mainRotZ;
    public boolean offOn = true;
    public double offX, offY, offZ, offScale, offRotX, offRotY, offRotZ;
    public int applied;

    public Viewmodel() {
        super("Viewmodel", "Move, scale and rotate your first person hands and items.", Category.VISUAL);
        INSTANCE = this;
        settings.add(new ToggleButton("Main Hand", mainOn, v -> mainOn = v).withDescription("Apply to the main hand."));
        settings.add(new Slider("Main X", -2, 2, mainX, 0.05, v -> mainX = v));
        settings.add(new Slider("Main Y", -2, 2, mainY, 0.05, v -> mainY = v));
        settings.add(new Slider("Main Z", -2, 2, mainZ, 0.05, v -> mainZ = v));
        settings.add(new Slider("Main Scale", -0.9, 2, mainScale, 0.05, v -> mainScale = v).withDescription("0 = normal size."));
        settings.add(new Slider("Main Rot X", -180, 180, mainRotX, 5, v -> mainRotX = v));
        settings.add(new Slider("Main Rot Y", -180, 180, mainRotY, 5, v -> mainRotY = v));
        settings.add(new Slider("Main Rot Z", -180, 180, mainRotZ, 5, v -> mainRotZ = v));
        settings.add(new ToggleButton("Off Hand", offOn, v -> offOn = v).withDescription("Apply to the off hand."));
        settings.add(new Slider("Off X", -2, 2, offX, 0.05, v -> offX = v));
        settings.add(new Slider("Off Y", -2, 2, offY, 0.05, v -> offY = v));
        settings.add(new Slider("Off Z", -2, 2, offZ, 0.05, v -> offZ = v));
        settings.add(new Slider("Off Scale", -0.9, 2, offScale, 0.05, v -> offScale = v).withDescription("0 = normal size."));
        settings.add(new Slider("Off Rot X", -180, 180, offRotX, 5, v -> offRotX = v));
        settings.add(new Slider("Off Rot Y", -180, 180, offRotY, 5, v -> offRotY = v));
        settings.add(new Slider("Off Rot Z", -180, 180, offRotZ, 5, v -> offRotZ = v));
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.enabled; }
}
