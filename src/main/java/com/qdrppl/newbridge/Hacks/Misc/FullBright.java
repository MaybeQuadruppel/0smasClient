package com.qdrppl.newbridge.Hacks.Misc;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;

public class FullBright extends Module {
    public FullBright() {
        super("FullBright", "(Makes your game Bright)", Category.MISC);
    }

    @Override
    public void onTick(Minecraft client) {
        client.options.gamma().set(100.0);
    }

    @Override
    public void onDisable() {
        Minecraft.getInstance().options.gamma().set(1.0);
    }
}