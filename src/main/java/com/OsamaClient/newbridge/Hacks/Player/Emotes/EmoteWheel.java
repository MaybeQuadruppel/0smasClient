package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor.EmoteEditor;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.anim.Anim;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import net.minecraft.client.Minecraft;
import com.OsamaClient.newbridge.UI.gui.input.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Emote wheel root: drawn every frame with our own renderer while the EmoteWheelScreen is open.
 * The same screen also hosts the emote library page ({@link EmoteLibraryPage}).
 */
public final class EmoteWheel {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/EmoteWheel");

    private enum Mode { WHEEL, LIBRARY }

    public static final EmoteWheel INSTANCE = new EmoteWheel();

    private static final float MIN_RADIUS = 75f;
    private static final float DEADZONE = 26f;
    private static final float BTN_W = 110f, BTN_H = 24f, BTN_GAP = 10f;

    private final UiRenderer renderer = new UiRenderer();
    private final Ui ui = new Ui(renderer);
    private final Anim openA = new Anim(0f, 14f);
    private final EmoteLibraryPage library = new EmoteLibraryPage(this);

    private Mode mode = Mode.WHEEL;
    private List<EmoteAnimation> emotes = List.of();
    private Anim[] hoverA = new Anim[0];
    private long lastNanos;
    private int hovered = -1;
    private int failures;

    private EmoteWheel() {}

    // ------------------------------------------------------------------ open / close

    public void open() { open(Mode.WHEEL); }

    /** Opens the screen directly on the library page (used by the editor). */
    public void openLibrary() { open(Mode.LIBRARY); }

    private void open(Mode m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof EmoteWheelScreen) {
            setMode(m);
            return;
        }
        lastNanos = 0;
        openA.snap(0f);
        setMode(m);
        mc.gui.setScreen(new EmoteWheelScreen());
    }

    public void close() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    void showWheel() { setMode(Mode.WHEEL); }

    void showLibrary() { setMode(Mode.LIBRARY); }

    private void setMode(Mode m) {
        mode = m;
        hovered = -1;
        if (m == Mode.WHEEL) {
            emotes = EmoteLibrary.wheelEmotes();
            hoverA = new Anim[emotes.size()];
            for (int i = 0; i < hoverA.length; i++) hoverA[i] = new Anim(0f, 18f);
        } else {
            library.reset();
        }
    }

    public void onScreenRemoved() {
        hovered = -1;
        lastNanos = 0;
    }

    public void shutdown() {
        renderer.close();
    }

    // ------------------------------------------------------------------ frame

    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.overlay() != null) return;
        if (!(mc.gui.screen() instanceof EmoteWheelScreen)) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        try {
            renderer.begin();
            ui.scale = Theme.scale() * (float) EmoteModule.INSTANCE.wheelScale.getValue();
            ui.dt = dt;
            ui.mouseX = UiInput.mouseFbX() / ui.scale;
            ui.mouseY = UiInput.mouseFbY() / ui.scale;
            ui.alpha = 1f;
            ui.hoverBlocked = false;
            draw(dt);
            renderer.flush();
            UiFont.collect(600);
        } catch (RuntimeException e) {
            if (failures++ % 600 == 0) LOG.error("EmoteWheel frame failed ({}x)", failures, e);
            renderer.begin();
        }
    }

    private float btnY() { return ui.height() - 40f; }

    private float newBtnX() { return ui.width() / 2f - BTN_GAP / 2f - BTN_W; }

    private float libBtnX() { return ui.width() / 2f + BTN_GAP / 2f; }

    private void drawButton(String text, float x) {
        boolean hover = ui.hovered(x, btnY(), BTN_W, BTN_H);
        ui.round(x, btnY(), BTN_W, BTN_H, 4f, hover ? Theme.ROW_HOVER : Theme.ROW);
        ui.outline(x, btnY(), BTN_W, BTN_H, 4f, 1f, hover ? Theme.accent() : Theme.SETTING_BLOCK_BORDER);
        ui.textCentered(text, x + BTN_W / 2f, btnY(), BTN_H, Theme.FONT_SMALL, hover ? 0xFFFFFFFF : Theme.TEXT_DIM);
    }

    private void draw(float dt) {
        float speed = Theme.animSpeed();
        openA.setTarget(1f);
        openA.update(dt, speed);
        float open = openA.value();
        ui.alpha = open;

        if (mode == Mode.LIBRARY) {
            library.draw(ui, dt);
            ui.alpha = 1f;
            return;
        }

        float cx = ui.width() / 2f;
        float cy = ui.height() / 2f;
        int n = emotes.size();
        final float radius = Math.max(MIN_RADIUS, n * 11.5f);
        double seg = n == 0 ? 0 : 2 * Math.PI / n;

        float dx = ui.mouseX - cx, dy = ui.mouseY - cy;
        hovered = -1;
        if (n > 0 && Math.sqrt(dx * dx + dy * dy) > DEADZONE) {
            double a = Math.atan2(dy, dx) + Math.PI / 2;
            if (a < 0) a += 2 * Math.PI;
            hovered = (int) (((a + seg / 2) % (2 * Math.PI)) / seg) % n;
        }
        for (int i = 0; i < n; i++) {
            hoverA[i].setTarget(i == hovered ? 1f : 0f);
            hoverA[i].update(dt, speed);
        }

        ui.rect(0, 0, ui.width(), ui.height(), 0x88000000);

        float g = Theme.glow();
        if (g > 0) ui.glow(cx - radius, cy - radius, radius * 2, radius * 2, radius, 8f, Theme.accent(0.10f * g));
        ui.outline(cx - radius, cy - radius, radius * 2, radius * 2, radius, 1f, Theme.accent(0.25f));
        ui.round(cx - DEADZONE, cy - DEADZONE, DEADZONE * 2, DEADZONE * 2, DEADZONE, Theme.PANEL_BG);
        ui.outline(cx - DEADZONE, cy - DEADZONE, DEADZONE * 2, DEADZONE * 2, DEADZONE, 1f, Theme.accent(0.5f));

        if (n == 0) {
            ui.textCentered("Wheel is empty", cx, cy - 5, 10, Theme.FONT, Theme.TEXT_DIM);
            ui.textCentered("Open the Library to add emotes", cx, cy + 40f, 10, Theme.FONT_SMALL, Theme.TEXT_DIM);
        } else if (hovered != -1) {
            ui.textCentered(emotes.get(hovered).name(), cx, cy - 5, 10, 9f, Theme.TEXT);
            ui.textCentered(emotes.get(hovered).description(), cx, cy + radius + 26f, 10, Theme.FONT_SMALL, Theme.TEXT_DIM);
        } else {
            ui.textCentered("Select Emote", cx, cy - 5, 10, Theme.FONT, Theme.TEXT_DIM);
        }

        for (int i = 0; i < n; i++) {
            float h = hoverA[i].value();
            double a = i * seg - Math.PI / 2;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float x = cx + radius * ca;
            float y = cy + radius * sa;

            if (h > 0.01f) {
                ui.line(cx + ca * DEADZONE, cy + sa * DEADZONE, x - ca * 6f, y - sa * 6f, 1.5f,
                        Theme.accent(0.5f * h));
            }

            String name = emotes.get(i).name();
            float boxW = ui.textWidth(name, Theme.FONT) + 16f + 6f * h;
            float boxH = 22f + 2f * h;
            float bx = x - boxW / 2f, by = y - boxH / 2f;

            if (h > 0.01f) ui.glow(bx, by, boxW, boxH, 4f, 8f, Theme.accent(0.6f * h));
            ui.round(bx, by, boxW, boxH, 4f, ColorUtil.lerp(Theme.ROW, Theme.ROW_HOVER, h));
            ui.outline(bx, by, boxW, boxH, 4f, 1f + 0.5f * h,
                    ColorUtil.lerp(Theme.SETTING_BLOCK_BORDER, Theme.accent(), h));
            ui.textCentered(name, x, by, boxH, Theme.FONT, ColorUtil.lerp(Theme.TEXT_DIM, 0xFFFFFFFF, h));
        }

        drawButton("New Emote", newBtnX());
        drawButton("Library", libBtnX());

        ui.alpha = 1f;
    }

    // ------------------------------------------------------------------ input

    public void mouseClicked(int button) {
        if (button != Keys.MOUSE_BUTTON_LEFT) return;
        if (mode == Mode.LIBRARY) {
            library.mouseClicked();
            return;
        }
        if (ui.hovered(newBtnX(), btnY(), BTN_W, BTN_H)) {
            EmoteEditor.INSTANCE.openNew();
            return;
        }
        if (ui.hovered(libBtnX(), btnY(), BTN_W, BTN_H)) {
            showLibrary();
            return;
        }
        if (hovered != -1) select(hovered);
    }

    public void mouseScrolled(double amount) {
        if (mode == Mode.LIBRARY) library.mouseScrolled(amount);
    }

    public boolean keyPressed(int key) {
        EmoteModule m = EmoteModule.INSTANCE;
        if (mode == Mode.LIBRARY) {
            if (library.keyPressed(key)) return true;
            if (m != null && key == m.key) {
                close();
                return true;
            }
            return false;
        }
        if (key == Keys.KEY_ESCAPE || (m != null && key == m.key)) {
            close();
            return true;
        }
        int idx = key - Keys.KEY_1;
        if (idx >= 0 && idx < emotes.size()) {
            select(idx);
            return true;
        }
        return false;
    }

    private void select(int index) {
        EmotePlayer.play(emotes.get(index));
        close();
    }
}