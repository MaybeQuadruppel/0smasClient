package com.OsamaClient.newbridge.UI;

import com.OsamaClient.newbridge.Config;
import com.OsamaClient.newbridge.UI.components.*;
import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 0samaClient ClickGUI
 */
public class ClickGuiScreen extends Screen {

    // ========================================================================
    // STATE
    // ========================================================================

    private Module selectedModule = null;
    private Module.Category selectedCategory = null;
    private boolean profilesSelected = false;

    private boolean searchActive = false;
    private String searchQuery = "";

    private String bindingModule = null;

    private long openTimeMs = -1L;

    private double categoryScroll = 0.0;
    private double moduleScroll = 0.0;
    private double settingsScroll = 0.0;

    private int categoryMaxScroll = 0;
    private int moduleMaxScroll = 0;
    private int settingsMaxScroll = 0;

    private String lastHoveredModule = null;
    private String lastHoveredSidebarItem = null;

    // ========================================================================
    // SORTING & FILTERING
    // ========================================================================

    private enum SortMode { DEFAULT, A_Z, ACTIVE, BINDS }
    private SortMode currentSortMode = SortMode.DEFAULT;
    private float sortHover = 0f;

    // ========================================================================
    // KEYBINDS
    // ========================================================================

    public static final Map<String, Integer> keybinds = new HashMap<>();

    // ========================================================================
    // ANIMATION
    // ========================================================================

    private final Map<String, Float> moduleHover = new HashMap<>();
    private final Map<Module.Category, Float> categoryHover = new EnumMap<>(Module.Category.class);

    private float settingsHover = 0f;
    private float profileHover = 0f;
    private float backHover = 0f;

    private static final long FADE_MS = 180L;

    // ========================================================================
    // LAYOUT
    // ========================================================================

    private static final int BASE_MARGIN = 10;
    private static final int BASE_SIDEBAR_W = 104;
    private static final int BASE_HEADER_H = 32;
    private static final int BASE_MODULE_H = 30;
    private static final int BASE_MODULE_GAP = 5;
    private static final int BASE_CONTENT_PAD = 10;
    private static final int BASE_SETTINGS_TOP = 42;

    // ========================================================================
    // THEME COLORS
    // ========================================================================

    private static int C_OVERLAY = 0x99000000;
    private static int C_WINDOW = 0xF20A0A0A;
    private static int C_WINDOW_BOTTOM = 0xF2060606;
    private static int C_HEADER = 0xFF111111;
    private static int C_SIDEBAR = 0xF20C0C0C;
    private static int C_SIDEBAR_HOVER = 0xFF171717;
    private static int C_CARD = 0xF2111111;
    private static int C_CARD_HOVER = 0xFF191919;
    private static int C_SEPARATOR = 0xFF292929;
    private static int C_TEXT = 0xFFEDEDED;
    private static int C_TEXT_DIM = 0xFF737373;
    private static int C_ACCENT = 0xFFFFFFFF;
    private static int C_ACCENT_DIM = 0xFFAAAAAA;
    private static int C_ENABLED = 0xFFFFFFFF;
    private static int C_DISABLED = 0xFF555555;
    private static int C_KEYBIND = 0xFF999999;

    private static void updateThemeColors() {
        Theme.Palette p = Theme.getActive().palette;

        int accent = p.accent | 0xFF000000;
        int baseBg = p.bg | 0xFF000000;

        int tintedBg = UISettings.getTintedBackground(baseBg, accent, 0.12f);
        int tintedBgDark = UISettings.getTintedBackground(Theme.darken(baseBg, 0.35f), accent, 0.08f);

        C_OVERLAY = 0x00000000;

        C_WINDOW = UISettings.withPanelAlpha(tintedBg);
        C_WINDOW_BOTTOM = UISettings.withPanelAlpha(tintedBgDark);
        C_HEADER = UISettings.withPanelAlpha(UISettings.getTintedBackground(p.bgHover | 0xFF000000, accent, 0.15f));
        C_SIDEBAR = UISettings.withPanelAlpha(UISettings.getTintedBackground(Theme.darken(baseBg, 0.15f), accent, 0.10f));
        C_SIDEBAR_HOVER = UISettings.withPanelAlpha(UISettings.getTintedBackground(p.bgHover | 0xFF000000, accent, 0.20f));
        C_CARD = UISettings.withPanelAlpha(UISettings.getTintedBackground(Theme.darken(baseBg, 0.08f), accent, 0.08f));
        C_CARD_HOVER = UISettings.withPanelAlpha(UISettings.getTintedBackground(p.bgHover | 0xFF000000, accent, 0.18f));

        C_SEPARATOR = p.border | 0xFF000000;
        C_TEXT = p.text | 0xFF000000;
        C_TEXT_DIM = p.textDim | 0xFF000000;
        C_ACCENT = accent;
        C_ACCENT_DIM = Theme.darken(accent, 0.35f);
        C_ENABLED = p.enabled | 0xFF000000;
        C_DISABLED = p.disabled | 0xFF000000;
        C_KEYBIND = p.keybind | 0xFF000000;
    }

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================

    public ClickGuiScreen() {
        super(net.minecraft.network.chat.Component.literal(""));
    }

    // ========================================================================
    // SCALE HELPERS
    // ========================================================================

    private int s(int value) {
        return UISettings.scaled(value);
    }

    private int sidebarWidth() {
        return Math.max(s(105), Math.round(s(BASE_SIDEBAR_W) * UISettings.sidebarWidthScale));
    }

    private int headerHeight() {
        return s(BASE_HEADER_H);
    }

    private int moduleHeight() {
        return s(UISettings.compactMode ? 27 : BASE_MODULE_H);
    }

    private int moduleGap() {
        return Math.max(1, Math.round(s(UISettings.compactMode ? 3 : BASE_MODULE_GAP) * UISettings.moduleGapScale));
    }

    private int contentPadding() {
        return s(BASE_CONTENT_PAD);
    }

    private boolean isPickerComponent(Component c) {
        if (c == null) return false;
        String name = c.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.contains("picker") || name.contains("target") || name.contains("color");
    }

    private void drawBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (UISettings.roundedCorners) {
            Component.drawRoundedRect(g, x, y, w, h, color);
        } else {
            g.fill(x, y, x + w, y + h, color);
        }
    }

    private void drawOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (UISettings.roundedCorners) {
            Component.drawRoundedOutline(g, x, y, w, h, color);
        } else {
            g.fill(x, y, x + w, y + 1, color);
            g.fill(x, y + h - 1, x + w, y + h, color);
            g.fill(x, y, x + 1, y + h, color);
            g.fill(x + w - 1, y, x + w, y + h, color);
        }
    }

    // ========================================================================
    // SOUND
    // ========================================================================

    public static void playGuiSound(float pitch, float volume) {
        try {
            if (!UISettings.soundEnabled) {
                return;
            }
            float finalVolume = Math.max(0f, Math.min(1f, volume * UISettings.soundVolume));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, finalVolume));
        } catch (Exception ignored) {
        }
    }

    public static void playGuiSound(float pitch) {
        playGuiSound(pitch, 0.25f);
    }

    // ========================================================================
    // INIT
    // ========================================================================

    @Override
    public void init() {
        super.init();

        openTimeMs = System.currentTimeMillis();
        selectedCategory = getFirstCategory();
        selectedModule = null;
        profilesSelected = false;
        categoryScroll = 0;
        moduleScroll = 0;
        settingsScroll = 0;
        bindingModule = null;

        playGuiSound(1.35f, 0.28f);
    }

    // ========================================================================
    // CATEGORY LOGIC
    // ========================================================================

    private boolean isVisibleModule(Module module) {
        return !(module instanceof UISettingsModule);
    }

    private List<Module> getCategoryModules(Module.Category category) {
        return ModuleManager.getModulesByCategory(category).stream()
                .filter(this::isVisibleModule)
                .collect(Collectors.toList());
    }

    private boolean categoryHasModules(Module.Category category) {
        return !getCategoryModules(category).isEmpty();
    }

    private List<Module.Category> getVisibleCategories() {
        List<Module.Category> result = new ArrayList<>();
        for (Module.Category category : Module.Category.values()) {
            if (categoryHasModules(category)) {
                result.add(category);
            }
        }
        return result;
    }

    private Module.Category getFirstCategory() {
        for (Module.Category category : Module.Category.values()) {
            if (categoryHasModules(category)) {
                return category;
            }
        }
        return null;
    }

    private void selectCategory(Module.Category category) {
        if (category == null || !categoryHasModules(category)) {
            return;
        }

        selectedCategory = category;
        profilesSelected = false;
        selectedModule = null;
        moduleScroll = 0;
        searchActive = false;
        searchQuery = "";
        bindingModule = null;

        playGuiSound(1.05f, 0.20f);
    }

    // ========================================================================
    // FADE
    // ========================================================================

    private float rawFade() {
        if (openTimeMs < 0) return 1f;
        return Math.min(1f, (System.currentTimeMillis() - openTimeMs) / (float) FADE_MS);
    }

    private static float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }

    // ========================================================================
    // TEXT
    // ========================================================================

    private String trimToWidth(String text, int maxWidth) {
        if (text == null) return "";
        if (UISettings.textWidth(this.font, text) <= maxWidth) return text;

        String dots = "...";
        String result = text;

        while (!result.isEmpty() && UISettings.textWidth(this.font, result + dots) > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }

        return result.isEmpty() ? dots : result + dots;
    }

    // ========================================================================
    // MAIN RENDER
    // ========================================================================

    @Override
    public void extractRenderState(@NotNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        updateThemeColors();

        float fade = easeOut(rawFade());

        if (C_OVERLAY != 0) {
            g.fill(0, 0, this.width, this.height, Component.withAlpha(C_OVERLAY, fade));
        }

        if (selectedModule != null) {
            renderSettings(g, mouseX, mouseY);
        } else {
            renderMain(g, mouseX, mouseY, fade);
        }

        if (selectedModule == null) {
            renderSearch(g, mouseX, mouseY);
        }

        renderBindingPrompt(g);

        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    private int[] getWindowRect() {
        int margin = Math.max(s(BASE_MARGIN), 6);
        return new int[]{margin, margin, this.width - margin * 2, this.height - margin * 2};
    }

    // ========================================================================
    // MAIN
    // ========================================================================

    private void renderMain(GuiGraphicsExtractor g, int mouseX, int mouseY, float fade) {
        int[] window = getWindowRect();
        int wx = window[0], wy = window[1], ww = window[2], wh = window[3];

        Component.drawShadow(g, wx, wy, ww, wh);
        drawVerticalGradient(g, wx, wy, ww, wh, C_WINDOW, C_WINDOW_BOTTOM);

        int headerH = headerHeight();
        drawBox(g, wx, wy, ww, headerH, C_HEADER);

        int sidebarW = sidebarWidth();
        drawSidebar(g, wx, wy + headerH, sidebarW, wh - headerH, mouseX, mouseY);

        g.fill(wx + sidebarW, wy + headerH, wx + sidebarW + 1, wy + wh, C_SEPARATOR);

        renderHeader(g, wx, wy, ww, headerH, mouseX, mouseY);

        int contentX = wx + sidebarW + contentPadding();
        int contentY = wy + headerH + contentPadding();
        int contentW = ww - sidebarW - contentPadding() * 2;
        int contentH = wh - headerH - contentPadding() * 2;

        if (profilesSelected) {
            renderProfilesPlaceholder(g, contentX, contentY, contentW, contentH);
        } else {
            renderModules(g, contentX, contentY, contentW, contentH, mouseX, mouseY);
        }

        drawOutline(g, wx, wy, ww, wh, C_SEPARATOR);
    }

    // ========================================================================
    // HEADER
    // ========================================================================

    private void renderHeader(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        int logoX = x + s(10);
        int logoY = y + (height / 2) - s(4);

        UISettings.drawText(g, this.font, "0samaClient", logoX, logoY, C_TEXT, false);

        int hintW = 0;
        if (!searchActive) {
            String hint = "CTRL+F";
            hintW = UISettings.textWidth(this.font, hint) + s(10);
            UISettings.drawText(g, this.font, hint, x + width - hintW, logoY, C_TEXT_DIM, false);
        }

        // Sortier-Button mit Clipping-Schutz
        int sortBtnW = 0;
        if (!profilesSelected && selectedModule == null) {
            String sortText = "Sort: " + currentSortMode.name();
            sortBtnW = UISettings.textWidth(this.font, sortText) + s(16);
            int sx = x + width - (searchActive ? s(195) : hintW) - sortBtnW - s(10);
            int sy = y + (height - s(16)) / 2;

            boolean hovered = mouseX >= sx && mouseX <= sx + sortBtnW && mouseY >= sy && mouseY <= sy + s(16);
            sortHover = hovered ? Math.min(1f, sortHover + UISettings.animStep(0.2f)) : Math.max(0f, sortHover - UISettings.animStep(0.2f));

            drawBox(g, sx, sy, sortBtnW, s(16), Component.lerpColor(C_CARD, C_CARD_HOVER, sortHover));
            drawOutline(g, sx, sy, sortBtnW, s(16), hovered ? C_ACCENT : C_SEPARATOR);
            UISettings.drawText(g, this.font, sortText, sx + s(8), sy + s(8) - s(4), hovered ? C_TEXT : C_TEXT_DIM, false);
        }

        String categoryName = searchActive && !searchQuery.isBlank() ? "Search Results" : (profilesSelected ? "Profiles" : (selectedCategory != null ? selectedCategory.name() : "Dashboard"));
        int categoryX = logoX + UISettings.textWidth(this.font, "0samaClient") + s(8);

        int reservedRight = searchActive ? s(195) : hintW;
        reservedRight += sortBtnW + s(20);

        int maxCatWidth = Math.max(s(20), width - (categoryX - x) - reservedRight);

        String catText = trimToWidth("/ " + categoryName, maxCatWidth);
        UISettings.drawText(g, this.font, catText, categoryX, logoY, C_TEXT_DIM, false);

        g.fill(x, y + height - 1, x + width, y + height, C_SEPARATOR);
    }

    // ========================================================================
    // SIDEBAR
    // ========================================================================

    private void drawSidebar(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        drawBox(g, x, y, width, height, C_SIDEBAR);

        int itemH = s(UISettings.compactMode ? 24 : 27);
        int itemGap = s(3);

        int catStartY = y + s(10);
        int catAvailH = height - s(15);

        List<Module.Category> visibleCategories = getVisibleCategories();
        int categoriesH = visibleCategories.size() * (itemH + itemGap);
        int separatorH = s(22);
        int profilesH = itemH + itemGap;
        int settingsH = itemH;

        int totalSidebarH = categoriesH + separatorH + profilesH + settingsH + s(10);
        categoryMaxScroll = Math.max(0, totalSidebarH - catAvailH);
        categoryScroll = Math.max(0, Math.min(categoryScroll, categoryMaxScroll));

        g.enableScissor(x, catStartY, x + width, catStartY + catAvailH);

        int currentY = catStartY - (int) categoryScroll;
        String currentHoveredItem = null;

        for (Module.Category category : visibleCategories) {
            if (currentY + itemH >= catStartY && currentY <= catStartY + catAvailH) {
                boolean selected = !profilesSelected && selectedCategory == category && (!searchActive || searchQuery.isBlank());
                float hover = categoryHover.getOrDefault(category, 0f);

                int bx = x + s(5);
                int bw = width - s(10);
                boolean hovered = mouseX >= bx && mouseX <= bx + bw && mouseY >= currentY && mouseY <= currentY + itemH;

                if (hovered) {
                    currentHoveredItem = "cat_" + category.name();
                }

                hover = hovered ? Math.min(1f, hover + UISettings.animStep(0.16f)) : Math.max(0f, hover - UISettings.animStep(0.16f));
                categoryHover.put(category, hover);

                int bg = selected ? Component.lerpColor(C_SIDEBAR_HOVER, C_ACCENT, 0.13f) : Component.lerpColor(C_SIDEBAR, C_SIDEBAR_HOVER, hover);

                if (selected || hover > 0.01f) {
                    drawBox(g, bx, currentY, bw, itemH, bg);
                }

                if (selected) {
                    g.fill(bx, currentY + s(4), bx + s(2), currentY + itemH - s(4), C_ACCENT);
                }

                int iconX = bx + s(7);
                int textX = bx + s(22);
                int textY = currentY + (itemH / 2) - s(4);

                UISettings.drawText(g, this.font, categoryIcon(category), iconX, textY, selected ? C_ACCENT : C_TEXT_DIM, false);
                UISettings.drawText(g, this.font, trimToWidth(prettyCategoryName(category), bw - s(28)), textX, textY, selected ? C_TEXT : Component.lerpColor(C_TEXT_DIM, C_TEXT, hover), false);
            }
            currentY += itemH + itemGap;
        }

        if (currentY + separatorH >= catStartY && currentY <= catStartY + catAvailH) {
            int sepY = currentY + s(4);
            g.fill(x + s(10), sepY, x + width - s(10), sepY + 1, C_SEPARATOR);
            UISettings.drawText(g, this.font, "UI", x + s(10), sepY + s(6), C_TEXT_DIM, false);
        }
        currentY += separatorH;

        if (currentY + itemH >= catStartY && currentY <= catStartY + catAvailH) {
            int profileX = x + s(5);
            int profileW = width - s(10);
            boolean profileHovered = mouseX >= profileX && mouseX <= profileX + profileW && mouseY >= currentY && mouseY <= currentY + itemH;

            if (profileHovered) {
                currentHoveredItem = "profiles";
            }

            profileHover = profileHovered ? Math.min(1f, profileHover + UISettings.animStep(0.16f)) : Math.max(0f, profileHover - UISettings.animStep(0.16f));
            int profileBg = profilesSelected ? Component.lerpColor(C_SIDEBAR_HOVER, C_ACCENT, 0.13f) : Component.lerpColor(C_SIDEBAR, C_SIDEBAR_HOVER, profileHover);

            if (profilesSelected || profileHover > 0.01f) {
                drawBox(g, profileX, currentY, profileW, itemH, profileBg);
            }
            UISettings.drawText(g, this.font, "◉", profileX + s(7), currentY + (itemH / 2) - s(4), profilesSelected ? C_ACCENT : C_TEXT_DIM, false);
            UISettings.drawText(g, this.font, "Profiles", profileX + s(22), currentY + (itemH / 2) - s(4), profilesSelected ? C_TEXT : C_TEXT_DIM, false);
        }
        currentY += itemH + itemGap;

        if (currentY + itemH >= catStartY && currentY <= catStartY + catAvailH) {
            int settingsX = x + s(5);
            int settingsW = width - s(10);
            boolean settingsHovered = mouseX >= settingsX && mouseX <= settingsX + settingsW && mouseY >= currentY && mouseY <= currentY + itemH;

            if (settingsHovered) {
                currentHoveredItem = "settings";
            }

            settingsHover = settingsHovered ? Math.min(1f, settingsHover + UISettings.animStep(0.18f)) : Math.max(0f, settingsHover - UISettings.animStep(0.18f));
            int settingsBg = Component.lerpColor(C_SIDEBAR, C_SIDEBAR_HOVER, settingsHover);

            if (settingsHovered || settingsHover > 0.01f) {
                drawBox(g, settingsX, currentY, settingsW, itemH, settingsBg);
            }
            UISettings.drawText(g, this.font, "⚙", settingsX + s(7), currentY + (itemH / 2) - s(4), settingsHovered ? C_ACCENT : C_TEXT_DIM, false);
            UISettings.drawText(g, this.font, "Settings", settingsX + s(22), currentY + (itemH / 2) - s(4), settingsHovered ? C_TEXT : C_TEXT_DIM, false);
        }

        g.disableScissor();

        if (currentHoveredItem != null && !currentHoveredItem.equals(lastHoveredSidebarItem)) {
            playGuiSound(1.75f, 0.08f);
            lastHoveredSidebarItem = currentHoveredItem;
        } else if (currentHoveredItem == null) {
            lastHoveredSidebarItem = null;
        }

        if (categoryMaxScroll > 0) {
            renderScrollbar(g, x + width - s(3), catStartY, s(2), catAvailH, totalSidebarH, categoryScroll, categoryMaxScroll);
        }
    }

    private String categoryIcon(Module.Category category) {
        return switch (category.name().toUpperCase(Locale.ROOT)) {
            case "COMBAT" -> "⚔";
            case "MOVEMENT" -> "↔";
            case "VISUAL" -> "◉";
            case "MISC" -> "⚙";
            case "DONUT" -> "◈";
            default -> "•";
        };
    }

    private String prettyCategoryName(Module.Category category) {
        String name = category.name().toLowerCase(Locale.ROOT);
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // ========================================================================
    // MODULES & FILTERING
    // ========================================================================

    private List<Module> getFilteredModules() {
        List<Module> baseList;

        if (searchActive && searchQuery != null && !searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase(Locale.ROOT);
            baseList = new ArrayList<>();
            for (Module.Category cat : Module.Category.values()) {
                baseList.addAll(getCategoryModules(cat));
            }
            baseList = baseList.stream()
                    .filter(module -> module.name.toLowerCase(Locale.ROOT).contains(query) ||
                            (module.description != null && module.description.toLowerCase(Locale.ROOT).contains(query)))
                    .collect(Collectors.toList());
        } else if (selectedCategory == null) {
            baseList = Collections.emptyList();
        } else {
            baseList = getCategoryModules(selectedCategory);
        }

        if (baseList.isEmpty() || currentSortMode == SortMode.DEFAULT) {
            return baseList;
        }

        List<Module> sortedList = new ArrayList<>(baseList);
        switch (currentSortMode) {
            case A_Z -> sortedList.sort(Comparator.comparing(m -> m.name.toLowerCase(Locale.ROOT)));
            case ACTIVE -> sortedList.sort(Comparator.comparing((Module m) -> !m.enabled).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
            case BINDS -> sortedList.sort(Comparator.comparing((Module m) -> !keybinds.containsKey(m.name)).thenComparing(m -> m.name.toLowerCase(Locale.ROOT)));
        }

        return sortedList;
    }

    private void renderModules(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<Module> modules = getFilteredModules();

        if (modules.isEmpty()) {
            renderEmptyState(g, x, y, width, height);
            moduleMaxScroll = 0;
            moduleScroll = 0;
            return;
        }

        int gap = moduleGap();
        int availableW = width - gap;
        int autoColumnW = availableW / 2;
        int columnW = Math.max(s(65), Math.round(autoColumnW * UISettings.columnWidthScale));
        columnW = Math.min(columnW, autoColumnW);

        int moduleH = moduleHeight();
        int rows = (modules.size() + 1) / 2;
        int totalContentH = rows * moduleH + Math.max(0, rows - 1) * gap;

        moduleMaxScroll = Math.max(0, totalContentH - height);
        moduleScroll = Math.max(0, Math.min(moduleScroll, moduleMaxScroll));

        g.enableScissor(x, y, x + width, y + height);
        int startY = y - (int) moduleScroll;

        String activeTooltip = null;

        for (int index = 0; index < modules.size(); index++) {
            Module module = modules.get(index);
            int column = index % 2;
            int row = index / 2;

            int cardX = x + column * (columnW + gap);
            int cardY = startY + row * (moduleH + gap);

            if (cardY + moduleH < y || cardY > y + height) continue;

            boolean hovered = mouseX >= cardX && mouseX <= cardX + columnW && mouseY >= cardY && mouseY <= cardY + moduleH;
            renderModuleCard(g, module, cardX, cardY, columnW, moduleH, hovered);

            if (hovered && module.description != null && !module.description.isEmpty()) {
                activeTooltip = module.description;
                if (!module.name.equals(lastHoveredModule)) {
                    playGuiSound(1.75f, 0.08f);
                    lastHoveredModule = module.name;
                }
            } else if (!hovered && Objects.equals(lastHoveredModule, module.name)) {
                lastHoveredModule = null;
            }
        }

        g.disableScissor();

        if (moduleMaxScroll > 0) {
            renderScrollbar(g, x + width - s(3), y, s(3), height, totalContentH, moduleScroll, moduleMaxScroll);
        }

        if (activeTooltip != null) {
            renderTooltip(g, activeTooltip, mouseX, mouseY);
        }
    }

    private void renderModuleCard(GuiGraphicsExtractor g, Module module, int x, int y, int width, int height, boolean hovered) {
        float hover = moduleHover.getOrDefault(module.name, 0f);
        hover = hovered ? Math.min(1f, hover + UISettings.animStep(0.16f)) : Math.max(0f, hover - UISettings.animStep(0.16f));
        moduleHover.put(module.name, hover);

        int bg = Component.lerpColor(C_CARD, C_CARD_HOVER, hover);
        drawBox(g, x, y, width, height, bg);

        int statusColor = module.enabled ? C_ENABLED : C_DISABLED;
        g.fill(x + s(1), y + s(5), x + s(3), y + height - s(5), statusColor);

        int textX = x + s(8);
        int textY = y + height / 2 - s(4);

        String name = trimToWidth(module.name, width - s(45));
        int nameColor = module.enabled ? C_TEXT : Component.lerpColor(C_TEXT_DIM, C_TEXT, hover);
        UISettings.drawText(g, this.font, name, textX, textY, nameColor, false);

        if (module.name.equals(bindingModule)) {
            boolean blink = (System.currentTimeMillis() / 250) % 2 == 0;
            String bindPrompt = blink ? "[ _ ]" : "[   ]";
            int bindW = UISettings.textWidth(this.font, bindPrompt);
            UISettings.drawText(g, this.font, bindPrompt, x + width - bindW - s(7), textY, C_ACCENT, false);
        } else if (keybinds.containsKey(module.name)) {
            String key = "[" + keyName(keybinds.get(module.name)) + "]";
            int keyW = UISettings.textWidth(this.font, key);
            UISettings.drawText(g, this.font, key, x + width - keyW - s(7), textY, C_KEYBIND, false);
        } else if (searchActive && !searchQuery.isBlank() && module.category != null) {
            String catBadge = "[" + prettyCategoryName(module.category) + "]";
            int catW = UISettings.textWidth(this.font, catBadge);
            UISettings.drawText(g, this.font, catBadge, x + width - catW - s(7), textY, C_TEXT_DIM, false);
        }

        drawOutline(g, x, y, width, height, hovered ? C_ACCENT : C_SEPARATOR);
    }

    // ========================================================================
    // EMPTY / PROFILES
    // ========================================================================

    private void renderEmptyState(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        String title = searchActive && !searchQuery.isBlank() ? "No results" : "No modules";
        String subtitle = searchActive && !searchQuery.isBlank() ? "\"" + searchQuery + "\"" : "This category is empty.";
        int titleW = UISettings.textWidth(this.font, title);
        int subW = UISettings.textWidth(this.font, subtitle);
        int centerX = x + width / 2;
        int centerY = y + height / 2;
        UISettings.drawText(g, this.font, title, centerX - titleW / 2, centerY - s(8), C_TEXT, false);
        UISettings.drawText(g, this.font, subtitle, centerX - subW / 2, centerY + s(6), C_TEXT_DIM, false);
    }

    private void renderProfilesPlaceholder(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        int boxW = Math.min(width - s(10), s(300));
        int boxH = s(90);
        int bx = x + (width - boxW) / 2;
        int by = y + (height - boxH) / 2;

        drawBox(g, bx, by, boxW, boxH, C_CARD);
        drawOutline(g, bx, by, boxW, boxH, C_SEPARATOR);

        String title = "Profiles";
        int titleW = UISettings.textWidth(this.font, title);
        UISettings.drawText(g, this.font, title, bx + (boxW - titleW) / 2, by + s(20), C_TEXT, false);

        String text = "Profile system coming soon";
        int textW = UISettings.textWidth(this.font, text);
        UISettings.drawText(g, this.font, text, bx + (boxW - textW) / 2, by + s(43), C_TEXT_DIM, false);
    }

    // ========================================================================
    // SETTINGS
    // ========================================================================

    private void renderSettings(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int[] window = getWindowRect();
        int wx = window[0], wy = window[1], ww = window[2], wh = window[3];

        Component.drawShadow(g, wx, wy, ww, wh);
        drawVerticalGradient(g, wx, wy, ww, wh, C_WINDOW, C_WINDOW_BOTTOM);

        int headerH = headerHeight();
        renderSettingsHeader(g, wx, wy, ww, headerH, mouseX, mouseY);

        int contentX = wx + contentPadding();
        int contentY = wy + headerH + contentPadding();
        int contentW = ww - contentPadding() * 2;
        int contentH = wh - headerH - contentPadding() * 2;

        List<Component> components = selectedModule.settings;
        int gap = s(UISettings.compactMode ? 3 : 5);
        int colW = (contentW - gap) / 2;

        int leftX = contentX;
        int rightX = contentX + colW + gap;

        int leftYOffset = 0;
        int rightYOffset = 0;

        for (Component component : components) {
            component.width = colW;
            if (isPickerComponent(component) || (rightYOffset < leftYOffset)) {
                component.x = rightX;
                rightYOffset += component.height + gap;
            } else {
                component.x = leftX;
                leftYOffset += component.height + gap;
            }
        }

        int contentTotalH = Math.max(leftYOffset, rightYOffset);
        settingsMaxScroll = Math.max(0, contentTotalH - contentH);
        settingsScroll = Math.max(0, Math.min(settingsScroll, settingsMaxScroll));

        leftYOffset = 0;
        rightYOffset = 0;

        for (Component component : components) {
            if (component.x == rightX) {
                component.y = contentY + rightYOffset - (int) settingsScroll;
                rightYOffset += component.height + gap;
            } else {
                component.y = contentY + leftYOffset - (int) settingsScroll;
                leftYOffset += component.height + gap;
            }
        }

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        Component hovered = null;

        for (int i = components.size() - 1; i >= 0; i--) {
            Component component = components.get(i);
            if (isPickerComponent(component)) continue;

            boolean visible = component.y <= contentY + contentH + s(150) && component.y + component.height >= contentY;

            if (visible) {
                component.render(g, mouseX, mouseY);

                if (component.getDescription() != null && !component.getDescription().isEmpty()
                        && mouseX >= component.x && mouseX <= component.x + component.width
                        && mouseY >= Math.max(component.y, contentY) && mouseY <= Math.min(component.y + component.height, contentY + contentH)) {
                    hovered = component;
                }
            }
        }

        for (int i = components.size() - 1; i >= 0; i--) {
            Component component = components.get(i);
            if (!isPickerComponent(component)) continue;

            boolean visible = component.y <= contentY + contentH + s(150) && component.y + component.height >= contentY;

            if (visible) {
                component.render(g, mouseX, mouseY);

                if (component.getDescription() != null && !component.getDescription().isEmpty()
                        && mouseX >= component.x && mouseX <= component.x + component.width
                        && mouseY >= Math.max(component.y, contentY) && mouseY <= Math.min(component.y + component.height, contentY + contentH)) {
                    hovered = component;
                }
            }
        }

        g.disableScissor();

        if (settingsMaxScroll > 0) {
            renderScrollbar(g, contentX + contentW - s(2), contentY, s(3), contentH, contentTotalH, settingsScroll, settingsMaxScroll);
        }

        if (hovered != null) {
            renderTooltip(g, hovered.getDescription(), mouseX, mouseY);
        }

        drawOutline(g, wx, wy, ww, wh, C_SEPARATOR);
    }

    private void renderSettingsHeader(GuiGraphicsExtractor g, int x, int y, int width, int height, int mouseX, int mouseY) {
        drawBox(g, x, y, width, height, C_HEADER);

        int backW = s(52), backH = s(21);
        int backX = x + s(6), backY = y + (height - backH) / 2;

        boolean hovered = mouseX >= backX && mouseX <= backX + backW && mouseY >= backY && mouseY <= backY + backH;
        backHover = hovered ? Math.min(1f, backHover + UISettings.animStep(0.18f)) : Math.max(0f, backHover - UISettings.animStep(0.18f));

        int bg = Component.lerpColor(C_HEADER, C_SIDEBAR_HOVER, backHover);
        drawBox(g, backX, backY, backW, backH, bg);
        UISettings.drawText(g, this.font, "< Back", backX + s(7), backY + backH / 2 - s(4), hovered ? C_TEXT : C_TEXT_DIM, false);

        String title = selectedModule != null ? selectedModule.name : "Settings";
        int titleX = backX + backW + s(9);
        UISettings.drawText(g, this.font, trimToWidth(title, width - backW - s(35)), titleX, y + height / 2 - s(4), C_TEXT, false);

        g.fill(x, y + height - 1, x + width, y + height, C_SEPARATOR);
    }

    // ========================================================================
    // SEARCH & PROMPTS
    // ========================================================================

    private void renderSearch(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!searchActive) return;

        int[] window = getWindowRect();
        int headerH = headerHeight();
        int width = s(180);
        int height = s(20);
        int x = window[0] + window[2] - width - s(8);
        int y = window[1] + (headerH - height) / 2;

        drawBox(g, x, y, width, height, UISettings.withPanelAlpha(0x101010));
        drawOutline(g, x, y, width, height, C_ACCENT);

        String displayText = searchQuery.isEmpty() ? "Search..." : searchQuery;
        String trimmed = trimToWidth(displayText, width - s(18));
        UISettings.drawText(g, this.font, "⌕ " + trimmed, x + s(6), y + (height / 2) - s(4), searchQuery.isEmpty() ? C_TEXT_DIM : C_TEXT, false);
    }

    private void renderBindingPrompt(GuiGraphicsExtractor g) {
        if (bindingModule == null) return;
        String text = "Bind: " + bindingModule + "  |  press key  |  ESC clear";
        int textW = UISettings.textWidth(this.font, text);
        int width = textW + s(14), height = s(20);
        int x = this.width / 2 - width / 2, y = s(5);

        drawBox(g, x, y, width, height, C_HEADER);
        drawOutline(g, x, y, width, height, C_ACCENT);
        UISettings.drawText(g, this.font, text, x + s(7), y + s(6), C_ACCENT, false);
    }

    // ========================================================================
    // SCROLLBAR & GRADIENT
    // ========================================================================

    private void renderScrollbar(GuiGraphicsExtractor g, int x, int y, int width, int height, int contentHeight, double scroll, int maxScroll) {
        if (height <= 0 || contentHeight <= height) return;
        g.fill(x, y, x + width, y + height, UISettings.withPanelAlpha(0x181818));
        int thumbHeight = Math.max(s(18), Math.round(height * (height / (float) contentHeight)));
        int available = height - thumbHeight;
        int thumbY = y + (int) (available * (scroll / (double) maxScroll));
        drawBox(g, x, thumbY, width, thumbHeight, C_ACCENT_DIM);
    }

    private void drawVerticalGradient(GuiGraphicsExtractor g, int x, int y, int width, int height, int top, int bottom) {
        if (width <= 0 || height <= 0) return;
        if (!UISettings.roundedCorners) {
            int steps = Math.max(1, Math.min(height, 28));
            int stepH = Math.max(1, height / steps);
            int currentY = y;

            for (int i = 0; i < steps; i++) {
                float t = steps <= 1 ? 0f : i / (float) (steps - 1);
                int color = Component.lerpColor(top, bottom, t);
                int segmentH = (i == steps - 1) ? y + height - currentY : stepH;
                g.fill(x, currentY, x + width, currentY + segmentH, color);
                currentY += segmentH;
            }
            return;
        }

        int avgColor = Component.lerpColor(top, bottom, 0.5f);
        Component.drawRoundedRect(g, x, y, width, height, avgColor);
    }

    // ========================================================================
    // TOOLTIP
    // ========================================================================

    private void renderTooltip(GuiGraphicsExtractor g, String description, int mouseX, int mouseY) {
        if (description == null || description.isEmpty()) return;

        List<net.minecraft.network.chat.FormattedText> lines = this.font.getSplitter()
                .splitLines(net.minecraft.network.chat.Component.literal(description), s(210), Style.EMPTY);

        int textWidth = 0;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            textWidth = Math.max(textWidth, UISettings.textWidth(this.font, line.getString()));
        }

        int lineHeight = s(this.font.lineHeight);
        int padding = s(5);
        int totalHeight = lines.size() * lineHeight + padding * 2;
        int boxWidth = textWidth + padding * 2;
        int tx = mouseX + s(12), ty = mouseY - s(10);

        if (tx + boxWidth > this.width) tx = mouseX - boxWidth - s(12);
        if (ty + totalHeight > this.height) ty = this.height - totalHeight - s(4);
        if (ty < 0) ty = s(4);

        Component.drawShadow(g, tx, ty, boxWidth, totalHeight);
        drawBox(g, tx, ty, boxWidth, totalHeight, UISettings.withPanelAlpha(0x0A0A0A));
        drawOutline(g, tx, ty, boxWidth, totalHeight, C_SEPARATOR);

        int cy = ty + padding;
        for (net.minecraft.network.chat.FormattedText line : lines) {
            UISettings.drawText(g, this.font, line.getString(), tx + padding, cy, C_TEXT, false);
            cy += lineHeight;
        }
    }

    // ========================================================================
    // MOUSE
    // ========================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        int mx = (int) event.x(), my = (int) event.y(), button = event.button();

        if (selectedModule != null) {
            if (isBackButtonHovered(mx, my)) {
                selectedModule = null;
                settingsScroll = 0;
                playGuiSound(0.85f, 0.25f);
                return true;
            }

            for (Component component : selectedModule.settings) {
                if (component.mouseClicked(mx, my, button)) return true;
            }
            return true;
        }

        int[] window = getWindowRect();
        int wx = window[0], wy = window[1], ww = window[2], wh = window[3];
        int headerH = headerHeight(), sidebarW = sidebarWidth();

        // Klick auf den Sortier-Button abfangen
        if (!profilesSelected && selectedModule == null) {
            int hintW = searchActive ? s(195) : UISettings.textWidth(this.font, "CTRL+F") + s(10);
            String sortText = "Sort: " + currentSortMode.name();
            int sortBtnW = UISettings.textWidth(this.font, sortText) + s(16);
            int sx = wx + ww - hintW - sortBtnW - s(10);
            int sy = wy + (headerH - s(16)) / 2;

            if (mx >= sx && mx <= sx + sortBtnW && my >= sy && my <= sy + s(16)) {
                if (button == 0) {
                    SortMode[] modes = SortMode.values();
                    currentSortMode = modes[(currentSortMode.ordinal() + 1) % modes.length];
                    moduleScroll = 0;
                    playGuiSound(1.1f, 0.20f);
                    return true;
                }
            }
        }

        if (mx >= wx && mx <= wx + sidebarW && my >= wy + headerH && my <= wy + wh) {
            if (handleSidebarClick(mx, my, wx, wy + headerH, sidebarW, wh - headerH, button)) return true;
        }

        if (!profilesSelected) {
            List<Module> modules = getFilteredModules();
            int contentX = wx + sidebarW + contentPadding();
            int contentY = wy + headerH + contentPadding();
            int contentW = ww - sidebarW - contentPadding() * 2;
            int contentH = wh - headerH - contentPadding() * 2;

            int gap = moduleGap();
            int columnW = Math.max(s(65), Math.round((contentW - gap) / 2f * UISettings.columnWidthScale));
            columnW = Math.min(columnW, (contentW - gap) / 2);
            int moduleH = moduleHeight();

            for (int index = 0; index < modules.size(); index++) {
                Module module = modules.get(index);
                int column = index % 2, row = index / 2;
                int cardX = contentX + column * (columnW + gap);
                int cardY = contentY - (int) moduleScroll + row * (moduleH + gap);

                if (mx >= cardX && mx <= cardX + columnW && my >= cardY && my <= cardY + moduleH
                        && cardY + moduleH >= contentY && cardY <= contentY + contentH) {
                    if (button == 0) {
                        module.toggle();
                        playGuiSound(module.enabled ? 1.05f : 0.80f, 0.25f);
                        return true;
                    }
                    if (button == 1) {
                        selectedModule = module;
                        settingsScroll = 0;
                        bindingModule = null;
                        playGuiSound(1.20f, 0.25f);
                        return true;
                    }
                    if (button == 2) {
                        bindingModule = module.name;
                        playGuiSound(0.90f, 0.20f);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    private boolean handleSidebarClick(int mx, int my, int x, int y, int width, int height, int button) {
        if (button != 0) return false;

        int itemH = s(UISettings.compactMode ? 24 : 27);
        int itemGap = s(3);
        int catStartY = y + s(10);
        int catAvailH = height - s(15);

        if (mx < x || mx > x + width || my < catStartY || my > catStartY + catAvailH) {
            return false;
        }

        int currentY = catStartY - (int) categoryScroll;

        for (Module.Category category : getVisibleCategories()) {
            if (mx >= x + s(5) && mx <= x + width - s(5) && my >= currentY && my <= currentY + itemH) {
                selectCategory(category);
                return true;
            }
            currentY += itemH + itemGap;
        }

        int separatorH = s(22);
        currentY += separatorH;

        int profileX = x + s(5);
        int profileW = width - s(10);
        if (mx >= profileX && mx <= profileX + profileW && my >= currentY && my <= currentY + itemH) {
            profilesSelected = true;
            selectedModule = null;
            selectedCategory = null;
            moduleScroll = 0;
            playGuiSound(1.0f, 0.20f);
            return true;
        }
        currentY += itemH + itemGap;

        int settingsX = x + s(5);
        int settingsW = width - s(10);
        if (mx >= settingsX && mx <= settingsX + settingsW && my >= currentY && my <= currentY + itemH) {
            selectedModule = UISettingsModule.getInstance();
            settingsScroll = 0;
            bindingModule = null;
            playGuiSound(1.25f, 0.25f);
            return true;
        }

        return false;
    }

    private boolean isBackButtonHovered(int mx, int my) {
        int[] window = getWindowRect();
        int x = window[0] + s(6), y = window[1] + (headerHeight() - s(21)) / 2;
        int w = s(52), h = s(21);
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (selectedModule != null) {
            for (Component component : selectedModule.settings) {
                component.mouseReleased(event.x(), event.y(), event.button());
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (selectedModule != null) {
            for (Component component : selectedModule.settings) {
                if (component.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
            }
            if (settingsMaxScroll > 0) {
                settingsScroll -= verticalAmount * s(18);
                settingsScroll = Math.max(0, Math.min(settingsScroll, settingsMaxScroll));
                return true;
            }
            return true;
        }

        int[] window = getWindowRect();
        int sidebarW = sidebarWidth();
        int headerH = headerHeight();

        int sidebarX = window[0];
        int sidebarY = window[1] + headerH;
        int sidebarAreaH = window[3] - headerH;

        if (categoryMaxScroll > 0 && mouseX >= sidebarX && mouseX <= sidebarX + sidebarW && mouseY >= sidebarY && mouseY <= sidebarY + sidebarAreaH) {
            categoryScroll -= verticalAmount * s(18);
            categoryScroll = Math.max(0, Math.min(categoryScroll, categoryMaxScroll));
            return true;
        }

        if (moduleMaxScroll > 0) {
            int contentX = window[0] + sidebarW + contentPadding();
            int contentY = window[1] + headerH + contentPadding();
            int contentW = window[2] - sidebarW - contentPadding() * 2;
            int contentH = window[3] - headerH - contentPadding() * 2;

            if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
                moduleScroll -= verticalAmount * s(18);
                moduleScroll = Math.max(0, Math.min(moduleScroll, moduleMaxScroll));
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key(), modifiers = event.modifiers();

        if (bindingModule != null) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                keybinds.remove(bindingModule);
                bindingModule = null;
                playGuiSound(0.70f, 0.20f);
                return true;
            }
            keybinds.put(bindingModule, key);
            bindingModule = null;
            playGuiSound(1.25f, 0.25f);
            return true;
        }

        if (key == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            searchActive = !searchActive;
            searchQuery = "";
            moduleScroll = 0;
            playGuiSound(searchActive ? 1.45f : 0.90f, 0.20f);
            return true;
        }

        if (searchActive && key == GLFW.GLFW_KEY_BACKSPACE) {
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                moduleScroll = 0;
            }
            return true;
        }

        if (searchActive && key == GLFW.GLFW_KEY_ESCAPE) {
            searchActive = false;
            searchQuery = "";
            moduleScroll = 0;
            playGuiSound(0.80f, 0.20f);
            return true;
        }

        if (selectedModule != null) {
            for (Component component : selectedModule.settings) {
                if (component.keyPressed(event)) return true;
            }
        }

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (selectedModule != null) {
                selectedModule = null;
                settingsScroll = 0;
                playGuiSound(0.85f, 0.25f);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchActive && bindingModule == null) {
            char character = (char) event.codepoint();
            if (character >= 32 && character != 127) {
                searchQuery += character;
                moduleScroll = 0;
                return true;
            }
        }
        if (selectedModule != null) {
            for (Component component : selectedModule.settings) {
                if (component.charTyped(event)) return true;
            }
        }
        return super.charTyped(event);
    }

    private static final Map<Integer, String> KEY_NAMES = new HashMap<>();
    static {
        for (int key = GLFW.GLFW_KEY_A; key <= GLFW.GLFW_KEY_Z; key++) KEY_NAMES.put(key, String.valueOf((char) key));
        for (int key = GLFW.GLFW_KEY_0; key <= GLFW.GLFW_KEY_9; key++) KEY_NAMES.put(key, String.valueOf((char) key));
        for (int f = 0; f < 12; f++) KEY_NAMES.put(GLFW.GLFW_KEY_F1 + f, "F" + (f + 1));
        KEY_NAMES.put(GLFW.GLFW_KEY_TAB, "TAB");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT_SHIFT, "LSHIFT");
        KEY_NAMES.put(GLFW.GLFW_KEY_RIGHT_SHIFT, "RSHIFT");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT_CONTROL, "LCTRL");
        KEY_NAMES.put(GLFW.GLFW_KEY_RIGHT_CONTROL, "RCTRL");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT_ALT, "ALT");
        KEY_NAMES.put(GLFW.GLFW_KEY_RIGHT_ALT, "RALT");
        KEY_NAMES.put(GLFW.GLFW_KEY_SPACE, "SPACE");
        KEY_NAMES.put(GLFW.GLFW_KEY_INSERT, "INS");
        KEY_NAMES.put(GLFW.GLFW_KEY_DELETE, "DEL");
        KEY_NAMES.put(GLFW.GLFW_KEY_HOME, "HOME");
        KEY_NAMES.put(GLFW.GLFW_KEY_END, "END");
        KEY_NAMES.put(GLFW.GLFW_KEY_PAGE_UP, "PGUP");
        KEY_NAMES.put(GLFW.GLFW_KEY_PAGE_DOWN, "PGDN");
        KEY_NAMES.put(GLFW.GLFW_KEY_CAPS_LOCK, "CAPS");
        KEY_NAMES.put(GLFW.GLFW_KEY_ENTER, "ENTER");
        KEY_NAMES.put(GLFW.GLFW_KEY_LEFT, "LEFT");
        KEY_NAMES.put(GLFW.GLFW_KEY_RIGHT, "RIGHT");
        KEY_NAMES.put(GLFW.GLFW_KEY_UP, "UP");
        KEY_NAMES.put(GLFW.GLFW_KEY_DOWN, "DOWN");
    }

    private static String keyName(int key) {
        return KEY_NAMES.getOrDefault(key, "K" + key);
    }

    @Override
    public void onClose() {
        Config.save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}