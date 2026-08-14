package com.OsamaClient.newbridge.Hacks.Visual.ESP;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;
import com.OsamaClient.newbridge.UI.components.EntityFilterPicker;
import com.OsamaClient.newbridge.Utils.Render.Color;
import com.OsamaClient.newbridge.Utils.Render.Events.EventHandler;
import com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent;
import com.OsamaClient.newbridge.Utils.Render.Renderer3D;
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

public class PlayerESP extends Module {
    public static PlayerESP INSTANCE;

    public float alphaValue = 0.4f;
    public float range = 128;
    public EntityFilterPicker targetPicker;

    public PlayerESP() {
        super("EntityESP", "Lets you See Entities by their Threadlevels", Category.VISUAL);
        INSTANCE = this;

        this.targetPicker = new EntityFilterPicker("Targets");
        this.settings.add(this.targetPicker.withDescription("Selects which entity types to highlight with ESP."));

        this.settings.add(new Slider("Alpha", 0.0, 1.0, (double) alphaValue, val -> alphaValue = val.floatValue()).withDescription("Controls the global transparency multiplier of the ESP rendering."));
        this.settings.add(new Slider("Range", 1.0, 128.0, (double) range, val -> range = val.floatValue()).withDescription("Sets the maximum distance at which entities are highlighted."));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    public static PlayerESP getInstance() {
        if (INSTANCE == null) INSTANCE = new PlayerESP();
        return INSTANCE;
    }

    @EventHandler
    public void onRender3D(Render3DEvent event) {
        if (!this.enabled) return;

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;

        float tickDelta = event.tickDelta;

        for (Entity entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity) || entity == client.player || !entity.isAlive()) continue;

            // Distanz-Check basierend auf dem Slider
            if (client.player.distanceToSqr(entity) > range * range) continue;

            String filterKey = null;

            // Zuordnung der Entitäten zu den Schlüsseln im EntityFilterPicker
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
                // Farbe und Alpha aus dem Picker auslesen
                int argb = targetPicker.getColor(filterKey);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = (argb & 0xFF) & 0xFF; // Korrigiert auf b
                int a = (int) (((argb >> 24) & 0xFF) / 255.0f * alphaValue * 255);

                // Interpolierte Position relativ zur Kamera
                double x = Mth.lerp(tickDelta, entity.xo, entity.getX()) - event.offsetX;
                double y = Mth.lerp(tickDelta, entity.yo, entity.getY()) - event.offsetY;
                double z = Mth.lerp(tickDelta, entity.zo, entity.getZ()) - event.offsetZ;

                float w = entity.getBbWidth() / 2f;
                float h = entity.getBbHeight();
                float yaw = Mth.lerp(tickDelta, ((LivingEntity) entity).yBodyRotO, ((LivingEntity) entity).yBodyRot);

                // Box über den neuen Renderer rendern (inkl. Rotation)
                event.renderer.boxRotatedNoDepth(
                        x, y, z,
                        w, h, yaw,
                        new Color(r, g, b, (int)(a * 0.4f)), // Füllung etwas transparenter
                        new Color(r, g, b, a),               // Deckende Outline
                        Renderer3D.ShapeMode.BOTH
                );
            }
        }
    }
}