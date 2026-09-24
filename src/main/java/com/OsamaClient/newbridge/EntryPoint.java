package com.OsamaClient.newbridge;

import com.OsamaClient.newbridge.Hacks.Combat.AimAssist;
import com.OsamaClient.newbridge.Hacks.Combat.AutoDihhTap;
import com.OsamaClient.newbridge.Hacks.Misc.ModuleList;
import com.OsamaClient.newbridge.Hacks.Misc.Scaffold;
import com.OsamaClient.newbridge.Hacks.Visual.HudOverlay;
import com.OsamaClient.newbridge.Hacks.Visual.Nametags;
import com.OsamaClient.newbridge.Hacks.Visual.TeammateList;
import com.OsamaClient.newbridge.UI.ClickGuiScreen;
import com.OsamaClient.newbridge.UI.ProfileManager;
import com.OsamaClient.newbridge.UI.UISettings;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.Utils.ChatHandler;
import com.OsamaClient.newbridge.event.EventBus;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public class EntryPoint implements ClientModInitializer {

    public static EntryPoint INSTANCE;

    /** Edge-Detection für die (rohe, nicht als KeyMapping registrierte)
     *  GUI-Öffnen-Taste - siehe unten im Tick-Handler. */
    private static boolean guiOpenKeyWasDown = false;

    public static final EventBus EVENT_BUS = new EventBus();

    private static final Identifier TEAMMATE_LIST_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "teammate_list");
    private static final Identifier MODULE_LIST_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "module_list");
    private static final Identifier RENDER_2D_INVOKER_ID = Identifier.fromNamespaceAndPath("newbridge", "render_2d_invoker");
    private static final Identifier NAMETAGS_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "nametags"); // <-- NEU
    private static final Identifier HUD_OVERLAY_ID = Identifier.fromNamespaceAndPath("newbridge", "hud_overlay");
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

        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // GUI-Öffnen-Taste: bewusst KEIN Minecraft-KeyMapping (würde unter
            // Optionen -> Steuerung auftauchen und dort umbindbar sein).
            // Stattdessen roher GLFW-Keycode aus UISettings.guiOpenKey, nur
            // über die ClickGUI selbst (UI Settings) änderbar. Edge-Detection
            // wie bei den normalen Modul-Keybinds, damit ein Tastendruck nur
            // einmal auslöst statt jeden Tick erneut.
            boolean guiOpenKeyIsDown = InputConstants.isKeyDown(client.getWindow(), UISettings.guiOpenKey);
            if (guiOpenKeyIsDown && !guiOpenKeyWasDown) {
                client.gui.setScreen(new ClickGuiScreen());
            }
            guiOpenKeyWasDown = guiOpenKeyIsDown;

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

                            // Bonus-Fix: Hotkey-Toggle bei geschlossener GUI hat vorher
                            // NIE gespeichert (nur ein Öffnen+Schließen der ClickGUI hat
                            // persistiert). Jetzt konsistent mit den In-GUI-Änderungen.
                            Config.save();
                            ProfileManager.syncActiveProfile();
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
        HudElementRegistry.addLast(TEAMMATE_LIST_HUD_ID, (guiGraphics, deltaTracker) -> TeammateList.draw(guiGraphics));
        HudElementRegistry.addLast(MODULE_LIST_HUD_ID, (guiGraphics, deltaTracker) -> ModuleList.draw(guiGraphics));
        HudElementRegistry.addLast(NAMETAGS_HUD_ID, (guiGraphics, deltaTracker) -> Nametags.draw(guiGraphics));
        HudElementRegistry.addLast(HUD_OVERLAY_ID, (guiGraphics, deltaTracker) -> HudOverlay.draw(guiGraphics));
        // Triggert das Render2DEvent für Tracers und andere 2D-Elemente bei jedem Frame
        HudElementRegistry.addLast(RENDER_2D_INVOKER_ID, (guiGraphics, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            int width = client.getWindow().getGuiScaledWidth();
            int height = client.getWindow().getGuiScaledHeight();
            float tickDelta = deltaTracker.getGameTimeDeltaTicks();

        });
    }


}