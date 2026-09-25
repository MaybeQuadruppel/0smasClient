package com.OsamaClient.newbridge.UI.gui.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/** Raw window input: mouse position in framebuffer pixels (never MC's GUI-scaled coordinates). */
public final class UiInput {

    private UiInput() {}

    public static float mouseFbX() {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        return (float) (mc.mouseHandler.xpos() * w.getWidth() / Math.max(1, w.getScreenWidth()));
    }

    public static float mouseFbY() {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        return (float) (mc.mouseHandler.ypos() * w.getHeight() / Math.max(1, w.getScreenHeight()));
    }

    public static boolean keyDown(int key) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), key);
    }

    public static boolean ctrl(int mods) { return (mods & GLFW.GLFW_MOD_CONTROL) != 0; }

    public static boolean shift(int mods) { return (mods & GLFW.GLFW_MOD_SHIFT) != 0; }

    public static String clipboard() {
        String s = Minecraft.getInstance().keyboardHandler.getClipboard();
        return s == null ? "" : s;
    }

    public static void setClipboard(String s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(s);
    }
}
