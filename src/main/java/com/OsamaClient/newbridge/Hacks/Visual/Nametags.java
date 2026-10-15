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
            .withDescription("Pick your own colour for the nametag text.");
    public final ToggleButton useThemeColor = new ToggleButton("Use Theme Accent", false, null)
            .withDescription("Uses the global GUI accent colour (including its rainbow mode).");
    public final ToggleButton rainbowText = new ToggleButton("Rainbow Text", false, null)
            .withDescription("Cycles the text colour through the rainbow.");
    public final Slider rainbowSpeed = new Slider("Rainbow Speed", 0.1, 5.0, 1.0, 0.1, null)
            .withDescription("Speed of the rainbow effect.");
    public final ToggleButton healthBar = new ToggleButton("Health Bar", true, null)
            .withDescription("Shows a health bar and the health value in the tag");
    public final ToggleButton dropShadow = new ToggleButton("Drop Shadow", true, null)
            .withDescription("Adds a text shadow so white text stays readable on bright backgrounds.");

    public final ToggleButton armorIcons = new ToggleButton("Armor Icons", true, null)
            .withDescription("Shows the armor pieces (and the held item) above the tag with a durability bar under each.");
    public final ToggleButton heldItem = new ToggleButton("Held Item", true, null)
            .withDescription("Also show the item in the main hand next to the armor.");
    public final ToggleButton durabilityText = new ToggleButton("Durability %", false, null)
            .withDescription("Write the remaining durability in percent above every icon.");

    /** Items drawn in the last frame per entity name (tests read this). */
    public static final java.util.Map<String, java.util.List<String>> lastIcons = new java.util.HashMap<>();

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
        this.settings.add(this.healthBar);
        this.settings.add(this.dropShadow);
        this.settings.add(this.armorIcons);
        this.settings.add(this.heldItem);
        this.settings.add(this.durabilityText);
    }

    /** Equipment shown above a tag: head, chest, legs, feet, then the main hand (empty slots are skipped). */
    public java.util.List<net.minecraft.world.item.ItemStack> iconItems(LivingEntity e) {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>();
        if (!armorIcons.enabled) return out;
        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            var st = e.getItemBySlot(slot);
            if (!st.isEmpty()) out.add(st);
        }
        if (heldItem.enabled && !e.getMainHandItem().isEmpty()) out.add(e.getMainHandItem());
        return out;
    }

    /** Remaining durability 0..1, or -1 for items without durability. */
    public static float durability(net.minecraft.world.item.ItemStack st) {
        if (!st.isDamageableItem() || st.getMaxDamage() <= 0) return -1f;
        return Mth.clamp(1f - st.getDamageValue() / (float) st.getMaxDamage(), 0f, 1f);
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
        lastIcons.clear();

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

            renderTag(text, distance, living, fb.x() / ui.scale, fb.y() / ui.scale, outlineColor);
        }

        renderer.flush();
        UiFont.collect(600);
    }

    private static void renderTag(String text, double distance, LivingEntity living, float x, float y, int outlineColor) {
        float size = Theme.FONT_SMALL;
        String distText = String.format("%.0fm", distance);
        boolean bar = instance.healthBar.enabled;
        float hp = Mth.clamp(living.getHealth() / Math.max(1f, living.getMaxHealth()), 0f, 1f);
        String hpText = String.valueOf(Math.round(living.getHealth()));

        float sub = size * 0.8f;
        float tw = ui.textWidth(text, size);
        float dw = ui.textWidth(distText, sub);
        float hw = bar ? ui.textWidth(hpText, sub) : 0f;
        float lineH = size * 1.35f;
        float subH = sub * 1.35f;

        float distFactor = 1f - Mth.clamp((float) (distance / instance.range), 0f, 1f);
        float fade = 0.35f + 0.65f * distFactor;

        String mode = instance.renderMode;

        float boxW = Math.max(tw + 10f, bar ? 38f : dw + 10f);
        float boxH = lineH + 2f + subH + (bar ? 3f : 0f) + 3f;
        float boxX = x - boxW * 0.5f;
        float boxY = y - boxH - 2f;
        float radius = Theme.radius() * 0.8f;

        ui.alpha = fade;
        if (!mode.equals("None")) {
            if (mode.equals("Fill") || mode.equals("Both")) {
                ui.round(boxX, boxY, boxW, boxH, radius, ColorUtil.alpha(C_PANEL_BG, 0.85f));
            }
            if (mode.equals("Outline") || mode.equals("Both")) {
                ui.outline(boxX, boxY, boxW, boxH, radius, 1f, ColorUtil.alpha(outlineColor, 0.85f));
            }
        }
        // Akzent-Streifen oben in der Filterfarbe
        ui.round(boxX + radius, boxY + 1f, boxW - radius * 2, 1.2f, 0.6f, ColorUtil.alpha(outlineColor, 0.9f));

        int finalTextColor = instance.getCustomTextColor();
        float ty = boxY + 2.5f;
        if (instance.dropShadow.enabled) {
            ui.textCentered(text, x + 0.5f, ty + 0.5f, lineH, size, ColorUtil.alpha(0xFF000000, fade * 0.9f));
        }
        ui.textCentered(text, x, ty, lineH, size, finalTextColor);

        float sy = ty + lineH + 0.5f;
        if (bar) {
            float bw = boxW - 8f;
            float bx = boxX + 4f;
            ui.round(bx, sy, bw, 2f, 1f, ColorUtil.alpha(0xFF000000, 0.55f));
            int hc = hp > 0.6f ? 0xFF52E084 : hp > 0.3f ? 0xFFF0C040 : 0xFFE0525A;
            ui.round(bx, sy, Math.max(2f, bw * hp), 2f, 1f, hc);
            sy += 3f;
            ui.text(hpText, boxX + 4f, sy, subH, sub, hc);
            ui.text(distText, boxX + boxW - 4f - dw, sy, subH, sub, Theme.TEXT_DIM);
        } else {
            ui.textCentered(distText, x, sy, subH, sub, Theme.TEXT_DIM);
        }
        drawIcons(living, x, boxY, fade);
        ui.alpha = 1f;
    }

    /** Armor / held item icons in a row above the tag; icons are drawn by the vanilla item layer (HudIcons). */
    private static void drawIcons(LivingEntity living, float cx, float boxY, float fade) {
        var items = instance.iconItems(living);
        if (items.isEmpty()) return;
        float icon = 9f, gap = 1f;
        float rowW = items.size() * icon + (items.size() - 1) * gap;
        float ix = cx - rowW / 2f;
        float iy = boxY - icon - 3f;
        java.util.List<String> names = new java.util.ArrayList<>();
        for (var st : items) {
            HudIcons.item(st, ix * ui.scale, iy * ui.scale, icon * ui.scale);
            float d = durability(st);
            names.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath() + (d >= 0 ? "@" + Math.round(d * 100) : ""));
            if (d >= 0) {
                int c = d > 0.6f ? 0xFF52E084 : d > 0.3f ? 0xFFF0C040 : 0xFFE0525A;
                ui.alpha = fade;
                ui.rect(ix + 0.5f, iy + icon + 0.3f, icon - 1f, 1.1f, 0xC0000000);
                ui.rect(ix + 0.5f, iy + icon + 0.3f, Math.max(0.4f, (icon - 1f) * d), 1.1f, c);
                if (instance.durabilityText.enabled)
                    ui.textCentered(Math.round(d * 100) + "", ix + icon / 2f, iy - 4.6f, 4.5f, 3.8f, c);
            }
            ix += icon + gap;
        }
        lastIcons.put(living.getName().getString(), names);
    }

    private String getFilterKey(Entity entity) {
        if (entity instanceof Player) return "Players";
        if (entity instanceof ArmorStand) return "ArmorStands";
        if (entity instanceof Enemy) return "Hostiles";
        if (entity instanceof Animal) return "Animals";
        if (entity instanceof Villager || entity instanceof WanderingTrader) return "NPCs";
        return null;
    }

    /**
     * Welt -> Framebuffer-Pixel. Nutzt exakt die View-Projection-Matrix der Kamera (inkl. Sprint-/Effekt-FOV,
     * Wasser, Aspect), statt das FOV selbst nachzurechnen - so kleben Tags/Waypoints beim Sprinten fest.
     * z = Tiefe vor der Kamera; null wenn hinter der Kamera.
     */
    public static Vector3f projectToFramebuffer(double worldX, double worldY, double worldZ, Vec3 camPos, Camera camera, Minecraft mc) {
        float dx = (float) (worldX - camPos.x);
        float dy = (float) (worldY - camPos.y);
        float dz = (float) (worldZ - camPos.z);

        Vector4f clip = new Vector4f(dx, dy, dz, 1.0f);
        camera.getViewRotationProjectionMatrix(new org.joml.Matrix4f()).transform(clip);
        if (clip.w <= 0.05f) return null;

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        int fbWidth = mc.getWindow().getWidth();
        int fbHeight = mc.getWindow().getHeight();
        float pixelX = (1.0f + ndcX) * 0.5f * fbWidth;
        float pixelY = (1.0f - ndcY) * 0.5f * fbHeight;
        return new Vector3f(pixelX, pixelY, clip.w);
    }
}