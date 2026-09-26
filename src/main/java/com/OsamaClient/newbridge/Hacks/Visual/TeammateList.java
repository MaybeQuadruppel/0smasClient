package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.FontAtlas;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;

/**
 * Animated HUD list of active teammates, top-left corner. Läuft jetzt über dieselbe Pipeline wie
 * ClickGui/ModuleList ({@link UiRenderer} + {@link Ui}/{@link UiFont}) statt über GuiGraphicsExtractor -
 * knackscharfe FreeType-Glyphen, echte abgerundete Reihen statt reiner Textzeilen, dieselben globalen
 * Theme-Werte (Radius, Outline). Eigene UiRenderer-Instanz, direkt vom {@code GameRendererUiMixin}
 * gerendert - nicht mehr über {@code HudElementRegistry} (siehe ModuleList-Klassenkommentar, warum das
 * für unsere eigene Pipeline nicht sauber wäre).
 */
public class TeammateList extends Module {

    public static TeammateList instance;

    private static final float LEFT_MARGIN = 4f, START_Y = 4f, ROW_GAP = 1.5f;
    private static final float ACCENT_W = 1.2f, SLIDE_RANGE = 30f, ANIM_SPEED = 0.14f;
    private static final int C_TEAM = 0xFF55FF55; // grüner Akzent für Teammates

    private static final UiRenderer renderer = new UiRenderer();
    private static final Ui ui = new Ui(renderer);

    // float[0] = alpha (0->1), float[1] = slide (0->1), float[2] = currentY
    private static final Map<String, float[]> anim = new LinkedHashMap<>();

    public final ToggleButton showDistance = new ToggleButton("Show Distance", true, null)
            .withDescription("Displays player distance next to their name");
    public final Slider textScale = new Slider("Text Scale", 0.5, 1.0, 1.0, 0.05, null)
            .withDescription("Adjusts the text size of the HUD list");
    public final ToggleButton background = new ToggleButton("Background", true, null)
            .withDescription("Draws a small backing box behind each entry");

    public TeammateList() {
        super("TeammateList", "HUD list of active teammates in the top left corner", Category.VISUAL);
        this.enabled = true;
        instance = this;
        settings.add(showDistance);
        settings.add(textScale);
        settings.add(background);
    }

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null
                || !instance.enabled
                || mc.player == null
                || mc.level == null
                || mc.gui.hud.isHidden()
                || mc.getDebugOverlay().showDebugScreen()
                || ClickGui.INSTANCE.isOpen()
                || mc.gui.screen() != null) {
            for (float[] s : anim.values()) { s[0] = 0f; s[1] = 0f; }
            return;
        }

        renderer.begin();
        ui.scale = Theme.scale() * (float) instance.textScale.getValue();
        ui.alpha = 1f;
        ui.hoverBlocked = true; // reines HUD-Overlay, nimmt nie Maus-Hover entgegen

        // ── Teammates sammeln ──────────────────────────────────────────────────
        List<Player> teammates = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (TeamUtils.isTeammate(player)) teammates.add(player);
        }
        teammates.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));

        Set<String> activeNames = new HashSet<>();
        Map<String, String> displayNameMap = new HashMap<>();
        for (Player p : teammates) {
            String name = p.getGameProfile().name();
            activeNames.add(name);
            String text = p.getDisplayName().getString();
            if (instance.showDistance.enabled) {
                int dist = (int) mc.player.distanceTo(p);
                text += "  " + dist + "m";
            }
            displayNameMap.put(name, text);
        }

        // ── Animationen verwalten ──────────────────────────────────────────────
        for (String name : activeNames) anim.putIfAbsent(name, new float[]{0f, 0f, START_Y});
        for (Map.Entry<String, float[]> e : anim.entrySet()) {
            boolean present = activeNames.contains(e.getKey());
            float[] s = e.getValue();
            s[0] = approach(s[0], present ? 1f : 0f, ANIM_SPEED);
            s[1] = approach(s[1], present ? 1f : 0f, ANIM_SPEED);
        }
        anim.entrySet().removeIf(e -> !activeNames.contains(e.getKey()) && e.getValue()[0] < 0.01f);

        List<String> order = new ArrayList<>(activeNames);
        for (String name : anim.keySet()) if (!activeNames.contains(name)) order.add(name);

        float textSize = Theme.FONT;
        FontAtlas atlas = ui.font(textSize);
        float lineH = atlas.lineHeight / ui.scale;
        float rowH = lineH + ROW_GAP;
        float radius = Theme.radius();

        float targetY = START_Y;
        for (String name : order) {
            float[] s = anim.get(name);
            if (s == null) continue;

            if (s[2] <= 0f) s[2] = targetY;
            s[2] = approach(s[2], targetY, 0.18f);

            if (s[0] < 0.01f) { targetY += rowH; continue; }

            String label = displayNameMap.getOrDefault(name, name);
            float lw = ui.textWidth(label, textSize);
            float slideOffset = (1f - s[1]) * -SLIDE_RANGE; // von links reinsliden beim Einblenden
            float textX = LEFT_MARGIN + slideOffset;
            float renderY = s[2];

            if (instance.background.enabled) {
                float padX = 3f, padY = 1f;
                ui.alpha = s[0] * 0.75f;
                ui.round(textX - padX, renderY - padY, lw + padX * 2, lineH + padY * 2, radius, Theme.ROW);
                if (Theme.outline()) {
                    ui.outline(textX - padX, renderY - padY, lw + padX * 2, lineH + padY * 2, radius, 1f, Theme.OUTLINE);
                }
            }
            ui.alpha = s[0];

            // Akzent-Leiste am linken Bildschirmrand
            ui.rect(slideOffset, renderY - 1, ACCENT_W, lineH + 2, ColorUtil.alpha(C_TEAM, s[0]));
            ui.text(label, textX, renderY, lineH, textSize, Theme.TEXT);

            targetY += rowH;
        }

        renderer.flush();
        UiFont.collect(600);
    }

    private static float approach(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(1f, speed);
    }
}