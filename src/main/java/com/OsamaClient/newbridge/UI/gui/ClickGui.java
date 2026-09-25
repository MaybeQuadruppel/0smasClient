package com.OsamaClient.newbridge.UI.gui;

import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import net.minecraft.client.Minecraft;

/** ClickGUI root (renderer spike). */
public final class ClickGui {

    public static final ClickGui INSTANCE = new ClickGui();

    /** Spike: draw the test shapes on every frame, even without the screen open. */
    public static boolean SPIKE_ALWAYS = true;

    private final UiRenderer renderer = new UiRenderer();

    private ClickGui() {}

    public void open() {
        Minecraft.getInstance().gui.setScreen(new ClickGuiScreen());
    }

    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() != null) return; // resources (our shaders) may not be loaded yet
        if (!SPIKE_ALWAYS && !(mc.gui.screen() instanceof ClickGuiScreen)) return;
        renderer.begin();
        int accent = 0xFF6C8CFF;
        // glow + rounded rect at fixed physical pixels
        renderer.shape(100, 100, 200, 60, 8, 2, 16, accent & 0x99FFFFFF, accent & 0x99FFFFFF, accent & 0x99FFFFFF, accent & 0x99FFFFFF);
        renderer.shape(100, 100, 200, 60, 8, 0, 0, 0xF0121216, 0xF0121216, 0xF0121216, 0xF0121216);
        renderer.shape(100, 100, 200, 60, 8, 1, 1, accent, accent, accent, accent);
        // gradient
        renderer.shape(100, 200, 200, 40, 4, 0, 0, 0xFFFFFFFF, 0xFFFF0000, 0xFF000000, 0xFF000000);
        // 1px lines to check crispness
        for (int i = 0; i < 10; i++) renderer.shape(100 + i * 4, 260, 1, 20, 0, 0, 0, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF);
        // clipped rect
        renderer.pushClip(100, 300, 100, 30);
        renderer.shape(80, 290, 200, 60, 10, 0, 0, 0xFF30D080, 0xFF30D080, 0xFF30D080, 0xFF30D080);
        renderer.popClip();
        renderer.flush();
    }

    public void mouseClicked(int button, int mods) {}
    public void mouseReleased(int button) {}
    public void mouseScrolled(double amount) {}
    public void keyPressed(int key, int mods) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) Minecraft.getInstance().gui.setScreen(null);
    }
    public void charTyped(int codepoint) {}
    public void onScreenRemoved() {}
}
