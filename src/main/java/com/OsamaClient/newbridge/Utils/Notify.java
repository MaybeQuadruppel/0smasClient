package com.OsamaClient.newbridge.Utils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Small action-bar helper for module messages. */
public final class Notify {
    public static String last;

    private Notify() {}

    public static void bar(String text) {
        last = text;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.hud.setOverlayMessage(Component.literal(text), false);
    }
}
