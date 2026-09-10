package com.OsamaClient.newbridge.UI;

import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;

import java.util.Arrays;
import java.util.List;

/**
 * Einstellungen für die ClickGUI.
 */
public class UISettingsModule extends Module {

    private static UISettingsModule INSTANCE;

    public static UISettingsModule getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new UISettingsModule();
        }
        return INSTANCE;
    }

    public UISettingsModule() {
        super(
                "UI Settings",
                "ClickGUI appearance and layout settings",
                Category.MISC
        );

        INSTANCE = this;

        // ====================================================================
        // THEME
        // ====================================================================

        List<String> themeNames = Arrays.stream(Theme.values())
                .map(t -> t.displayName)
                .toList();

        ModeButton themeBtn = new ModeButton(
                "Theme",
                themeNames,
                Theme.getActive().ordinal(),
                selected -> {
                    for (Theme theme : Theme.values()) {
                        if (theme.displayName.equals(selected)) {
                            Theme.setActive(theme);
                            Sounds.themeChange();
                            break;
                        }
                    }
                }
        );
        themeBtn.setDescription("Selects the active color theme for the entire GUI.");
        settings.add(themeBtn);

        // ====================================================================
        // CUSTOM COLORS
        // ====================================================================

        ColorPicker customAccent = new ColorPicker(
                "Custom: Accent Color",
                Theme.CUSTOM.get(Theme.ColorSlot.ACCENT),
                color -> {
                    Theme.applyCustomBase(color, Theme.CUSTOM.get(Theme.ColorSlot.TEXT));
                    Theme.setActive(Theme.CUSTOM);
                }
        );
        customAccent.setDescription("Sets the primary accent color and background tint when using Custom Theme.");
        settings.add(customAccent);

        ColorPicker customText = new ColorPicker(
                "Custom: Text Color",
                Theme.CUSTOM.get(Theme.ColorSlot.TEXT),
                color -> {
                    Theme.applyCustomBase(Theme.CUSTOM.get(Theme.ColorSlot.ACCENT), color);
                    Theme.setActive(Theme.CUSTOM);
                }
        );
        customText.setDescription("Sets the primary text color when using Custom Theme.");
        settings.add(customText);

        // ====================================================================
        // UI DENSITY
        // ====================================================================

        List<String> densities = List.of("Compact", "Standard", "Comfortable");
        int startDensity = UISettings.compactMode ? 0 : UISettings.fontScale > 1.05f ? 2 : 1;

        ModeButton densityBtn = new ModeButton(
                "UI Density",
                densities,
                startDensity,
                selected -> {
                    switch (selected) {
                        case "Compact" -> UISettings.applyCompactPreset();
                        case "Comfortable" -> UISettings.applyLargePreset();
                        default -> UISettings.applyComfortablePreset();
                    }
                    Sounds.select();
                }
        );
        densityBtn.setDescription("Adjusts spacing, font scaling, and element padding presets.");
        settings.add(densityBtn);

        // ====================================================================
        // GLOBAL SCALE
        // ====================================================================

        Slider scaleSlider = new Slider(
                "Scale",
                UISettings.SCALE_MIN,
                UISettings.SCALE_MAX,
                UISettings.scale,
                0.05,
                value -> UISettings.setScale(value.floatValue())
        );
        scaleSlider.setDescription("Globally resizes the ClickGUI window and scale factor.");
        settings.add(scaleSlider);

        // ====================================================================
        // SIDEBAR WIDTH
        // ====================================================================

        Slider sidebarWidthSlider = new Slider(
                "Sidebar Width",
                UISettings.SIDEBAR_WIDTH_MIN,
                UISettings.SIDEBAR_WIDTH_MAX,
                UISettings.sidebarWidthScale,
                0.05,
                value -> UISettings.setSidebarWidthScale(value.floatValue())
        );
        sidebarWidthSlider.setDescription("Adjusts the horizontal width of the left sidebar.");
        settings.add(sidebarWidthSlider);

        // ====================================================================
        // MODULE WIDTH
        // ====================================================================

        Slider moduleWidthSlider = new Slider(
                "Module Width",
                UISettings.COLUMN_WIDTH_MIN,
                UISettings.COLUMN_WIDTH_MAX,
                UISettings.columnWidthScale,
                0.05,
                value -> UISettings.setCustomColumnWidth(value.floatValue())
        );
        moduleWidthSlider.setDescription("Adjusts the width of module cards in the grid.");
        settings.add(moduleWidthSlider);

        // ====================================================================
        // MODULE GAP
        // ====================================================================

        Slider moduleGapSlider = new Slider(
                "Module Gap",
                UISettings.MODULE_GAP_MIN,
                UISettings.MODULE_GAP_MAX,
                UISettings.moduleGapScale,
                0.05,
                value -> UISettings.setModuleGapScale(value.floatValue())
        );
        moduleGapSlider.setDescription("Adjusts the spacing gap between individual module cards.");
        settings.add(moduleGapSlider);

        // ====================================================================
        // PANEL ALPHA
        // ====================================================================

        Slider panelAlphaSlider = new Slider(
                "Panel Alpha",
                0,
                255,
                UISettings.panelAlpha,
                1.0,
                value -> UISettings.panelAlpha = value.intValue()
        );
        panelAlphaSlider.setDescription("Controls background panel transparency level (0 = transparent, 255 = opaque).");
        settings.add(panelAlphaSlider);

        // ====================================================================
        // ANIMATIONS
        // ====================================================================

        ToggleButton animationsToggle = new ToggleButton(
                "Animations",
                UISettings.animationsEnabled,
                value -> UISettings.animationsEnabled = value
        );
        animationsToggle.setDescription("Enables or disables hover fade and transition animations.");
        settings.add(animationsToggle);

        // ====================================================================
        // ROUNDED CORNERS
        // ====================================================================

        ToggleButton roundedToggle = new ToggleButton(
                "Rounded Corners",
                UISettings.roundedCorners,
                value -> UISettings.roundedCorners = value
        );
        roundedToggle.setDescription("Toggles rounded vs sharp edges on GUI panels and cards.");
        settings.add(roundedToggle);

        // ====================================================================
        // SOUND
        // ====================================================================

        ToggleButton soundToggle = new ToggleButton(
                "Sound Enabled",
                UISettings.soundEnabled,
                value -> {
                    UISettings.soundEnabled = value;
                    UISettings.applyToSounds();
                }
        );
        soundToggle.setDescription("Enables audio feedback for UI clicks and element hovers.");
        settings.add(soundToggle);

        Slider soundVolumeSlider = new Slider(
                "Sound Volume",
                0,
                1,
                UISettings.soundVolume,
                0.05,
                value -> {
                    UISettings.soundVolume = value.floatValue();
                    UISettings.applyToSounds();
                }
        );
        soundVolumeSlider.setDescription("Adjusts the volume level of UI sound effects.");
        settings.add(soundVolumeSlider);
    }
}