package com.qdrppl.newbridge.Hacks.Misc;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;
import net.minecraft.client.Minecraft;

public class FullBright extends Module {
    public static double amount = 16.0;
    public static FullBright instance;

    public FullBright() {
        super("FullBright", "Makes your game Bright", Category.MISC);
        this.settings.add(new Slider("amount", 0.1, 16.0, 16.0, val -> amount = val));
    }

    @Override
    public void onTick(Minecraft client) {
        if (this.enabled && client.options != null) {
            client.options.gamma().set(amount);
        }
    }
}