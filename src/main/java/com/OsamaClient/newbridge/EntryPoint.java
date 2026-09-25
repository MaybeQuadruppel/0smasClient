package com.OsamaClient.newbridge;

import com.OsamaClient.newbridge.Hacks.Combat.AimAssist;
import com.OsamaClient.newbridge.Hacks.Misc.ModuleList;
import com.OsamaClient.newbridge.Hacks.Misc.Scaffold;
import com.OsamaClient.newbridge.Hacks.Visual.Nametags;
import com.OsamaClient.newbridge.Hacks.Visual.TeammateList;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.render.UiPipelines;
import com.OsamaClient.newbridge.Utils.ChatHandler;
import com.OsamaClient.newbridge.event.EventBus;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public class EntryPoint implements ClientModInitializer {

    public static EntryPoint INSTANCE;

    public static final EventBus EVENT_BUS = new EventBus();

    private static final Identifier TEAMMATE_LIST_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "teammate_list");
    private static final Identifier MODULE_LIST_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "module_list");
    private static final Identifier NAMETAGS_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "nametags");

    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        // NoDepth-Pipelines (von den ESP-/Render-Modulen genutzt)
        RenderPipeline linesNoDepth = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("newbridge", "pipeline/lines_no_depth"))
                        .withDepthStencilState(Optional.empty())
                        .build()
        );

        RenderPipeline trianglesNoDepth = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("newbridge", "pipeline/triangles_no_depth"))
                        .withDepthStencilState(Optional.empty())
                        .build()
        );

        // Eigene Pipelines der ClickGUI (SDF-Formen + FreeType-Text)
        UiPipelines.register();

        ModuleManager.init();
        ChatHandler.register();
        com.OsamaClient.newbridge.UI.gui.Keybinds.register();

        LevelRenderEvents.START_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && AimAssist.INSTANCE != null && AimAssist.INSTANCE.enabled) {
                AimAssist.INSTANCE.onUpdate(client);
                Scaffold.INSTANCE.onUpdate(client);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (client.options.keyAttack.isDown()) {
                HitResult targetResult = client.hitResult;
                if (targetResult != null && targetResult.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) targetResult;
                    if (entityHit.getEntity() instanceof LivingEntity target) {
                        if (AimAssist.INSTANCE != null && AimAssist.INSTANCE.enabled) {
                            AimAssist.INSTANCE.setLockedTarget(target);
                        }
                    }
                }
            }

            if (ModuleManager.modules != null) {
                for (Module m : ModuleManager.modules) {
                    if (m.enabled) {
                        m.onTick(client);
                    }
                }
            }
        });

        // HUD-Elemente der Module (kein GUI-System, nur die Modul-eigenen Overlays)
        HudElementRegistry.addLast(TEAMMATE_LIST_HUD_ID, (guiGraphics, deltaTracker) -> TeammateList.draw(guiGraphics));
        HudElementRegistry.addLast(MODULE_LIST_HUD_ID, (guiGraphics, deltaTracker) -> ModuleList.draw(guiGraphics));
        HudElementRegistry.addLast(NAMETAGS_HUD_ID, (guiGraphics, deltaTracker) -> Nametags.draw(guiGraphics));
    }
}
