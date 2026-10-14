package com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteEditor;

import com.OsamaClient.newbridge.Hacks.Player.Emotes.CustomEmote;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteLibrary;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteModule;
import com.OsamaClient.newbridge.Hacks.Player.Emotes.EmoteWheel;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.setting.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.TreeSet;

public final class EmoteEditor {

    private static final Logger LOG = LoggerFactory.getLogger("newbridge/EmoteEditor");
    public static final EmoteEditor INSTANCE = new EmoteEditor();

    public static final RenderStateDataKey<Boolean> PREVIEW_KEY =
            RenderStateDataKey.create(() -> "newbridge/emote_editor_preview");

    public enum Bone {
        ROOT, HEAD, BODY, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG,
        RIGHT_ELBOW, LEFT_ELBOW, RIGHT_KNEE, LEFT_KNEE, SPINE;

        public boolean isLower() {
            return this == RIGHT_ELBOW || this == LEFT_ELBOW || this == RIGHT_KNEE || this == LEFT_KNEE || this == SPINE;
        }
    }

    private static final int CHANNEL_COUNT = 6;
    private static final float EPSILON = 0.0005f;

    private static final float ROW_DY = 8f, ROW_BH = 22f;
    private static final float NAME_DX = 12f, NAME_W = 140f;
    private static final float DUR_MINUS_DX = 162f, DUR_PLUS_DX = 220f, DUR_BTN_W = 18f;
    private static final float LOOP_DX = 248f, LOOP_W = 50f;
    private static final float WHEEL_DX = 306f, WHEEL_W = 96f;

    private static final float MAX_PITCH = 25f;

    private static final float HDR_MIRROR_DX = 320f, HDR_AUTOKEY_DX = 420f, HDR_REDO_DX = 480f, HDR_UNDO_DX = 540f;

    private final UiRenderer renderer = new UiRenderer();
    private final Ui ui = new Ui(renderer);

    private Bone selectedBone = Bone.ROOT;
    private final Map<Bone, float[]> boneTransforms = new EnumMap<>(Bone.class);
    private final Map<Bone, NavigableMap<Float, float[]>> keyframes = new EnumMap<>(Bone.class);

    private float scrollOffset;
    private float targetScrollOffset;
    private float maxScroll;

    private float currentTime;
    private float duration = 2.0f;
    private boolean loop;
    private boolean isPlaying;

    private final TextField nameField = new TextField();
    private boolean addToWheel = true;
    private String editingName;
    private String status = "";
    private float statusTime;

    private int draggingSlider = -1;
    private boolean draggingTimeline;
    private boolean draggingPreview;
    private boolean draggingKeyframe;
    private double lastPreviewMouseX;
    private double lastPreviewMouseY;

    private Float selectedKeyframeTime = null;
    private final Map<Bone, float[]> copiedPose = new EnumMap<>(Bone.class);

    private float previewYaw = 180f;
    private float previewPitch = 8f;

    /** Auto-Key: Slider-Änderungen erzeugen/aktualisieren automatisch einen Keyframe an der Abspielposition. */
    private boolean autoKey = true;
    private boolean keyDragUndoPending;

    // Undo / Redo (Snapshots von Keyframes + Dauer)
    private static final int HISTORY_LIMIT = 64;
    private static final float SNAP_STEP = 0.05f;
    private final Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final Deque<Snapshot> redoStack = new ArrayDeque<>();

    private record Snapshot(Map<Bone, TreeMap<Float, float[]>> keys, float duration, Float selected) {}

    private long lastNanos;
    private int failures;

    private EmoteEditor() {
        for (Bone bone : Bone.values()) {
            boneTransforms.put(bone, new float[CHANNEL_COUNT]);
            keyframes.put(bone, new TreeMap<>());
        }
        nameField.maxLength = EmoteLibrary.NAME_MAX;
    }

    public void open() { openNew(); }

    public void openNew() {
        resetSession();
        duration = 2.0f;
        loop = false;
        addToWheel = true;
        editingName = null;
        setName(EmoteLibrary.uniqueName("My Emote", null));
        enter();
    }

    public void openEdit(CustomEmote c) {
        resetSession();
        duration = Math.max(0.5f, Math.min(10f, c.duration));
        loop = c.loop;
        editingName = c.name;
        addToWheel = EmoteLibrary.inWheel(c.name);
        setName(c.name);
        for (CustomEmote.Frame f : c.frames) {
            for (Map.Entry<String, float[]> e : f.bones.entrySet()) {
                float[] v = e.getValue();
                if (v == null || v.length < CHANNEL_COUNT) continue;
                try {
                    keyframes.get(Bone.valueOf(e.getKey())).put(quantizeTime(Math.min(f.time, duration)),
                            Arrays.copyOf(v, CHANNEL_COUNT));
                } catch (IllegalArgumentException ignored) {}
            }
        }
        evaluateAllBones();
        enter();
    }

    private void resetSession() {
        for (Bone bone : Bone.values()) {
            Arrays.fill(boneTransforms.get(bone), 0f);
            keyframes.get(bone).clear();
        }
        selectedBone = Bone.ROOT;
        selectedKeyframeTime = null;
        undoStack.clear();
        redoStack.clear();
        currentTime = 0f;
        isPlaying = false;
        status = "";
        statusTime = 0f;
    }

    private void enter() {
        Minecraft mc = Minecraft.getInstance();
        lastNanos = 0L;
        draggingSlider = -1;
        draggingTimeline = false;
        draggingPreview = false;
        draggingKeyframe = false;
        scrollOffset = 0f;
        targetScrollOffset = 0f;
        previewYaw = 180f;
        previewPitch = 8f;
        nameField.end();
        if (!(mc.gui.screen() instanceof EmoteEditorScreen)) mc.gui.setScreen(new EmoteEditorScreen());
    }

    public void close() {
        Minecraft.getInstance().gui.setScreen(null);
    }

    private void setName(String name) {
        nameField.end();
        nameField.text = name;
        nameField.cursor = name.length();
    }

    private void setStatus(String text) {
        status = text;
        statusTime = 3.5f;
    }

    public void frame() {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof EmoteEditorScreen)) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;

        renderer.begin();

        try {
            ui.scale = fittedScale();
            ui.dt = dt;
            ui.mouseX = UiInput.mouseFbX() / ui.scale;
            ui.mouseY = UiInput.mouseFbY() / ui.scale;
            ui.alpha = 1f;
            ui.hoverBlocked = false;

            statusTime = Math.max(0f, statusTime - dt);

            if (isPlaying) {
                currentTime += dt;
                if (currentTime > duration) {
                    if (loop) {
                        currentTime = 0f;
                    } else {
                        currentTime = duration; // ohne Loop am Ende stehen bleiben
                        isPlaying = false;
                    }
                }
                evaluateAllBones();
            }

            scrollOffset = lerp(scrollOffset, targetScrollOffset, Math.min(1f, dt * 18f));

            if (draggingSlider != -1) updateDraggedSlider();
            if (draggingTimeline) updateTimelineFromMouse();

            drawUI(dt);
            renderer.flush();
            UiFont.collect(600);
        } catch (RuntimeException e) {
            if (failures++ % 120 == 0) {
                LOG.error("EmoteEditor frame failed ({}x)", failures, e);
            }
            renderer.begin();
        }
    }

    public void extractPlayerPreview(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        var target = mc.gameRenderer.mainRenderTarget();
        float fbW = target.width;
        float fbH = target.height;

        // gleiche Geometrie wie drawPreviewViewport(), nur in Pixeln (Einheiten * Skalierung)
        float sc = fittedScale();
        float vx = 250f * sc;
        float vy = 52f * sc;
        float vw = Math.max(80f, fbW / sc - 260f) * sc;
        float vh = Math.max(80f, fbH / sc - 142f) * sc;

        double guiScale = mc.getWindow().getGuiScale();
        int x0 = (int) Math.round(vx / guiScale);
        int y0 = (int) Math.round(vy / guiScale);
        int x1 = (int) Math.round((vx + vw) / guiScale);
        int y1 = (int) Math.round((vy + vh) / guiScale);
        int size = Math.max(20, (int) Math.round(Math.min(vw, vh) * 0.42f / guiScale));

        float centerX = (x0 + x1) / 2f;
        float centerY = (y0 + y1) / 2f;
        float fakeMouseY = centerY - 40f * (float) Math.tan(previewPitch / 20f);

        try {
            InventoryScreen.extractEntityInInventoryFollowsMouse(
                    graphics,
                    x0, y0, x1, y1,
                    size,
                    0.0625F,
                    centerX,
                    fakeMouseY,
                    mc.player
            );
        } catch (RuntimeException e) {
            if (failures++ % 120 == 0) {
                LOG.error("EmoteEditor player preview failed", e);
            }
        }
    }

    public float[] getPreviewTransform(Bone bone) {
        return boneTransforms.get(bone);
    }

    public float previewBodyYaw() { return previewYaw + rootYaw(); }

    public float previewHeadYaw() { return -rootYaw(); }

    public float previewRootY() { return boneTransforms.get(Bone.ROOT)[4]; }

    public boolean isRotActive(Bone bone) { return groupActive(bone, 0); }

    public boolean isPosActive(Bone bone) { return groupActive(bone, 3); }

    private float rootYaw() { return boneTransforms.get(Bone.ROOT)[1]; }

    private boolean groupActive(Bone bone, int from) {
        float[] cur = boneTransforms.get(bone);
        for (int i = from; i < from + 3; i++) if (Math.abs(cur[i]) > 0.001f) return true;
        for (float[] frame : keyframes.get(bone).values()) {
            for (int i = from; i < from + 3; i++) if (Math.abs(frame[i]) > 0.001f) return true;
        }
        return false;
    }

    public boolean isPreviewState(EntityRenderState state) {
        return Boolean.TRUE.equals(((net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState) state).getData(PREVIEW_KEY));
    }

    private void drawUI(float dt) {
        ui.rect(0, 0, ui.width(), ui.height(), 0x3D0B0B0E);
        drawHeader();
        drawSidebar();
        drawPreviewViewport();
        drawTimeline();
    }

    private void drawHeader() {
        float w = ui.width();
        ui.rect(0, 0, w, 40, Theme.PANEL_BG);
        ui.line(0, 40, w, 40, 1f, Theme.SETTING_BLOCK_BORDER);
        ui.text("Emote Studio", 16f, 12f, 16f, Theme.FONT, Theme.TEXT);
        ui.text(String.format(Locale.ROOT, "Time: %.2fs / %.2fs", currentTime, duration),
                180f, 14f, 12f, Theme.FONT_SMALL, Theme.TEXT_DIM);

        drawButton(isPlaying ? "Pause" : "Play", w - 86f, 8f, 70f, 24f, true, false);
        drawButton("Save", w - 164f, 8f, 70f, 24f, true, false);
        drawButton("Library", w - 242f, 8f, 70f, 24f, true, false);
        drawButton("Mirror", w - HDR_MIRROR_DX, 8f, 70f, 24f, true, false);
        drawButton(autoKey ? "Auto Key: On" : "Auto Key: Off", w - HDR_AUTOKEY_DX, 8f, 92f, 24f, true, autoKey);
        drawButton("Redo", w - HDR_REDO_DX, 8f, 52f, 24f, !redoStack.isEmpty(), false);
        drawButton("Undo", w - HDR_UNDO_DX, 8f, 52f, 24f, !undoStack.isEmpty(), false);

        if (statusTime > 0f && !status.isEmpty()) {
            float avail = (w - HDR_UNDO_DX - 12f) - 300f;
            if (avail > 40f) {
                ui.text(ui.ellipsize(status, avail, Theme.FONT_SMALL), 300f, 14f, 12f, Theme.FONT_SMALL, Theme.accent());
            }
        }
    }

    private void drawSidebar() {
        float px = 10f, py = 52f, pw = 230f, ph = ui.height() - 142f;
        ui.round(px, py, pw, ph, 6f, Theme.PANEL_BG);
        ui.outline(px, py, pw, ph, 6f, 1f, Theme.SETTING_BLOCK_BORDER);

        ui.pushClip(px, py + 2f, pw, ph - 4f);

        float startY = py + 12f - scrollOffset;
        float currentY = startY;
        ui.text("Bones", px + 12f, currentY, 12f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        currentY += 20f;

        for (Bone bone : Bone.values()) {
            float btnH = 26f;
            if (currentY + btnH > py && currentY < py + ph) {
                boolean selected = bone == selectedBone;
                boolean hovered = ui.hovered(px + 10f, currentY, pw - 20f, btnH);
                int bg = selected ? Theme.accent(0.28f) : (hovered ? Theme.ROW_HOVER : Theme.ROW);
                int border = selected ? Theme.accent()
                        : (hovered ? Theme.accent(0.5f) : Theme.SETTING_BLOCK_BORDER);
                ui.round(px + 10f, currentY, pw - 20f, btnH, 4f, bg);
                ui.outline(px + 10f, currentY, pw - 20f, btnH, 4f, 1f, border);
                ui.text(bone.name(), px + 20f, currentY + 7f,
                        12f, Theme.FONT_SMALL, selected ? 0xFFFFFFFF : Theme.TEXT);
            }
            currentY += btnH + 4f;
        }

        currentY += 12f;
        ui.text("Transforms (" + selectedBone.name() + ")",
                px + 12f, currentY, 12f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        drawButton("Reset", px + pw - 62f, currentY - 4f, 50f, 18f, true, false);
        currentY += 22f;

        String[] labels = {"Rot X", "Rot Y", "Rot Z", "Pos X", "Pos Y", "Pos Z"};
        float[] vals = boneTransforms.get(selectedBone);
        for (int i = 0; i < labels.length; i++) {
            float sliderH = 32f;
            if (currentY + sliderH > py && currentY < py + ph) {
                drawSlider(px + 10f, currentY, pw - 20f, labels[i], vals[i], i);
            }
            currentY += sliderH + 8f;
        }

        ui.popClip();

        float totalHeight = currentY - startY;
        maxScroll = Math.max(0f, totalHeight - ph + 20f);
        targetScrollOffset = Math.max(0f, Math.min(targetScrollOffset, maxScroll));

        if (maxScroll > 0f) {
            float barH = Math.max(16f, (ph / (ph + maxScroll)) * ph);
            float barY = py + (scrollOffset / maxScroll) * (ph - barH);
            ui.round(px + pw - 4f, barY, 2f, barH, 1f, Theme.accent(0.5f));
        }
    }

    private void drawSlider(float x, float y, float w, String label, float val, int index) {
        ui.text(label, x, y, 12f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        ui.text(String.format(Locale.ROOT, "%.1f", val), x + w - 30f, y, 12f, Theme.FONT_SMALL, Theme.TEXT);

        float trackY = y + 16f;
        float trackH = 6f;
        ui.round(x, trackY, w, trackH, 3f, Theme.ROW);

        float min = index < 3 ? -180f : -10f;
        float max = index < 3 ? 180f : 10f;
        float pct = Math.max(0f, Math.min(1f, (val - min) / (max - min)));
        ui.round(x, trackY, w * pct, trackH, 3f, Theme.accent());
        ui.round(x + w * pct - 4f, trackY - 2f, 8f, 10f, 2f, 0xFFFFFFFF);
    }

    private void drawPreviewViewport() {
        float vx = 250f, vy = 52f;
        float vw = Math.max(80f, ui.width() - 260f);
        float vh = Math.max(80f, ui.height() - 142f);
        ui.round(vx, vy, vw, vh, 6f, 0x660F1014);
        ui.outline(vx, vy, vw, vh, 6f, 1f, Theme.SETTING_BLOCK_BORDER);

        ui.text("Drag to rotate  -  R resets the view  -  M mirror  -  Ctrl+Z/Y undo/redo", vx + 12f, vy + 10f, 11f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        ui.text("Sliders: right-click = 0, Ctrl+scroll = fine  -  Left/Right = prev/next key  -  Up/Down = bone  -  Shift = no snap",
                vx + 12f, vy + vh - 18f, 10f, Theme.FONT_SMALL, Theme.TEXT_DIM);
        ui.text(String.format(Locale.ROOT, "Yaw %.0f  Pitch %.0f", previewYaw - 180f, previewPitch),
                vx + 12f, vy + 25f, 10f, Theme.FONT_SMALL, Theme.TEXT_DIM);
    }

    private void drawTimeline() {
        float tx = 10f, ty = ui.height() - 80f;
        float tw = ui.width() - 20f, th = 70f;

        ui.round(tx, ty, tw, th, 6f, Theme.PANEL_BG);
        ui.outline(tx, ty, tw, th, 6f, 1f, Theme.SETTING_BLOCK_BORDER);

        float rowY = ty + ROW_DY;

        float nx = tx + NAME_DX;
        boolean nameHover = ui.hovered(nx, rowY, NAME_W, ROW_BH);
        ui.round(nx, rowY, NAME_W, ROW_BH, 4f, Theme.ROW);
        ui.outline(nx, rowY, NAME_W, ROW_BH, 4f, 1f,
                nameField.editing ? Theme.accent() : nameHover ? Theme.accent(0.5f) : Theme.SETTING_BLOCK_BORDER);
        nameField.draw(ui, nx + 6f, rowY + 3f, NAME_W - 12f, ROW_BH - 6f, Theme.FONT_SMALL, "Emote name", false);

        drawButton("-", tx + DUR_MINUS_DX, rowY, DUR_BTN_W, ROW_BH, true, false);
        ui.textCentered(String.format(Locale.ROOT, "%.2fs", duration),
                tx + (DUR_MINUS_DX + DUR_BTN_W + DUR_PLUS_DX) / 2f, rowY, ROW_BH, Theme.FONT_SMALL, Theme.TEXT);
        drawButton("+", tx + DUR_PLUS_DX, rowY, DUR_BTN_W, ROW_BH, true, false);

        drawButton("Loop", tx + LOOP_DX, rowY, LOOP_W, ROW_BH, true, loop);
        drawButton(addToWheel ? "In Wheel: On" : "In Wheel: Off", tx + WHEEL_DX, rowY, WHEEL_W, ROW_BH, true, addToWheel);

        drawButton("+ Keyframe", tx + tw - 110f, rowY, 100f, 22f, true, false);
        drawButton("Delete", tx + tw - 170f, rowY, 55f, 22f, selectedKeyframeTime != null, false);
        drawButton("Paste", tx + tw - 230f, rowY, 55f, 22f, !copiedPose.isEmpty(), false);
        drawButton("Copy", tx + tw - 285f, rowY, 50f, 22f, selectedKeyframeTime != null, false);

        float trackX = tx + 20f, trackY = ty + 42f, trackW = tw - 40f;
        ui.line(trackX, trackY, trackX + trackW, trackY, 2f, Theme.SETTING_BLOCK_BORDER);

        for (Map.Entry<Bone, NavigableMap<Float, float[]>> entry : keyframes.entrySet()) {
            for (Float time : entry.getValue().keySet()) {
                if (time.equals(selectedKeyframeTime)) continue;

                float p = duration <= 0f ? 0f : Math.max(0f, Math.min(1f, time / duration));
                float kx = trackX + trackW * p;
                boolean isCurrent = Math.abs(time - currentTime) < 0.01f;
                ui.round(kx - 3f, trackY - 3f, 6f, 6f, 2f, isCurrent ? Theme.accent() : Theme.TEXT_DIM);
            }
        }

        float progress = duration <= 0f ? 0f : Math.max(0f, Math.min(1f, currentTime / duration));
        float playheadX = trackX + trackW * progress;
        ui.line(playheadX, trackY - 10f, playheadX, trackY + 10f, 2f, Theme.accent());
        ui.round(playheadX - 4f, trackY - 14f, 8f, 8f, 4f, Theme.accent());

        if (selectedKeyframeTime != null) {
            float p = duration <= 0f ? 0f : Math.max(0f, Math.min(1f, selectedKeyframeTime / duration));
            float kx = trackX + trackW * p;
            ui.round(kx - 4f, trackY - 4f, 8f, 8f, 4f, 0xFFFFFFFF);
            ui.outline(kx - 4f, trackY - 4f, 8f, 8f, 4f, 1f, Theme.accent());
        }
    }

    private void drawButton(String text, float x, float y, float w, float h, boolean enabled, boolean active) {
        boolean hover = enabled && ui.hovered(x, y, w, h);
        int bg = !enabled ? 0x22FFFFFF : active ? Theme.accent(0.28f) : (hover ? Theme.ROW_HOVER : Theme.ROW);
        int border = !enabled ? 0x00000000
                : active ? Theme.accent() : (hover ? Theme.accent() : Theme.SETTING_BLOCK_BORDER);
        int textCol = enabled ? Theme.TEXT : Theme.TEXT_DIM;

        ui.round(x, y, w, h, 4f, bg);
        if (border != 0) ui.outline(x, y, w, h, 4f, 1f, border);
        ui.textCentered(text, x + w / 2f, y, h, Theme.FONT_SMALL, textCol);
    }

    /** Mindestbreite in UI-Einheiten, damit Kopfzeile und Timeline-Buttons sich nie überlappen. */
    private static final float MIN_UI_W = 860f, MIN_UI_H = 420f;

    /** Gewünschte Skalierung, aber klein genug, dass das Layout ins Fenster passt. */
    private float fittedScale() {
        float want = Theme.scale() * (float) EmoteModule.INSTANCE.wheelScale.getValue();
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        float fit = Math.min(target.width / MIN_UI_W, target.height / MIN_UI_H);
        return Math.max(0.5f, Math.min(want, fit));
    }

    private void setMouseFromScreen(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        double guiScale = mc.getWindow().getGuiScale();
        float currentScale = fittedScale();
        if (currentScale <= 0f) currentScale = 1f;

        ui.scale = currentScale;
        ui.mouseX = (float) (mouseX * guiScale / currentScale);
        ui.mouseY = (float) (mouseY * guiScale / currentScale);
    }

    public boolean mouseClicked(int button, double mouseX, double mouseY) {
        setMouseFromScreen(mouseX, mouseY);

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int ch = sliderAtMouse();
            if (ch >= 0) {
                pushUndo();
                applySliderValue(ch, 0f);
                return true;
            }
            return false;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;

        float w = ui.width();
        float tx = 10f, ty = ui.height() - 80f, tw = ui.width() - 20f;
        float rowY = ty + ROW_DY;

        if (ui.hovered(w - 86f, 8f, 70f, 24f)) { togglePlay(); return true; }
        if (ui.hovered(w - HDR_MIRROR_DX, 8f, 70f, 24f)) { mirrorPose(); return true; }
        if (ui.hovered(w - HDR_AUTOKEY_DX, 8f, 92f, 24f)) {
            autoKey = !autoKey;
            setStatus(autoKey ? "Auto Key on: slider changes are keyed automatically" : "Auto Key off");
            return true;
        }
        if (ui.hovered(w - HDR_REDO_DX, 8f, 52f, 24f)) { redo(); return true; }
        if (ui.hovered(w - HDR_UNDO_DX, 8f, 52f, 24f)) { undo(); return true; }
        if (ui.hovered(w - 164f, 8f, 70f, 24f)) { saveEmote(); return true; }
        if (ui.hovered(w - 242f, 8f, 70f, 24f)) {
            nameField.end();
            EmoteWheel.INSTANCE.openLibrary();
            return true;
        }

        boolean overName = ui.hovered(tx + NAME_DX, rowY, NAME_W, ROW_BH);
        if (!overName) nameField.end();
        if (overName) {
            if (!nameField.editing) nameField.begin(nameField.text);
            return true;
        }

        if (ui.hovered(tx + DUR_MINUS_DX, rowY, DUR_BTN_W, ROW_BH)) { pushUndo(); changeDuration(-0.25f); return true; }
        if (ui.hovered(tx + DUR_PLUS_DX, rowY, DUR_BTN_W, ROW_BH)) { pushUndo(); changeDuration(0.25f); return true; }
        if (ui.hovered(tx + LOOP_DX, rowY, LOOP_W, ROW_BH)) { loop = !loop; return true; }
        if (ui.hovered(tx + WHEEL_DX, rowY, WHEEL_W, ROW_BH)) { addToWheel = !addToWheel; return true; }

        float px = 10f, py = 52f, pw = 230f, ph = ui.height() - 142f;
        if (ui.mouseX >= px && ui.mouseX <= px + pw && ui.mouseY >= py && ui.mouseY <= py + ph) {
            float startY = py + 12f - scrollOffset + 20f;
            Bone[] bones = Bone.values();
            for (int i = 0; i < bones.length; i++) {
                float boneY = startY + i * 30f;
                if (ui.hovered(px + 10f, boneY, pw - 20f, 26f)) {
                    selectedBone = bones[i];
                    return true;
                }
            }

            // "Reset"-Button neben "Transforms"
            float resetY = startY + bones.length * 30f + 12f - 4f;
            if (ui.hovered(px + pw - 62f, resetY, 50f, 18f)) {
                pushUndo();
                for (int ch = 0; ch < CHANNEL_COUNT; ch++) applySliderValue(ch, 0f);
                setStatus("Reset " + selectedBone.name());
                return true;
            }

            int ch = sliderAtMouse();
            if (ch >= 0) {
                pushUndo();
                draggingSlider = ch;
                updateDraggedSlider();
                return true;
            }
        }

        float vx = 250f, vy = 52f;
        float vw = Math.max(80f, ui.width() - 260f);
        float vh = Math.max(80f, ui.height() - 142f);
        if (ui.hovered(vx, vy, vw, vh)) {
            draggingPreview = true;
            lastPreviewMouseX = ui.mouseX;
            lastPreviewMouseY = ui.mouseY;
            return true;
        }

        float trackX = tx + 20f, trackY = ty + 42f, trackW = tw - 40f;

        if (ui.hovered(tx + tw - 110f, rowY, 100f, 22f)) { pushUndo(); setKeyframe(); return true; }
        if (selectedKeyframeTime != null && ui.hovered(tx + tw - 170f, rowY, 55f, 22f)) { pushUndo(); deleteKeyframe(); return true; }
        if (!copiedPose.isEmpty() && ui.hovered(tx + tw - 230f, rowY, 55f, 22f)) { pushUndo(); pasteKeyframe(); return true; }
        if (selectedKeyframeTime != null && ui.hovered(tx + tw - 285f, rowY, 50f, 22f)) { copyKeyframe(); return true; }

        if (ui.mouseY >= trackY - 12f && ui.mouseY <= trackY + 12f) {
            Float hitTime = null;
            float bestDist = 10f;
            for (Bone bone : Bone.values()) {
                for (Float time : keyframes.get(bone).keySet()) {
                    float kx = trackX + trackW * (time / duration);
                    float dist = Math.abs(ui.mouseX - kx);
                    if (dist < bestDist) {
                        bestDist = dist;
                        hitTime = time;
                    }
                }
            }

            if (hitTime != null) {
                keyDragUndoPending = true; // Undo-Snapshot erst, wenn wirklich verschoben wird
                selectedKeyframeTime = hitTime;
                draggingKeyframe = true;
                currentTime = hitTime;
                evaluateAllBones();
                return true;
            }
        }

        if (ui.hovered(tx + 20f, ty + 30f, tw - 40f, 25f)) {
            draggingTimeline = true;
            selectedKeyframeTime = null;
            updateTimelineFromMouse();
            return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        setMouseFromScreen(mouseX, mouseY);

        if (draggingPreview) {
            updatePreviewOrbit();
            return true;
        }

        if (draggingSlider != -1) {
            updateDraggedSlider();
            return true;
        }

        if (draggingKeyframe && selectedKeyframeTime != null) {
            float tx = 10f, tw = ui.width() - 20f;
            float trackX = tx + 20f, trackW = tw - 40f;
            float pct = Math.max(0f, Math.min(1f, (ui.mouseX - trackX) / trackW));
            float raw = pct * duration;
            boolean free = UiInput.keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || UiInput.keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
            float newTime = quantizeTime(free ? raw : Math.round(raw / SNAP_STEP) * SNAP_STEP);
            newTime = Math.max(0f, Math.min(duration, newTime));

            // Nicht auf einen anderen Keyframe schieben (das hat ihn vorher stillschweigend überschrieben)
            if (Math.abs(newTime - selectedKeyframeTime) > 0.0001f && !isKeyTimeOccupied(newTime)) {
                if (keyDragUndoPending) { pushUndo(); keyDragUndoPending = false; }
                for (Bone bone : Bone.values()) {
                    NavigableMap<Float, float[]> map = keyframes.get(bone);
                    if (map.containsKey(selectedKeyframeTime)) {
                        float[] data = map.remove(selectedKeyframeTime);
                        map.put(newTime, data);
                    }
                }
                selectedKeyframeTime = newTime;
                currentTime = newTime;
                evaluateAllBones();
            }
            return true;
        }

        if (draggingTimeline) {
            updateTimelineFromMouse();
            return true;
        }

        return false;
    }

    public void mouseReleased(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggingSlider = -1;
            draggingTimeline = false;
            draggingPreview = false;
            draggingKeyframe = false;
        }
    }

    public void mouseScrolled(double mouseX, double mouseY, double delta) {
        setMouseFromScreen(mouseX, mouseY);
        float px = 10f, py = 52f, pw = 230f, ph = ui.height() - 142f;
        boolean ctrl = UiInput.keyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || UiInput.keyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
        int ch = ctrl ? sliderAtMouse() : -1;
        if (ch >= 0) {
            float step = ch < 3 ? 1f : 0.1f;
            boolean shift = UiInput.keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || UiInput.keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
            if (shift) step *= 10f;
            float min = ch < 3 ? -180f : -10f, max = ch < 3 ? 180f : 10f;
            float v = boneTransforms.get(selectedBone)[ch] + (float) Math.signum(delta) * step;
            pushUndo();
            applySliderValue(ch, Math.max(min, Math.min(max, v)));
            return;
        }
        if (ui.mouseX >= px && ui.mouseX <= px + pw && ui.mouseY >= py && ui.mouseY <= py + ph) {
            targetScrollOffset = Math.max(0f,
                    Math.min(targetScrollOffset - (float) delta * 24f, maxScroll));
        }
    }

    public boolean keyPressed(int key, int mods) {
        if (nameField.editing) {
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                nameField.end();
            } else {
                nameField.keyPressed(key, mods);
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_S && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
            saveEmote();
            return true;
        }
        if (key == GLFW.GLFW_KEY_Z && UiInput.ctrl(mods)) {
            if (UiInput.shift(mods)) redo(); else undo();
            return true;
        }
        if (key == GLFW.GLFW_KEY_Y && UiInput.ctrl(mods)) {
            redo();
            return true;
        }
        if (key == GLFW.GLFW_KEY_C && UiInput.ctrl(mods)) {
            copyKeyframe();
            return true;
        }
        if (key == GLFW.GLFW_KEY_V && UiInput.ctrl(mods)) {
            if (!copiedPose.isEmpty()) { pushUndo(); pasteKeyframe(); }
            return true;
        }
        if (key == GLFW.GLFW_KEY_M) {
            mirrorPose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT) {
            jumpToKeyframe(key == GLFW.GLFW_KEY_RIGHT);
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            Bone[] bones = Bone.values();
            int idx = selectedBone.ordinal() + (key == GLFW.GLFW_KEY_DOWN ? 1 : -1);
            selectedBone = bones[(idx + bones.length) % bones.length];
            return true;
        }
        if (key == GLFW.GLFW_KEY_SPACE) {
            togglePlay();
            return true;
        }
        if (key == GLFW.GLFW_KEY_K) {
            pushUndo();
            setKeyframe();
            return true;
        }
        if (key == GLFW.GLFW_KEY_R) {
            previewYaw = 180f;
            previewPitch = 8f;
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) {
            if (selectedKeyframeTime != null) { pushUndo(); deleteKeyframe(); }
            return true;
        }
        return false;
    }

    public void charTyped(int codepoint) {
        if (nameField.editing) nameField.charTyped(codepoint);
    }

    public void onScreenRemoved() {
        draggingSlider = -1;
        draggingTimeline = false;
        draggingPreview = false;
        draggingKeyframe = false;
        isPlaying = false;
        nameField.end();
        lastNanos = 0L;
    }

    private void togglePlay() {
        isPlaying = !isPlaying;
        if (isPlaying) selectedKeyframeTime = null;
    }

    private void changeDuration(float delta) {
        float oldDuration = duration;
        duration = Math.max(0.5f, Math.min(10f, Math.round((duration + delta) * 4f) / 4f));
        if (oldDuration <= 0f || Math.abs(oldDuration - duration) < 1.0E-4f) return;
        // Keyframes proportional mitskalieren. Vorher wurden sie auf die neue Dauer geclampt,
        // wodurch mehrere Keyframes auf derselben Zeit landeten und sich gegenseitig überschrieben.
        float scale = duration / oldDuration;
        for (Bone bone : Bone.values()) {
            NavigableMap<Float, float[]> map = keyframes.get(bone);
            TreeMap<Float, float[]> moved = new TreeMap<>();
            for (Map.Entry<Float, float[]> e : map.entrySet()) {
                moved.put(quantizeTime(Math.min(duration, e.getKey() * scale)), e.getValue());
            }
            map.clear();
            map.putAll(moved);
        }
        currentTime = Math.min(duration, currentTime * scale);
        if (selectedKeyframeTime != null) selectedKeyframeTime = quantizeTime(Math.min(duration, selectedKeyframeTime * scale));
    }

    private void saveEmote() {
        nameField.end();

        TreeSet<Float> times = new TreeSet<>();
        for (Bone bone : Bone.values()) times.addAll(keyframes.get(bone).keySet());
        if (times.isEmpty()) {
            setStatus("Add at least one keyframe before saving");
            return;
        }

        CustomEmote emote = new CustomEmote();
        emote.name = nameField.text;
        emote.duration = duration;
        emote.loop = loop;
        for (Float t : times) {
            CustomEmote.Frame frame = new CustomEmote.Frame();
            frame.time = t;
            for (Bone bone : Bone.values()) {
                float[] v = keyframes.get(bone).get(t);
                if (v != null) frame.bones.put(bone.name(), v.clone());
            }
            emote.frames.add(frame);
        }

        EmoteLibrary.SaveResult result = EmoteLibrary.saveCustom(emote, editingName, addToWheel);
        editingName = result.name();
        addToWheel = result.inWheel();
        setName(result.name());
        setStatus(result.message());
    }

    private void updateDraggedSlider() {
        if (draggingSlider < 0 || draggingSlider >= CHANNEL_COUNT) return;

        // Gleiche Geometrie wie drawSlider(): Spur beginnt bei px+10 und ist pw-20 breit
        // (vorher px+20 / pw-40 -> Knopf saß nicht unter der Maus).
        float px = 10f, pw = 230f;
        float min = draggingSlider < 3 ? -180f : -10f;
        float max = draggingSlider < 3 ? 180f : 10f;
        float pct = Math.max(0f, Math.min(1f,
                (ui.mouseX - (px + 10f)) / (pw - 20f)));
        float newValue = min + pct * (max - min);
        boolean fine = UiInput.keyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || UiInput.keyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
        float step = draggingSlider < 3 ? (fine ? 0.5f : 1f) : (fine ? 0.01f : 0.05f);
        newValue = Math.round(newValue / step) * step;

        applySliderValue(draggingSlider, newValue);
    }

    /** Setzt einen Kanal des gewählten Bones und schreibt ihn in den passenden Keyframe. */
    private void applySliderValue(int channel, float value) {
        boneTransforms.get(selectedBone)[channel] = value;

        Float keyTime = selectedKeyframeTime;
        if (keyTime == null) {
            Float onKey = keyAt(currentTime);
            if (onKey != null) keyTime = onKey;
            else if (autoKey) {
                setKeyframe(); // legt Keyframe für alle Bones an der Abspielposition an
                keyTime = selectedKeyframeTime;
            }
        }
        if (keyTime != null) {
            float[] data = keyframes.get(selectedBone).get(keyTime);
            if (data != null) {
                data[channel] = value;
            } else {
                keyframes.get(selectedBone).put(keyTime, boneTransforms.get(selectedBone).clone());
            }
        }
    }

    /** Index des Sliders unter der Maus, -1 wenn keiner. */
    private int sliderAtMouse() {
        float px = 10f, py = 52f, pw = 230f, ph = ui.height() - 142f;
        if (!(ui.mouseX >= px && ui.mouseX <= px + pw && ui.mouseY >= py && ui.mouseY <= py + ph)) return -1;
        float startY = py + 12f - scrollOffset + 20f;
        float sliderStartY = startY + Bone.values().length * 30f + 34f;
        for (int i = 0; i < CHANNEL_COUNT; i++) {
            if (ui.hovered(px + 10f, sliderStartY + i * 40f, pw - 20f, 32f)) return i;
        }
        return -1;
    }

    private Float keyAt(float time) {
        for (Bone bone : Bone.values()) {
            for (Float t : keyframes.get(bone).keySet()) {
                if (Math.abs(t - time) < 0.0015f) return t;
            }
        }
        return null;
    }

    private boolean isKeyTimeOccupied(float time) {
        for (Bone bone : Bone.values()) {
            for (Float t : keyframes.get(bone).keySet()) {
                if (Math.abs(t - time) < 0.0005f && !t.equals(selectedKeyframeTime)) return true;
            }
        }
        return false;
    }

    private void jumpToKeyframe(boolean forward) {
        TreeSet<Float> times = new TreeSet<>();
        for (Bone bone : Bone.values()) times.addAll(keyframes.get(bone).keySet());
        if (times.isEmpty()) return;
        Float next = forward ? times.higher(currentTime + 0.0005f) : times.lower(currentTime - 0.0005f);
        if (next == null) next = forward ? times.first() : times.last();
        isPlaying = false;
        currentTime = next;
        selectedKeyframeTime = next;
        evaluateAllBones();
    }

    // ── Mirror ──────────────────────────────────────────────────────────────

    private static Bone mirrorOf(Bone b) {
        return switch (b) {
            case RIGHT_ARM -> Bone.LEFT_ARM;
            case LEFT_ARM -> Bone.RIGHT_ARM;
            case RIGHT_LEG -> Bone.LEFT_LEG;
            case LEFT_LEG -> Bone.RIGHT_LEG;
            case RIGHT_ELBOW -> Bone.LEFT_ELBOW;
            case LEFT_ELBOW -> Bone.RIGHT_ELBOW;
            case RIGHT_KNEE -> Bone.LEFT_KNEE;
            case LEFT_KNEE -> Bone.RIGHT_KNEE;
            default -> b;
        };
    }

    /** Spiegelung an der Körpermitte: Rot Y/Z und Pos X negieren. */
    private static float[] mirrorValues(float[] v) {
        float[] m = v.clone();
        m[1] = -m[1];
        m[2] = -m[2];
        m[3] = -m[3];
        return m;
    }

    /** Spiegelt die Pose links/rechts – im ausgewählten Keyframe bzw. in der aktuellen Pose. */
    private void mirrorPose() {
        pushUndo();
        Float keyTime = selectedKeyframeTime != null ? selectedKeyframeTime : keyAt(currentTime);

        Map<Bone, float[]> src = new EnumMap<>(Bone.class);
        for (Bone b : Bone.values()) src.put(b, boneTransforms.get(b).clone());
        for (Bone b : Bone.values()) {
            float[] mirrored = mirrorValues(src.get(mirrorOf(b)));
            System.arraycopy(mirrored, 0, boneTransforms.get(b), 0, CHANNEL_COUNT);
        }
        if (keyTime != null) {
            for (Bone b : Bone.values()) keyframes.get(b).put(keyTime, boneTransforms.get(b).clone());
        } else if (autoKey) {
            setKeyframe();
        }
        setStatus("Pose mirrored");
    }

    // ── Undo / Redo ─────────────────────────────────────────────────────────

    private Snapshot snapshot() {
        Map<Bone, TreeMap<Float, float[]>> copy = new EnumMap<>(Bone.class);
        for (Bone b : Bone.values()) {
            TreeMap<Float, float[]> m = new TreeMap<>();
            for (Map.Entry<Float, float[]> e : keyframes.get(b).entrySet()) m.put(e.getKey(), e.getValue().clone());
            copy.put(b, m);
        }
        return new Snapshot(copy, duration, selectedKeyframeTime);
    }

    private void restore(Snapshot snap) {
        for (Bone b : Bone.values()) {
            NavigableMap<Float, float[]> map = keyframes.get(b);
            map.clear();
            for (Map.Entry<Float, float[]> e : snap.keys().get(b).entrySet()) map.put(e.getKey(), e.getValue().clone());
        }
        duration = snap.duration();
        selectedKeyframeTime = snap.selected();
        currentTime = Math.min(currentTime, duration);
        evaluateAllBones();
    }

    /** Vor jeder Änderung aufrufen. */
    private void pushUndo() {
        undoStack.push(snapshot());
        while (undoStack.size() > HISTORY_LIMIT) undoStack.removeLast();
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) { setStatus("Nothing to undo"); return; }
        redoStack.push(snapshot());
        restore(undoStack.pop());
        setStatus("Undo");
    }

    private void redo() {
        if (redoStack.isEmpty()) { setStatus("Nothing to redo"); return; }
        undoStack.push(snapshot());
        restore(redoStack.pop());
        setStatus("Redo");
    }

    private void updateTimelineFromMouse() {
        float tx = 10f, tw = ui.width() - 20f;
        float trackX = tx + 20f, trackW = tw - 40f;
        float pct = Math.max(0f, Math.min(1f,
                (ui.mouseX - trackX) / trackW));
        currentTime = pct * duration;

        evaluateAllBones();
    }

    private void updatePreviewOrbit() {
        double dx = ui.mouseX - lastPreviewMouseX;
        double dy = ui.mouseY - lastPreviewMouseY;
        lastPreviewMouseX = ui.mouseX;
        lastPreviewMouseY = ui.mouseY;

        previewYaw -= (float) dx * 0.65f;
        previewPitch += (float) dy * 0.30f;
        previewPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, previewPitch));
        while (previewYaw > 540f) previewYaw -= 360f;
        while (previewYaw < -180f) previewYaw += 360f;
    }

    private void setKeyframe() {
        float t = quantizeTime(currentTime);
        for (Bone bone : Bone.values()) {
            keyframes.get(bone).put(t, boneTransforms.get(bone).clone());
        }
        selectedKeyframeTime = t;
    }

    private void deleteKeyframe() {
        if (selectedKeyframeTime != null) {
            for (Bone bone : Bone.values()) {
                keyframes.get(bone).remove(selectedKeyframeTime);
            }
            selectedKeyframeTime = null;
        }
    }

    private void copyKeyframe() {
        if (selectedKeyframeTime != null) {
            copiedPose.clear();
            for (Bone bone : Bone.values()) {
                float[] data = keyframes.get(bone).get(selectedKeyframeTime);
                if (data != null) {
                    copiedPose.put(bone, data.clone());
                }
            }
        }
    }

    private void pasteKeyframe() {
        if (!copiedPose.isEmpty()) {
            float t = quantizeTime(currentTime);
            for (Map.Entry<Bone, float[]> entry : copiedPose.entrySet()) {
                keyframes.get(entry.getKey()).put(t, entry.getValue().clone());
                System.arraycopy(entry.getValue(), 0, boneTransforms.get(entry.getKey()), 0, CHANNEL_COUNT);
            }
            selectedKeyframeTime = t;
        }
    }

    private void evaluateAllBones() {
        for (Bone bone : Bone.values()) {
            float[] evaluated = evaluateBone(bone, currentTime);
            if (evaluated != null) {
                System.arraycopy(evaluated, 0, boneTransforms.get(bone), 0, CHANNEL_COUNT);
            }
        }
    }

    private float[] evaluateBone(Bone bone, float time) {
        NavigableMap<Float, float[]> map = keyframes.get(bone);
        if (map == null || map.isEmpty()) return null;

        Map.Entry<Float, float[]> floor = map.floorEntry(time + EPSILON);
        Map.Entry<Float, float[]> ceil = map.ceilingEntry(time - EPSILON);

        if (floor == null) return map.firstEntry().getValue().clone();
        if (ceil == null) return map.lastEntry().getValue().clone();
        if (Math.abs(floor.getKey() - ceil.getKey()) < EPSILON) return floor.getValue().clone();

        float aTime = floor.getKey();
        float bTime = ceil.getKey();
        float f = Math.max(0f, Math.min(1f, (time - aTime) / (bTime - aTime)));
        f = f * f * (3f - 2f * f);
        float[] a = floor.getValue();
        float[] b = ceil.getValue();
        float[] out = new float[CHANNEL_COUNT];
        for (int i = 0; i < CHANNEL_COUNT; i++) out[i] = lerp(a[i], b[i], f);
        return out;
    }

    private float quantizeTime(float time) {
        return Math.round(time * 1000f) / 1000f;
    }

    private float lerp(float a, float b, float f) {
        return a + f * (b - a);
    }
}