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
import net.minecraft.world.entity.player.PlayerSkin;

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

    public final ToggleButton showFaces = new ToggleButton("Show Faces", true, null)
            .withDescription("Draws each teammate's skin face next to the name");
    public final ToggleButton showHealth = new ToggleButton("Show Health", true, null)
            .withDescription("Thin health bar under each teammate");

    public TeammateList() {
        super("TeammateList", "HUD list of active teammates in the top left corner", Category.VISUAL);
        this.enabled = false;
        instance = this;
        settings.add(showDistance);
        settings.add(textScale);
        settings.add(showFaces);
        settings.add(showHealth);
        settings.add(background);
    }

    private record Entry(String key, String name, int color, String dist, float health, PlayerSkin skin) {}

    public static void draw() {
        Minecraft mc = Minecraft.getInstance();
        boolean editing = HudOverlay.editing();
        if (instance == null
                || !instance.enabled
                || mc.player == null
                || mc.level == null
                || mc.gui.hud.isHidden()
                || mc.getDebugOverlay().showDebugScreen()
                || ClickGui.INSTANCE.isOpen()
                || (mc.gui.screen() != null && !editing)) {
            for (float[] s : anim.values()) { s[0] = 0f; s[1] = 0f; }
            return;
        }

        renderer.begin();
        ui.scale = Theme.scale() * (float) instance.textScale.getValue();
        ui.alpha = 1f;
        ui.hoverBlocked = true;

        // ── Teammates sammeln ──────────────────────────────────────────────────
        List<Player> teammates = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (TeamUtils.isTeammate(player)) teammates.add(player);
        }
        teammates.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));

        List<Entry> entries = new ArrayList<>();
        for (Player p : teammates) {
            var info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(p.getUUID());
            // Name: Profilname (Custom-Nametag-Server ueberschreiben getDisplayName oft mit Dekoration)
            String name = p.getGameProfile().name();
            Integer col = TeamUtils.teamColorOf(p.getDisplayName());
            if (col == null && info != null) col = TeamUtils.teamColorOf(info.getTabListDisplayName());
            if (col == null && info != null && info.getTeam() != null)
                col = TeamUtils.teamColorOf(info.getTeam().getFormattedName(net.minecraft.network.chat.Component.literal(name)));
            String dist = instance.showDistance.enabled ? (int) mc.player.distanceTo(p) + "m" : "";
            float hp = Math.max(0f, Math.min(1f, p.getHealth() / Math.max(1f, p.getMaxHealth())));
            entries.add(new Entry(name, name, col == null ? C_TEAM : (0xFF000000 | col), dist, hp, info == null ? null : info.getSkin()));
        }
        if (entries.isEmpty() && editing) {
            entries.add(new Entry("Steve", "Steve", C_TEAM, instance.showDistance.enabled ? "12m" : "", 1f, null));
            entries.add(new Entry("Alex", "Alex", C_TEAM, instance.showDistance.enabled ? "27m" : "", 0.6f, null));
        }

        Set<String> activeNames = new HashSet<>();
        for (Entry e : entries) activeNames.add(e.key());
        for (String name : activeNames) anim.putIfAbsent(name, new float[]{0f, 0f, 0f});
        for (Map.Entry<String, float[]> e : anim.entrySet()) {
            boolean present = activeNames.contains(e.getKey());
            float[] s = e.getValue();
            s[0] = approach(s[0], present ? 1f : 0f, ANIM_SPEED);
            s[1] = approach(s[1], present ? 1f : 0f, ANIM_SPEED);
        }
        anim.entrySet().removeIf(e -> !activeNames.contains(e.getKey()) && e.getValue()[0] < 0.01f);

        float textSize = Theme.FONT;
        FontAtlas atlas = ui.font(textSize);
        float lineH = atlas.lineHeight / ui.scale;
        boolean faces = instance.showFaces.enabled, hp = instance.showHealth.enabled;
        float face = Math.max(lineH + 2f, 10f);
        float rowH = face + 2f + (hp ? 2f : 0f);
        float radius = Theme.radius();
        float pad = 4f, headH = lineH + 4f;

        // Kartenbreite
        float w = ui.textWidth("TEAM", textSize * 0.85f) + 30f;
        for (Entry e : entries) {
            float rw = (faces ? face + 3f : 0f) + ui.textWidth(e.name(), textSize) + (e.dist().isEmpty() ? 0f : 8f + ui.textWidth(e.dist(), textSize));
            w = Math.max(w, rw);
        }
        w += pad * 2;
        float h = headH + entries.size() * rowH + pad;

        float[] org = HudLayout.origin("teammates", LEFT_MARGIN * ui.scale, START_Y * ui.scale,
                mc.getWindow().getWidth(), mc.getWindow().getHeight());
        float x0 = org[0] / ui.scale, y0 = org[1] / ui.scale;
        HudLayout.report("teammates", "Teammates", false, x0 * ui.scale, y0 * ui.scale, w * ui.scale, (entries.isEmpty() ? headH + pad : h) * ui.scale);

        if (!entries.isEmpty() || editing) {
            float cardAlpha = 1f;
            for (Entry e : entries) cardAlpha = Math.max(cardAlpha > 1f ? 0f : 0f, anim.get(e.key())[0]);
            ui.alpha = Math.max(cardAlpha, editing ? 1f : 0f);
            if (instance.background.enabled) {
                ui.alpha *= 0.8f;
                ui.round(x0, y0, w, entries.isEmpty() ? headH + pad : h, radius, Theme.ROW);
                if (Theme.outline()) ui.outline(x0, y0, w, entries.isEmpty() ? headH + pad : h, radius, 1f, Theme.OUTLINE);
                ui.alpha = Math.max(cardAlpha, editing ? 1f : 0f);
            }
            ui.rect(x0 + 2f, y0 + 2f, 1.5f, headH - 3f, Theme.accent());
            ui.text("TEAM", x0 + pad + 2f, y0 + 2f, lineH, textSize * 0.85f, Theme.TEXT_DIM);
            String cnt = String.valueOf(entries.size());
            ui.text(cnt, x0 + w - pad - ui.textWidth(cnt, textSize * 0.85f), y0 + 2f, lineH, textSize * 0.85f, Theme.accent());
        }

        float y = y0 + headH;
        for (Entry e : entries) {
            float[] s = anim.get(e.key());
            if (s == null || s[0] < 0.01f) { y += rowH; continue; }
            float slide = (1f - s[1]) * -SLIDE_RANGE;
            ui.alpha = s[0];
            float x = x0 + pad + slide;
            float tx = x;
            if (faces) {
                ui.round(x, y, face, face, 2f, ColorUtil.alpha(e.color(), 0.35f));
                if (e.skin() != null && s[0] > 0.9f)
                    HudIcons.face(e.skin(), (x + 0.5f) * ui.scale, (y + 0.5f) * ui.scale, (face - 1f) * ui.scale);
                tx += face + 3f;
            }
            float ty = y + (face - lineH) * 0.5f;
            ui.text(e.name(), tx, ty, lineH, textSize, e.color());
            if (!e.dist().isEmpty()) {
                float dw = ui.textWidth(e.dist(), textSize);
                ui.text(e.dist(), x0 + w - pad - dw + slide, ty, lineH, textSize, Theme.TEXT_DIM);
            }
            if (hp) {
                float bx = tx, bw = x0 + w - pad - tx + slide, by = y + face + 0.5f;
                ui.round(bx, by, bw, 1.5f, 0.75f, ColorUtil.alpha(0xFF000000, 0.5f));
                int hc = e.health() > 0.6f ? 0xFF52E084 : e.health() > 0.3f ? 0xFFF0C040 : 0xFFE0525A;
                ui.round(bx, by, Math.max(1.5f, bw * e.health()), 1.5f, 0.75f, hc);
            }
            y += rowH;
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