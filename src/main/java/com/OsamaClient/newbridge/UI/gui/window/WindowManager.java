package com.OsamaClient.newbridge.UI.gui.window;

import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import net.minecraft.client.Minecraft;
import com.OsamaClient.newbridge.UI.gui.input.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Zeigt genau ein {@link UiWindow} (Waypoints, Profiles, Macros, ...). Gleiche Render-Pipeline wie
 * die ClickGUI; aufgerufen aus GameRendererUiMixin. Wurde das Fenster aus der ClickGUI geöffnet,
 * kehrt ESC/Schließen dorthin zurück.
 */
public final class WindowManager {

    public static final WindowManager INSTANCE = new WindowManager();
    private static final Logger LOG = LoggerFactory.getLogger("newbridge/Windows");

    private final UiRenderer renderer = new UiRenderer();
    private final Ui ui = new Ui(renderer);
    private UiWindow window;
    private UiWindow pending;
    private int pendingDelay;
    private boolean returnToClickGui;
    private long lastNanos;
    private int failures;

    private WindowManager() {}

    /** Einträge für das Dock in der ClickGUI (Label -> Fenster-Fabrik). */
    public record DockEntry(String label, String description, java.util.function.Supplier<UiWindow> factory) {}
    private final java.util.List<DockEntry> dock = new java.util.ArrayList<>();

    public void registerDock(String label, String description, java.util.function.Supplier<UiWindow> factory) {
        dock.removeIf(d -> d.label().equals(label));
        dock.add(new DockEntry(label, description, factory));
    }

    public java.util.List<DockEntry> dock() { return dock; }

    public UiWindow current() { return window; }

    public boolean isOpen() {
        return window != null && Minecraft.getInstance().gui.screen() instanceof WindowScreen;
    }

    /** Öffnet sofort (z.B. aus der ClickGUI heraus). */
    public void open(UiWindow w) {
        Minecraft mc = Minecraft.getInstance();
        returnToClickGui = ClickGui.INSTANCE.isOpen();
        window = w;
        w.onOpen();
        lastNanos = 0;
        mc.gui.setScreen(new WindowScreen());
    }

    /** Öffnet im nächsten Tick (aus Chat-Befehlen: der Chat-Screen schließt sich sonst darüber). */
    public void openLater(UiWindow w) {
        pending = w;
        pendingDelay = 2;
    }

    /** Pro Client-Tick aus EntryPoint. */
    public void tick() {
        if (pending != null && --pendingDelay <= 0) {
            UiWindow w = pending;
            pending = null;
            if (Minecraft.getInstance().gui.screen() == null) open(w);
        }
    }

    public void close() {
        Minecraft mc = Minecraft.getInstance();
        UiWindow w = window;
        window = null;
        if (w != null) w.onClose();
        boolean back = returnToClickGui;
        returnToClickGui = false;
        if (mc.gui.screen() instanceof WindowScreen) mc.gui.setScreen(null);
        if (back) ClickGui.INSTANCE.open();
    }

    void onScreenRemoved(WindowScreen screen) {
        if (window != null && Minecraft.getInstance().gui.screen() == screen) {
            // wird gleich durch etwas anderes ersetzt
            UiWindow w = window;
            window = null;
            w.onClose();
        }
    }

    // ------------------------------------------------------------------ frame

    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (window == null || !(mc.gui.screen() instanceof WindowScreen) || mc.gui.overlay() != null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        try {
            renderer.begin();
            ui.scale = Theme.scale();
            ui.dt = dt;
            ui.mouseX = UiInput.mouseFbX() / ui.scale;
            ui.mouseY = UiInput.mouseFbY() / ui.scale;
            ui.alpha = 1f;
            ui.hoverBlocked = false;
            ui.rect(0, 0, ui.width(), ui.height(), ColorUtil.alpha(0xFF000000, 0.35f));
            window.frame(ui, dt);
            renderer.flush();
            UiFont.collect(600);
        } catch (RuntimeException e) {
            if (failures++ % 600 == 0) LOG.error("Window frame failed ({}x)", failures, e);
            renderer.begin();
        }
    }

    // ------------------------------------------------------------------ input

    void mouseClicked(int button, int mods) {
        if (window != null) window.queueClick(button, mods);
    }

    void mouseReleased(int button) {
        if (window != null) window.mouseReleased(button);
    }

    void mouseScrolled(double amount) {
        if (window != null) window.queueScroll(amount);
    }

    void keyPressed(int key, int mods) {
        if (window == null) return;
        if (window.keyPressed(key, mods)) return;
        if (key == Keys.KEY_ESCAPE) close();
    }

    void charTyped(int cp) {
        if (window != null) window.charTyped(cp);
    }

    public void shutdown() {
        renderer.close();
    }
}
