package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.TeamUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class TeammateList extends Module {

    public static TeammateList instance;

    private static final int  LEFT_MARGIN = 6;
    private static final int  ACCENT_W    = 2;
    private static final int  SPACING     = 0;
    private static final int  START_Y     = 4;
    private static final float SLIDE_RANGE = 50f;
    private static final float ANIM_SPEED = 0.14f;

    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_TEAM_BAR  = 0xFF55FF55; // Grüner Akzentbalken für Teammates

    // float[0] = alpha (0->1), float[1] = slide (0->1), float[2] = currentY
    private static final Map<String, float[]> anim = new LinkedHashMap<>();

    private boolean showDistance = true;
    private double  fontScale    = 1.0;

    public TeammateList() {
        super("TeammateList", "HUD list of active teammates in the top left corner", Category.VISUAL);
        this.enabled = true;
        instance = this;

        this.settings.add(new ToggleButton("Show Distance", true,
                val -> showDistance = val).withDescription("Displays player distance next to their name."));
        this.settings.add(new Slider("Text Scale", 0.5, 1.0, 1.0,
                val -> fontScale = val).withDescription("Adjusts the text size of the HUD list."));
    }

    public static void draw(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (instance == null
                || !instance.enabled
                || mc.player == null
                || mc.level == null
                || mc.gui.hud.isHidden()
                || mc.getDebugOverlay().showDebugScreen()) {
            for (float[] s : anim.values()) { s[0] = 0f; s[1] = 0f; }
            return;
        }

        // ── Teammates sammeln ──────────────────────────────────────────────────
        List<Player> teammates = new ArrayList<>();
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (TeamUtils.isTeammate(player)) {
                teammates.add(player);
            }
        }

        // Nach Distanz sortieren (nächste zuerst)
        teammates.sort(Comparator.comparingDouble(p -> mc.player.distanceToSqr(p)));

        Set<String> activeNames = new HashSet<>();
        Map<String, String> displayNameMap = new HashMap<>();

        for (Player p : teammates) {
            String name = p.getGameProfile().name();
            activeNames.add(name);

            String text = p.getDisplayName().getString();
            if (instance.showDistance) {
                int dist = (int) mc.player.distanceTo(p);
                text += " " + dist + "m";
            }
            displayNameMap.put(name, text);
        }

        // ── Animationen verwalten ──────────────────────────────────────────────
        for (String name : activeNames) {
            anim.putIfAbsent(name, new float[]{0f, 0f, START_Y});
        }

        for (Map.Entry<String, float[]> e : anim.entrySet()) {
            boolean present = activeNames.contains(e.getKey());
            float[] s = e.getValue();
            s[0] = approach(s[0], present ? 1f : 0f, ANIM_SPEED); // Alpha
            s[1] = approach(s[1], present ? 1f : 0f, ANIM_SPEED); // Slide
        }

        anim.entrySet().removeIf(e -> !activeNames.contains(e.getKey()) && e.getValue()[0] < 0.01f);

        List<String> order = new ArrayList<>(activeNames);
        for (String name : anim.keySet()) {
            if (!activeNames.contains(name)) order.add(name);
        }

        float scale = (float) instance.fontScale;
        int scaledLh = (int) (mc.font.lineHeight * scale);
        int targetY = START_Y;

        // ── Rendering oben links ───────────────────────────────────────────────
        for (int i = 0; i < order.size(); i++) {
            String name = order.get(i);
            float[] s = anim.get(name);
            if (s == null) continue;

            if (s[2] <= 0f) s[2] = targetY;
            s[2] = approach(s[2], targetY, 0.18f);

            if (s[0] < 0.01f) {
                targetY += scaledLh + SPACING + 1;
                continue;
            }

            String label = displayNameMap.getOrDefault(name, name);
            int renderY = (int) s[2];

            // Akzent-Balken ganz links am Bildschirmrand
            int accentColor = argbWithAlpha(C_TEAM_BAR, s[0]);
            g.fill(0, renderY - 1, ACCENT_W, renderY + scaledLh + 1, accentColor);

            boolean doScale = scale != 1.0f;
            if (doScale) {
                g.pose().pushMatrix();
                g.pose().scale(scale, scale);
            }

            float invScale = doScale ? (1f / scale) : 1f;

            // Nach links rein-sliden beim Einblenden
            float slideOffset = (1f - s[1]) * -SLIDE_RANGE;
            float scaledTextX = (LEFT_MARGIN + slideOffset) * invScale;
            float scaledRenderY = renderY * invScale;

            int textColor = argbWithAlpha(C_WHITE, s[0]);
            g.text(mc.font, label, (int) scaledTextX, (int) scaledRenderY, textColor, true);

            if (doScale) {
                g.pose().popMatrix();
            }

            targetY += scaledLh + SPACING + 1;
        }
    }

    private static float approach(float current, float target, float speed) {
        float diff = target - current;
        if (Math.abs(diff) < 0.001f) return target;
        return current + diff * Math.min(1f, speed);
    }

    private static int argbWithAlpha(int rgb, float alpha) {
        int a = (int) (((rgb >> 24) & 0xFF) * Math.max(0f, Math.min(1f, alpha)));
        return (rgb & 0x00FFFFFF) | (a << 24);
    }
}