package com.OsamaClient.newbridge;

import com.OsamaClient.newbridge.Hacks.Combat.AimAssist;
import com.OsamaClient.newbridge.Hacks.Combat.AutoDihhTap;
import com.OsamaClient.newbridge.Hacks.Misc.ModuleList;
import com.OsamaClient.newbridge.Hacks.Misc.Scaffold;
import com.OsamaClient.newbridge.UI.ClickGuiScreen;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.Utils.ChatHandler;
import com.OsamaClient.newbridge.Utils.Render.Events.EventBus;
import com.OsamaClient.newbridge.Utils.Render.Events.IEventBus;
import com.OsamaClient.newbridge.Utils.Render.OsamaRenderPipelines;
import com.OsamaClient.newbridge.Utils.Render.Renderer3D;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;

public class EntryPoint implements ClientModInitializer {

    public static EntryPoint INSTANCE;
    public static KeyMapping guiKeyBind;

    // ZENTRALE METEOR-KOMPONENTEN
    public static final IEventBus EVENT_BUS = new EventBus();
    public static Renderer3D RENDERER;

    private static final Identifier MODULE_LIST_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "module_list");
    private static final Identifier RENDER_2D_INVOKER_ID = Identifier.fromNamespaceAndPath("newbridge", "render_2d_invoker");
    String CategoryName = "Client";

    @Override
    public void onInitializeClient() {
        INSTANCE = this;

        // 1. Korrekte Erstellung der NoDepth-Pipelines mit den Vanilla-Debug-Snippets
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

        // 2. Initialisiere den Renderer mit allen 4 Pipelines (Normal + NoDepth)
        RENDERER = new Renderer3D(
                OsamaRenderPipelines.WORLD_COLORED_LINES,
                OsamaRenderPipelines.WORLD_COLORED,
                linesNoDepth,
                trianglesNoDepth
        );

        EVENT_BUS.subscribe(this);

        Identifier catId = Identifier.parse("client");
        KeyMapping.Category myCategory = KeyMapping.Category.register(catId);
        guiKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "GUI NewBridge",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                myCategory
        ));

        ModuleManager.init();
        Config.load();
        ChatHandler.register();

        LevelRenderEvents.START_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && AimAssist.INSTANCE != null && AimAssist.INSTANCE.enabled) {
                AimAssist.INSTANCE.onUpdate(client);
                Scaffold.INSTANCE.onUpdate(client);
            }
        });

        LevelRenderEvents.END_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            float tickDelta = client.getDeltaTracker().getGameTimeDeltaTicks();

            // 1. Renderer für diesen Frame starten
            RENDERER.begin();

            // 2. Events an alle 3D-Module (ESP, Tracers etc.) verteilen
            EVENT_BUS.post(com.OsamaClient.newbridge.Utils.Render.Events.Render3DEvent.get(
                    context.poseStack(),
                    RENDERER,
                    tickDelta
            ));

            RENDERER.render(context.poseStack());
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (guiKeyBind.consumeClick()) {
                client.gui.setScreen(new ClickGuiScreen());
            }

            if (client.gui.screen() == null) {
                ClickGuiScreen.keybinds.forEach((moduleName, boundKey) -> {
                    Module m = ModuleManager.getModuleByName(moduleName);
                    if (m == null) return;

                    if (InputConstants.isKeyDown(client.getWindow(), boundKey)) {
                        if (!m.keyAlreadyPressed) {
                            if (m instanceof AutoDihhTap tap) {
                                if (tap.getMode().equals("Manual")) {
                                    if (!tap.isEnabled()) {
                                        tap.setEnabled(true);
                                        tap.onEnable();
                                    } else {
                                        if (client.hitResult instanceof EntityHitResult entityHit &&
                                                entityHit.getEntity() instanceof LivingEntity target) {
                                            tap.triggerManual(target);
                                        }
                                    }
                                } else {
                                    tap.toggle();
                                }
                            } else {
                                m.toggle();
                            }
                            m.keyAlreadyPressed = true;
                        }
                    } else {
                        m.keyAlreadyPressed = false;
                    }
                });
            }

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

        // Bestehendes HUD-Element (ModuleList)
        HudElementRegistry.addLast(MODULE_LIST_HUD_ID, (guiGraphics, deltaTracker) -> ModuleList.draw(guiGraphics));

        // Triggert das Render2DEvent für Tracers und andere 2D-Elemente bei jedem Frame
        HudElementRegistry.addLast(RENDER_2D_INVOKER_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            float tickDelta = deltaTracker.getGameTimeDeltaTicks();

            EVENT_BUS.post(com.OsamaClient.newbridge.Utils.Render.Events.Render2DEvent.get(
                    guiGraphics, width, height, tickDelta
            ));
        });
    }

    public Renderer3D getRenderer3D() {
        return RENDERER;
    }

    public IEventBus getEventBus() {
        return EVENT_BUS;
    }
}