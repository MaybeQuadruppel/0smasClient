package com.qdrppl.newbridge.Hacks.Misc;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;

public class FullBright extends Module {
    public static FullBright instance;
    public static double amount = 16.0;

    public FullBright() {
        super("FullBright", "Makes your game Bright", Category.MISC);
        instance = this;
        this.settings.add(new Slider("amount", 0.1, 16.0, 16.0, val -> amount = val));
    }
}
