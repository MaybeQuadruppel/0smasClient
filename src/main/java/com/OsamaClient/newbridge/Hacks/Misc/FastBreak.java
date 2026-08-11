package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;

public class FastBreak extends Module {
    public static FastBreak INSTANCE;
    private Field destroyDelayField;

    public FastBreak() {
        super("FastBreak", "Removes delay between breaking blocks", Category.MISC);
        INSTANCE = this;

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    @Override
    public void onTick(Minecraft client) {
        if (!this.enabled || client.player == null || client.gameMode == null) return;

        try {
            if (destroyDelayField == null) {
                destroyDelayField = client.gameMode.getClass().getDeclaredField("destroyDelay");
                destroyDelayField.setAccessible(true);
            }
            destroyDelayField.setInt(client.gameMode, 0);
        } catch (Exception ignored) {}
    }
}