package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.ModeButton;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public class Nametags extends Module {

    public static Nametags instance;

    // ── Black & White Palette ─────────────────────────────────────────────
    private static final int C_PANEL_BG     = 0xB2000000; // sehr transparentes Panel
    private static final int C_TEXT         = 0xFFEEEEEE; // near-white text
    private static final int C_TEXT_DIM     = 0xFF999999; // gedämpfter Text (Distanz)
    private static final int C_ACCENT_DIM   = 0xFF999999; // grey accent (Outline)

    public float range = 128f;
    public double fontScale = 0.8;
    public String renderMode = "None"; // Fill, Outline, Both, None
    public EntityFilterPicker targetPicker;

    public Nametags() {
        super("Nametags", "Displays 2D nametags above visible entities", Category.VISUAL);
        instance = this;

        this.targetPicker = new EntityFilterPicker("Targets");
        this.settings.add(this.targetPicker.withDescription("Selects which entities to show nametags for."));

        List<String> modes = List.of("None", "Outline", "Fill", "Both");
        this.settings.add(new ModeButton("Mode", modes, modes.indexOf(renderMode), val -> renderMode = val)
                .withDescription("Selects box rendering style (Fill, Outline, Both, None)."));

        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue())
                .withDescription("Maximum distance for nametags."));
        this.settings.add(new Slider("Scale", 0.5, 2.0, fontScale, val -> fontScale = val)
                .withDescription("Adjusts nametag text scale."));
    }

    public static void draw(GuiGraphicsExtractor g) {
        if (instance == null || !instance.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.gui.hud.isHidden() || mc.gameRenderer.mainCamera() == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !entity.isAlive()) continue;

            double distSqr = mc.player.distanceToSqr(entity);
            if (distSqr > instance.range * instance.range) continue;

            String filterKey = instance.getFilterKey(entity);
            if (filterKey == null || instance.targetPicker == null || !instance.targetPicker.isFilterEnabled(filterKey)) continue;

            double x = Mth.lerp(partialTick, entity.xo, entity.getX());
            double y = Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getBbHeight() + 0.3;
            double z = Mth.lerp(partialTick, entity.zo, entity.getZ());
            Vec3 headPos = new Vec3(x, y, z);
            Vector3f screenPos = projectToScreen(x, y, z, camPos, camera, mc);
            if (screenPos == null) continue;

            String text = entity.getDisplayName().getString();
            int accentColor = instance.targetPicker.getColor(filterKey);
            double distance = Math.sqrt(distSqr);

            renderTag(g, mc, text, distance, screenPos.x(), screenPos.y(), accentColor);
        }
    }

    private static void renderTag(GuiGraphicsExtractor g, Minecraft mc, String text, double distance, float x, float y, int accentColor) {
        float scale = (float) instance.fontScale;
        String distText = String.format("%.1fm", distance);

        int textWidth = mc.font.width(text);
        int distWidth = (int) (mc.font.width(distText) * 0.85f);
        int lineHeight = mc.font.lineHeight;

        // Distanz-Fade: näher = kräftiger, am Range-Limit = fast unsichtbar
        float distFactor = 1.0f - Mth.clamp((float) (distance / instance.range), 0f, 1f);
        int alphaBoost = (int) (80 + 150 * distFactor); // 80..230

        boolean doScale = scale != 1.0f;
        if (doScale) {
            g.pose().pushMatrix();
            g.pose().scale(scale, scale);
        }
        float invScale = doScale ? (1f / scale) : 1f;

        // Koordinaten innerhalb der skalieren Matrix berechnen
        float scaledX = x * invScale;
        float scaledY = y * invScale;

        String mode = instance.renderMode;
        if (!mode.equals("None")) {
            int boxWidth = Math.max(textWidth, distWidth) + 8;
            int boxHeight = (int) (lineHeight * 1.85f) + 4;
            int rectX1 = (int) (scaledX - boxWidth / 2f);
            int rectY1 = (int) (scaledY - lineHeight - 2f);
            int rectX2 = rectX1 + boxWidth;
            int rectY2 = rectY1 + boxHeight;

            if (mode.equals("Fill") || mode.equals("Both")) {
                int fillAlpha = Math.min(255, (C_PANEL_BG >>> 24) * alphaBoost / 255);
                int fillColor = (fillAlpha << 24) | (C_PANEL_BG & 0xFFFFFF);
                g.fill(rectX1, rectY1, rectX2, rectY2, fillColor);
            }
            if (mode.equals("Outline") || mode.equals("Both")) {
                int outlineAlpha = Math.min(255, alphaBoost);
                int outlineColor = (outlineAlpha << 24) | (C_ACCENT_DIM & 0xFFFFFF);
                g.fill(rectX1, rectY1, rectX2, rectY1 + 1, outlineColor);
                g.fill(rectX1, rectY2 - 1, rectX2, rectY2, outlineColor);
                g.fill(rectX1, rectY1, rectX1 + 1, rectY2, outlineColor);
                g.fill(rectX2 - 1, rectY1, rectX2, rectY2, outlineColor);
            }
        }

        int nameAlpha = Math.min(255, alphaBoost + 25);
        int distAlpha = Math.min(255, alphaBoost);
        int nameColor = (nameAlpha << 24) | (C_TEXT & 0xFFFFFF);
        int distColor = (distAlpha << 24) | (C_TEXT_DIM & 0xFFFFFF);

        float nameX = scaledX - textWidth / 2f;
        float nameY = scaledY - lineHeight;
        g.text(mc.font, text, (int) nameX, (int) nameY, nameColor, true);

        float distX = scaledX - distWidth / 2f;
        float distY = scaledY + 1f;
        g.text(mc.font, distText, (int) distX, (int) distY, distColor, true);

        if (doScale) {
            g.pose().popMatrix();
        }
    }


    private static Vector3f projectToScreen(double worldX, double worldY, double worldZ, Vec3 camPos, Camera camera, Minecraft mc) {
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
        double aspect = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getScreenHeight();
        float scaleX = (float) (scaleY / aspect);

        float ndcX = (viewSpace.x * scaleX) / z;
        float ndcY = (viewSpace.y * scaleY) / z;

        int guiWidth = mc.getWindow().getGuiScaledWidth();
        int guiHeight = mc.getWindow().getGuiScaledHeight();

        float pixelX = (float) ((1.0 + ndcX) * 0.5 * guiWidth);
        float pixelY = (float) ((1.0 - ndcY) * 0.5 * guiHeight);

        return new Vector3f(pixelX, pixelY, z);
    }

    private String getFilterKey(Entity entity) {
        if (entity instanceof Player) return "Players";
        if (entity instanceof ArmorStand) return "ArmorStands";
        if (entity instanceof Enemy) return "Hostiles";
        if (entity instanceof Animal) return "Animals";
        if (entity instanceof Villager || entity instanceof WanderingTrader) return "NPCs";
        return null;
    }
}