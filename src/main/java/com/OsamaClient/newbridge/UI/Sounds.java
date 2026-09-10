package com.OsamaClient.newbridge.UI;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Zentrale Sound-Bank für die ClickGUI. Bündelt alle bisher verstreuten
 * {@code ClickGuiScreen.playGuiSound(...)}-Aufrufe an einer Stelle und
 * gibt jeder Aktion (Hover, Toggle, Öffnen, Fehler, ...) ein eigenes,
 * klar unterscheidbares Klangbild statt überall denselben Klick.
 *
 * ClickGuiScreen.playGuiSound(pitch, volume) bleibt als dünner Wrapper
 * um Sounds.click(...) bestehen, damit bestehender Code (ToggleButton,
 * ModeButton, ...) nicht überall angepasst werden muss.
 */
public final class Sounds {

    private Sounds() {}

    /** Globale Lautstärke aus den UI-Settings (0.0 - 1.0). */
    public static float masterVolume = 1.0f;
    public static boolean enabled = true;

    private static void playEvent(SoundEvent event, float pitch, float volume) {
        if (!enabled || volume <= 0f) return;
        try {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(event, pitch, volume * masterVolume));
        } catch (Exception ignored) {}
    }

    // ── Generischer Klick (Fallback / Kompatibilität mit playGuiSound) ──────
    public static void click(float pitch, float volume) {
        playEvent(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume);
    }

    // ── Toggles ───────────────────────────────────────────────────────────
    public static void toggleOn()  { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 1.25f, 0.30f); }
    public static void toggleOff() { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 0.80f, 0.28f); }

    // ── Hover (sehr leise, hoher Pitch, damit es nicht nervt) ────────────────
    public static void hover() { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f, 0.07f); }

    // ── Panels / Fenster ──────────────────────────────────────────────────
    public static void open()  { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 1.5f, 0.32f); }
    public static void close() { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 0.75f, 0.30f); }

    // ── Auswahl / Selektion (Dropdown-Eintrag, Modus, Theme) ────────────────
    public static void select()      { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.28f); }
    public static void deselect()    { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 0.88f, 0.24f); }

    // ── Fehler / Erfolg ───────────────────────────────────────────────────
    public static void error()   { playEvent(SoundEvents.VILLAGER_NO, 1.0f, 0.30f); }
    public static void success() { playEvent(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.22f); }

    // ── Theme-Wechsel: kleiner "Chime" statt normalem Klick ──────────────────
    public static void themeChange() { playEvent(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.25f); }

    // ── Kontinuierliches Feedback (Drag auf Slider / Hue-Bar) ────────────────
    /** pct: 0.0 - 1.0, steuert die Tonhöhe -> je weiter rechts, desto höher. */
    public static void drag(float pct) {
        float p = Math.max(0f, Math.min(1f, pct));
        playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 0.8f + p * 0.8f, 0.08f);
    }

    public static void scroll() { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.10f); }

    public static void keybind() { playEvent(SoundEvents.UI_BUTTON_CLICK.value(), 1.3f, 0.30f); }
}