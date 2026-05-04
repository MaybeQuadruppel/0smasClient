package com.qdrppl.newbridge.Hacks.Visual;

import com.qdrppl.newbridge.UI.components.*;
import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Comparator;
import java.util.List;

public class ModuleList extends Module {
    public static ModuleList instance;

    // ── Settings (defaults updated to match new palette) ──────────────────────
    private String side           = "Right";
    private double xOffset        = 4.0;
    private double yStart         = 4.0;
    private double moduleSpacing  = 2.0;

    // New palette defaults
    private int textColor         = 0xFFE8E8F0;   // C_TEXT
    private int backgroundColor   = 0xC01C1C2E;   // C_PANEL_BG
    private int accentColor       = 0xFF5B8DFA;   // C_ACCENT

    private boolean showBackground  = true;
    private boolean showAccentBar   = true;
    private boolean textShadow      = false;
    private boolean rainbow         = false;
    private boolean showCategories  = false;

    private String sortMode           = "Width";
    private double accentBarWidth     = 2.0;
    private double backgroundPaddingX = 4.0;
    private double backgroundPaddingY = 1.0;
    private double rainbowSpeed       = 3.0;

    private static float rainbowHue = 0f;


    private static final int C_PANEL_BG  = 0xC01C1C2E;
    private static final int C_ACCENT    = 0xFF5B8DFA;
    private static final int C_ENABLED   = 0xFF4ECEAA;
    private static final int C_TEXT      = 0xFFE8E8F0;
    private static final int C_SEPARATOR = 0xFF2A2A44;

    public ModuleList() {
        super("ArrayList", "Shows all active Modules in the Game as a List", Category.MISC);

        instance = this;

        this.settings.add(new ModeButton("Side", List.of("Right", "Left"), 0, val -> side = val));
        this.settings.add(new Slider("X Offset",        0.0,  50.0,  4.0, val -> xOffset       = val));
        this.settings.add(new Slider("Y Start",         0.0, 200.0,  4.0, val -> yStart         = val));
        this.settings.add(new Slider("Module Spacing",  0.0,  10.0,  2.0, val -> moduleSpacing  = val));
        this.settings.add(new ColorPicker("Text Color",       0xFFE8E8F0, val -> textColor       = val));
        this.settings.add(new ColorPicker("Background Color", 0xC01C1C2E, val -> backgroundColor = val));
        this.settings.add(new ColorPicker("Accent Color",     0xFF5B8DFA, val -> accentColor     = val));
        this.settings.add(new ToggleButton("Show Background", true,  val -> showBackground = val));
        this.settings.add(new ToggleButton("Show Accent Bar", true,  val -> showAccentBar  = val));
        this.settings.add(new ToggleButton("Text Shadow",     false, val -> textShadow     = val));
        this.settings.add(new ToggleButton("Rainbow",         false, val -> rainbow        = val));
        this.settings.add(new ToggleButton("Show Categories", false, val -> showCategories = val));
        this.settings.add(new ModeButton("Sort By",
                List.of("Width", "Alphabetical", "Category"), 0, val -> sortMode = val));
        this.settings.add(new Slider("Accent Bar Width",      1.0,  6.0,  2.0, val -> accentBarWidth      = val));
        this.settings.add(new Slider("Background Padding X",  0.0, 10.0,  4.0, val -> backgroundPaddingX  = val));
        this.settings.add(new Slider("Background Padding Y",  0.0, 10.0,  1.0, val -> backgroundPaddingY  = val));
        this.settings.add(new Slider("Rainbow Speed",         0.5, 10.0,  3.0, val -> rainbowSpeed        = val));
    }

    public static void draw(GuiGraphicsExtractor guiGraphics) {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null || !instance.enabled
                || mc.player == null
                || mc.options.hideGui
                || mc.getDebugOverlay().showDebugScreen()) {
            return;
        }

        if (instance.rainbow) {
            rainbowHue = (rainbowHue + (float) instance.rainbowSpeed * 0.001f) % 1.0f;
        }

        int screenW  = mc.getWindow().getGuiScaledWidth();
        int yOffset  = (int) instance.yStart;
        int padX     = (int) instance.backgroundPaddingX;
        int padY     = (int) instance.backgroundPaddingY;
        int accentW  = Math.max(1, (int) instance.accentBarWidth);
        boolean isRight = instance.side.equals("Right");

        List<Module> activeModules = ModuleManager.modules.stream()
                .filter(m -> m.enabled)
                .filter(m -> m != instance)
                .sorted(instance.buildComparator(mc))
                .toList();

        for (int i = 0; i < activeModules.size(); i++) {
            Module mod = activeModules.get(i);

            // Resolve colours (rainbow overrides user picks)
            int resolvedText   = instance.rainbow
                    ? hsvToArgb((rainbowHue + i * 0.05f) % 1.0f, 0.6f, 1f)
                    : instance.textColor;
            int resolvedAccent = instance.rainbow
                    ? hsvToArgb((rainbowHue + i * 0.05f) % 1.0f, 0.8f, 1f)
                    : instance.accentColor;

            String label      = instance.showCategories
                    ? "[" + mod.category.name() + "] " + mod.name
                    : mod.name;
            int labelW  = mc.font.width(label);
            int lineH   = mc.font.lineHeight;

            // X position
            int x = isRight
                    ? screenW - labelW - padX - (int) instance.xOffset
                    : (int) (instance.xOffset + padX);

            // Background row (rounded rect via Component helper)
            if (instance.showBackground) {
                int bgX, bgW;
                if (isRight) {
                    bgX = x - padX;
                    bgW = screenW - bgX;
                } else {
                    bgX = 0;
                    bgW = x + labelW + padX;
                }
                // rounded rect for the background strip
                Component.drawRoundedRect(guiGraphics,
                        bgX, yOffset - padY,
                        bgW, lineH + padY * 2,
                        instance.backgroundColor);
            }

            // Text
            guiGraphics.text(mc.font, label, x, yOffset, resolvedText, instance.textShadow);

            // Accent bar (sits at the screen edge, same side as the list)
            if (instance.showAccentBar) {
                if (isRight) {
                    guiGraphics.fill(screenW - accentW, yOffset - padY,
                            screenW, yOffset + lineH + padY,
                            resolvedAccent);
                } else {
                    guiGraphics.fill(0, yOffset - padY,
                            accentW, yOffset + lineH + padY,
                            resolvedAccent);
                }
            }

            yOffset += lineH + (int) instance.moduleSpacing;
        }
    }

    private Comparator<Module> buildComparator(Minecraft mc) {
        return switch (sortMode) {
            case "Alphabetical" -> Comparator.comparing(m -> m.name);
            case "Category"     -> Comparator.comparing((Module m) -> m.category.name())
                    .thenComparing(m -> m.name);
            default             -> (m1, m2) -> mc.font.width(m2.name) - mc.font.width(m1.name);
        };
    }

    private static int hsvToArgb(float h, float s, float v) {
        int rgb = java.awt.Color.HSBtoRGB(h, s, v);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }
}