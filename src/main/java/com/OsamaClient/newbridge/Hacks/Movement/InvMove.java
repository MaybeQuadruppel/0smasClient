package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.event.Subscribe;
import com.OsamaClient.newbridge.event.TickEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

public class InvMove extends Module {

    public InvMove() {
        super("InvMove", "Allows movement while inventory screens are open", Category.MOVEMENT);
        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Subscribe
    public void onTick(TickEvent event) {
        if (!this.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() == null) return;
        if (mc.gui.screen() instanceof ChatScreen) return;

        KeyMapping[] moveKeys = new KeyMapping[]{
                mc.options.keyUp,
                mc.options.keyDown,
                mc.options.keyLeft,
                mc.options.keyRight,
                mc.options.keyJump,
                mc.options.keySprint
        };
        for (KeyMapping key : moveKeys) {

            int keyCode = InputConstants.getKey(key.saveString()).getValue();

            if (keyCode != InputConstants.UNKNOWN.getValue()) {
                boolean isPressed = InputConstants.isKeyDown(mc.getWindow(), keyCode);
                key.setDown(isPressed);
            }
        }
    }
}