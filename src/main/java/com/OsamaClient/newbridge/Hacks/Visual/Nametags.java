package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.ColorPicker;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.ClickGui;
import com.OsamaClient.newbridge.UI.gui.render.Ui;
import com.OsamaClient.newbridge.UI.gui.render.UiRenderer;
import com.OsamaClient.newbridge.UI.gui.render.font.UiFont;
import com.OsamaClient.newbridge.UI.gui.util.ColorUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public class Nametags extends Module {

    public static Nametags instance;

    private static final int C_PANEL_BG = 0xFF0A0A0C;

    public float range = 128f;
    public double fontScale = 0.8;
    public String renderMode = "Both"; // None, Outline, Fill, Both
    public EntityFilterPicker targetPicker;

    // Neue Einstellungen für Farben, Rainbow und Sichtbarkeit
    public final ColorPicker nameColor = new ColorPicker("Name Color", 0xFFFFFFFF, null)
            .withDescription("Eigene Farbe für den Nametag-Text auswählen.");
    public final ToggleButton useThemeColor = new ToggleButton("Use Theme Accent", false, null)
            .withDescription("Nutzt die globale GUI-Akzentfarbe (unterstützt auch deren Rainbow).");
    public final ToggleButton rainbowText = new ToggleButton("Rainbow Text", false, null)
            .withDescription("Lässt die Textfarbe durch den Regenbogen wechseln.");
    public final Slider rainbowSpeed = new Slider("Rainbow Speed", 0.1, 5.0, 1.0, 0.1, null)
            .withDescription("Geschwindigkeit des Regenbogeneffekts.");
    public final ToggleButton dropShadow = new ToggleButton("Drop Shadow", true, null)
            .withDescription("Fügt einen Textschatten hinzu, um 'Weiß auf Hell' perfekt lesbar zu machen.");

    private static final UiRenderer renderer = new UiRenderer();
    private static final Ui ui = new Ui(renderer);

    public Nametags() {
        super("Nametags", "Displays 2D nametags above visible entities", Category.VISUAL);
        instance = this;

        this.targetPicker = new EntityFilterPicker("Targets");
        this.settings.add(this.targetPicker.withDescription("Selects which entities to show nametags for."));

        List<String> modes = List.of("None", "Outline", "Fill", "Both");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val)
                .withDescription("Selects box rendering style."));
        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue())
                .withDescription("Maximum distance for nametags."));
        this.settings.add(new Slider("Scale", 0.5, 2.0, fontScale, val -> fontScale = val)
                .withDescription("Adjusts nametag text scale."));

        // Fügt die neuen Settings zum Menü (ClickGui) hinzu
        this.settings.add(this.nameColor);
        this.settings.add(this.useThemeColor);
        this.settings.add(this.rainbowText);
        this.settings.add(this.rainbowSpeed);
        this.settings.add(this.dropShadow);
    }

    /**
     * Berechnet die finale Farbe des Textes basierend auf den Einstellungen.
     */
    public int getCustomTextColor() {
        if (useThemeColor.enabled) {
            return Theme.accent(); // Greift auf ClickGui-Farbe inkl. Rainbow zu
        }
        if (rainbowText.enabled) {
            float speed = (float) rainbowSpeed.getValue();
            float hue = (float) ((System.currentTimeMillis() % 100_000L) * 0.0001 * speed);
            return ColorUtil.hsvToRgb(hue, 0.85f, 1f) | 0xFF000000;
        }
        return nameColor.getColor() | 0xFF000000;
    }

    public static void draw() {
        if (instance == null || !instance.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden() || mc.gameRenderer.mainCamera() == null) return;
        // the ClickGui is drawn with its own renderer/pass right after ours; without this check the
        // world-space nametags would render on top of (or clip through) the open panels
        if (ClickGui.INSTANCE.isOpen() || mc.gui.screen() != null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        renderer.begin();
        ui.scale = Theme.scale() * (float) instance.fontScale;
        ui.alpha = 1f;
        ui.hoverBlocked = true;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !entity.isAlive()) continue;

            double distSqr = mc.player.distanceToSqr(entity);
            if (distSqr > instance.range * instance.range) continue;

            String filterKey = instance.getFilterKey(entity);
            if (filterKey == null || instance.targetPicker == null || !instance.targetPicker.isFilterEnabled(filterKey)) continue;

            double x = Mth.lerp(partialTick, entity.xo, entity.getX());
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.3;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
            Vector3f fb = projectToFramebuffer(x, y, z, camPos, camera, mc);
            if (fb == null) continue;

            String text = entity.getDisplayName().getString();
            int outlineColor = instance.targetPicker.getColor(filterKey);
            double distance = Math.sqrt(distSqr);

            renderTag(text, distance, fb.x() / ui.scale, fb.y() / ui.scale, outlineColor);
        }

        renderer.flush();
        UiFont.collect(600);
    }

    private static void renderTag(String text, double distance, float x, float y, int outlineColor) {
        float size = Theme.FONT_SMALL;
        String distText = String.format("%.1fm", distance);

        float tw = ui.textWidth(text, size);
        float dw = ui.textWidth(distText, size * 0.85f);
        float lineH = size * 1.35f;

        float distFactor = 1f - Mth.clamp((float) (distance / instance.range), 0f, 1f);
        float fade = 0.3f + 0.7f * distFactor;

        String mode = instance.renderMode;

        // Die Box wurde etwas vergrößert, damit das Layout luftiger und nicht so reingequetscht aussieht
        float boxW = Math.max(tw, dw) + 12f;
        float boxH = lineH * 2f + 5f;
        float boxX = x - boxW * 0.5f;
        float boxY = y - lineH - 5f;
        float radius = Theme.radius() * 0.8f;

        ui.alpha = fade;
        if (!mode.equals("None")) {
            if (mode.equals("Fill") || mode.equals("Both")) {
                // Deckkraft des Hintergrunds etwas verstärkt für besseren Kontrast
                ui.round(boxX, boxY, boxW, boxH, radius, ColorUtil.alpha(C_PANEL_BG, 0.85f));
            }
            if (mode.equals("Outline") || mode.equals("Both")) {
                ui.outline(boxX, boxY, boxW, boxH, radius, 1f, ColorUtil.alpha(outlineColor, 0.85f));
            }
        }

        int finalTextColor = instance.getCustomTextColor();

        // Schatten rendern (leicht versetzt), falls in den Settings aktiviert
        if (instance.dropShadow.enabled) {
            ui.textCentered(text, x + 0.5f, boxY + 2.5f, lineH, size, ColorUtil.alpha(0xFF000000, fade * 0.9f));
            ui.textCentered(distText, x + 0.5f, boxY + lineH + 1.5f, lineH, size * 0.85f, ColorUtil.alpha(0xFF000000, fade * 0.9f));
        }

        // Regulärer Text (obendrüber)
        ui.textCentered(text, x, boxY + 2.0f, lineH, size, finalTextColor);
        ui.textCentered(distText, x, boxY + lineH + 1.0f, lineH, size * 0.85f, Theme.TEXT_DIM);
        ui.alpha = 1f;
    }

    private String getFilterKey(Entity entity) {
        if (entity instanceof Player) return "Players";
        if (entity instanceof ArmorStand) return "ArmorStands";
        if (entity instanceof Enemy) return "Hostiles";
        if (entity instanceof Animal) return "Animals";
        if (entity instanceof Villager || entity instanceof WanderingTrader) return "NPCs";
        return null;
    }

    private static Vector3f projectToFramebuffer(double worldX, double worldY, double worldZ, Vec3 camPos, Camera camera, Minecraft mc) {
        float dx = (float) (worldX - camPos.x);
        float dy = (float) (worldY - camPos.y);
        float dz = (float) (worldZ - camPos.z);

        Quaternionf inverseRotation = new Quaternionf(camera.rotation()).conjugate();
        Vector4f viewSpace = new Vector4f(dx, dy, dz, 1.0f).rotate(inverseRotation);

        float z = -viewSpace.z;
        if (z <= 0.05f) return null;

        double baseFov = mc.options.fov().get();
        float effectScale = mc.options.fovEffectScale().get().floatValue();
        boolean isFirstPerson = mc.options.getCameraType().isFirstPerson();
        float fovModifier = mc.player.getFieldOfViewModifier(isFirstPerson, effectScale);
        double fov = baseFov * fovModifier;

        double halfFovRad = Math.toRadians(fov / 2.0);
        float scaleY = (float) (1.0 / Math.tan(halfFovRad));

        int fbWidth = mc.getWindow().getWidth();
        int fbHeight = mc.getWindow().getHeight();
        double aspect = (double) fbWidth / fbHeight;
        float scaleX = (float) (scaleY / aspect);

        float ndcX = (viewSpace.x * scaleX) / z;
        float ndcY = (viewSpace.y * scaleY) / z;

        float pixelX = (float) ((1.0 + ndcX) * 0.5 * fbWidth);
        float pixelY = (float) ((1.0 - ndcY) * 0.5 * fbHeight);

        return new Vector3f(pixelX, pixelY, z);
    }
}