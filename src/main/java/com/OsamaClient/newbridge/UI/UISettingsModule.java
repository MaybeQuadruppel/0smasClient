package com.OsamaClient.newbridge.UI; // TODO: an euer echtes Package anpassen

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;

import java.util.Arrays;
import java.util.List;

/**
 * Eigenes Modul für die ClickGUI, über das sich das Erscheinungsbild der
 * gesamten GUI live einstellen lässt (Theme, Schriftgröße, Spaltenbreite,
 * Sound, Animationen, ...). Einfach wie jedes andere Modul registrieren
 * (ModuleManager o. Ä.) und über die ClickGUI aufrufen (Rechtsklick -> Settings).
 *
 * WICHTIG: Der Konstruktor-Aufruf von {@code Module(...)} und
 * {@code Category.XXX} müssen an eure echte Module-Basisklasse angepasst
 * werden, falls sie von diesem Entwurf abweicht.
 */
public class UISettingsModule extends Module {

    /** Singleton-Zugriff, damit z. B. der Gear-Button in ClickGuiScreen
     *  direkt in dieses Modul springen kann, ohne über ModuleManager zu suchen. */
    private static UISettingsModule INSTANCE;

    public static UISettingsModule getInstance() {
        if (INSTANCE == null) INSTANCE = new UISettingsModule();
        return INSTANCE;
    }

    public UISettingsModule() {
        super("UI Settings", "Passt Aussehen und Verhalten der ClickGUI an",
                Category.MISC /* TODO: passende Kategorie eurer echten Category-Enum */);
        INSTANCE = this;

        // ── Theme ────────────────────────────────────────────────────────
        List<String> themeNames = Arrays.stream(Theme.values())
                .map(t -> t.displayName)
                .toList();

        settings.add(new ModeButton("Theme", themeNames, Theme.getActive().ordinal(),
                selected -> {
                    for (Theme t : Theme.values()) {
                        if (t.displayName.equals(selected)) {
                            Theme.setActive(t);
                            Sounds.themeChange();
                            break;
                        }
                    }
                }));

        // ── Custom-Theme-Farben ──────────────────────────────────────────
        // Bewusst NUR zwei Farb-Picker statt einem pro Palette-Slot: Border,
        // Border-Hover, Enabled und Keybind sind optisch ohnehin nur
        // Abwandlungen der Hauptfarbe (heller/dunkler), Text-Dim ist eine
        // abgedunkelte Textfarbe. Diese werden über Theme.applyCustomBase(...)
        // automatisch berechnet, statt dass man sie einzeln von Hand
        // aufeinander abstimmen muss. Jede Änderung schaltet automatisch auf
        // "Custom" um, damit der Effekt sofort sichtbar ist.
        settings.add(new ColorPicker("Custom: Hauptfarbe",
                Theme.CUSTOM.get(Theme.ColorSlot.ACCENT),
                c -> Theme.applyCustomBase(c, Theme.CUSTOM.get(Theme.ColorSlot.TEXT))));

        settings.add(new ColorPicker("Custom: Text",
                Theme.CUSTOM.get(Theme.ColorSlot.TEXT),
                c -> Theme.applyCustomBase(Theme.CUSTOM.get(Theme.ColorSlot.ACCENT), c)));

        // ── Layout / Schrift ─────────────────────────────────────────────
        // Presets für den schnellen Wechsel (setzt Font-Scale + Compact-Mode
        // gemeinsam auf getestete, zusammenpassende Werte).
        List<String> densities = List.of("Compact", "Standard", "Comfortable");
        int startDensityIdx = UISettings.compactMode ? 0
                : UISettings.fontScale > 1.05f ? 2
                : 1;

        settings.add(new ModeButton("UI Density", densities, startDensityIdx,
                selected -> {
                    switch (selected) {
                        case "Compact" -> UISettings.applyCompactPreset();
                        case "Comfortable" -> UISettings.applyLargePreset();
                        default -> UISettings.applyComfortablePreset();
                    }
                    Sounds.select();
                }));

        // Feinjustierung zusätzlich zu den Presets: EIN Regler statt getrennter
        // Slider für Schriftgröße und Breite. Schrift und Boxen/Spaltenbreite
        // skalieren dadurch immer gemeinsam im gleichen Verhältnis mit
        // (UISettings.setScale zieht fontScale proportional mit) – so kann
        // die Schrift nie größer wirken als ihre Box oder umgekehrt.
        settings.add(new Slider("Scale",
                UISettings.SCALE_MIN, UISettings.SCALE_MAX,
                UISettings.scale, 0.05,
                v -> UISettings.setScale(v.floatValue())));


        settings.add(new Slider("Column Width",
                0, UISettings.COLUMN_WIDTH_MAX,
                UISettings.customColumnWidth, 1.0,
                v -> UISettings.setCustomColumnWidth(v.intValue())));

        settings.add(new Slider("Panel Alpha",
                0, 255, UISettings.panelAlpha, 1.0,
                v -> UISettings.panelAlpha = v.intValue()));

        settings.add(new ToggleButton("Animations", UISettings.animationsEnabled,
                v -> UISettings.animationsEnabled = v));

        settings.add(new ToggleButton("Rounded Corners", UISettings.roundedCorners,
                v -> UISettings.roundedCorners = v));

        // ── Sound ────────────────────────────────────────────────────────
        settings.add(new ToggleButton("Sound Enabled", UISettings.soundEnabled,
                v -> {
                    UISettings.soundEnabled = v;
                    UISettings.applyToSounds();
                }));

        settings.add(new Slider("Sound Volume",
                0, 1, UISettings.soundVolume, 0.05,
                v -> {
                    UISettings.soundVolume = v.floatValue();
                    UISettings.applyToSounds();
                }));
    }
}