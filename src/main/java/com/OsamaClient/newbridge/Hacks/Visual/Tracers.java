package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.EntryPoint;

import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.Utils.Render.Color;
import com.OsamaClient.newbridge.Utils.Render.Events.EventHandler;
import com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent;
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

public class Tracers extends Module {
    public static Tracers INSTANCE;

    public float alphaValue = 0.8f;
    public float range = 128f;
    public EntityFilterPicker targetPicker;

    public Tracers() {
        super("Tracers", "Displays tracer lines to specified entities.", Category.VISUAL);
        INSTANCE = this;

        this.targetPicker = new EntityFilterPicker("Targets");
        this.settings.add(this.targetPicker.withDescription("Selects which entity types to draw tracers to."));

        this.settings.add(new Slider("Alpha", 0.0, 1.0, alphaValue, val -> alphaValue = val.floatValue()).withDescription("Controls the transparency of the tracer lines."));
        this.settings.add(new Slider("Range", 1.0, 128.0, range, val -> range = val.floatValue()).withDescription("Maximum distance for tracers to show."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    public static Tracers getInstance() {
        if (INSTANCE == null) INSTANCE = new Tracers();
        return INSTANCE;
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        float tickDelta = event.tickDelta;

        // Kamera-Position und Blickrichtung holen, um Clipping zu verhindern
        Camera camera = client.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        Vec3 forward = Vec3.directionFromRotation(camera.xRot(), camera.yRot());

        // Startpunkt leicht vor der Kamera
        double startX = camPos.x + forward.x * 0.2;
        double startY = camPos.y + forward.y * 0.2;
        double startZ = camPos.z + forward.z * 0.2;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == client.player || !entity.isAlive()) continue;

            if (client.player.distanceToSqr(entity) > range * range) continue;

            String filterKey = null;

            if (entity instanceof Player) {
                filterKey = "Players";
            } else if (entity instanceof ArmorStand) {
                filterKey = "ArmorStands";
            } else if (entity instanceof Enemy) {
                filterKey = "Hostiles";
            } else if (entity instanceof Animal) {
                filterKey = "Animals";
            } else if (entity instanceof Villager || entity instanceof WanderingTrader) {
                filterKey = "NPCs";
            }

            if (filterKey != null && targetPicker != null && targetPicker.isFilterEnabled(filterKey)) {
                int argb = targetPicker.getColor(filterKey);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int a = (int) (((argb >> 24) & 0xFF) / 255.0f * alphaValue * 255);

                double x = Mth.lerp(tickDelta, entity.xo, entity.getX());
                double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) + (entity.getBbHeight() / 2.0);
                double z = Mth.lerp(tickDelta, entity.zo, entity.getZ());

                Color color = new Color(r, g, b, Math.max(a, 50));

                event.renderer.lineNoDepth(startX, startY, startZ, x, y, z, color);
            }
        }
    }
}