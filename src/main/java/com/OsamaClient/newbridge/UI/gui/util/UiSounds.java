package com.OsamaClient.newbridge.UI.gui.util;

import com.OsamaClient.newbridge.UI.gui.ClickGuiModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/** Small UI feedback sounds (can be switched off / turned down in the ClickGui settings). */
public final class UiSounds {

    private static long lastTickNanos;

    private UiSounds() {}

    private static void play(SoundEvent sound, float pitch, float volume) {
        try {
            var m = ClickGuiModule.INSTANCE;
            if (m != null && !m.sounds.enabled) return;
            float v = volume * (m != null ? (float) m.soundVolume.getValue() : 1f);
            if (v <= 0.001f) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.getSoundManager() == null) return;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, v));
        } catch (RuntimeException ignored) {
            // sounds are optional, never break the GUI
        }
    }

    /** Neutral click. */
    public static void click() { play(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.35f); }

    /** Something got switched on / off (rising / falling pitch). */
    public static void toggle(boolean on) { play(SoundEvents.UI_BUTTON_CLICK.value(), on ? 1.45f : 0.8f, 0.4f); }

    /** Selecting an entry in a list. */
    public static void select(boolean on) { play(SoundEvents.NOTE_BLOCK_HAT.value(), on ? 1.7f : 1.2f, 0.45f); }

    /** Expanding / collapsing, opening a list. Rate-limited so fast scrolling doesn't machine-gun. */
    public static void tick() {
        long now = System.nanoTime();
        if (now - lastTickNanos < 40_000_000L) return;
        lastTickNanos = now;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.2f);
    }

    public static void open() { play(SoundEvents.UI_TOAST_IN, 1.5f, 0.12f); }

    public static void close() { play(SoundEvents.UI_TOAST_OUT, 1.5f, 0.12f); }
}
