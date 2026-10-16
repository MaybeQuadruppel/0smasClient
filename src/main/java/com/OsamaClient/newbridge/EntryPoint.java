package com.OsamaClient.newbridge;

import com.OsamaClient.newbridge.Hacks.Combat.AimAssist;
import com.OsamaClient.newbridge.Hacks.Visual.ModuleList;
import com.OsamaClient.newbridge.Hacks.Misc.Scaffold;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import com.OsamaClient.newbridge.UI.gui.render.UiPipelines;
import com.OsamaClient.newbridge.Utils.ChatHandler;
import com.OsamaClient.newbridge.event.EventBus;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Waypoints",
                "Waypoint manager: all waypoints, edit, colors, go to", com.OsamaClient.newbridge.Hacks.Misc.WaypointsWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Profiles",
                "Profile manager: save, load, compare and bind setups", com.OsamaClient.newbridge.Hacks.Client.ProfilesWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Stashes",
                "Found stashes: value, contents, distance; add waypoint / go", com.OsamaClient.newbridge.Hacks.Misc.stash.StashesWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Macros",
                "Macros: keys that send messages, commands and client commands", com.OsamaClient.newbridge.Hacks.Client.macro.MacrosWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Friends",
                "Friends: who is online or nearby, last seen, add / remove, message, go to", com.OsamaClient.newbridge.Hacks.Client.FriendsWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Keybinds",
                "Keybind overview: a real keyboard showing module and vanilla binds and any conflicts", com.OsamaClient.newbridge.UI.gui.window.KeyboardWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Shortcuts",
                "Every shortcut at a glance: mouse and keyboard actions, your module keybinds and the chat commands (F1)", com.OsamaClient.newbridge.UI.gui.window.ShortcutsWindow::new);
        com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.registerDock("Crosshair",
                "Pixel editor: draw your own crosshair with colours", com.OsamaClient.newbridge.UI.gui.window.CustomCrosshairWindow::new);
        com.OsamaClient.newbridge.Utils.ChatSuggest.register();
        com.OsamaClient.newbridge.UI.gui.Keybinds.register();
        // Alles speichern/laden (Module, Keybinds, Einstellungen, Panels, GUI-Theme)
        com.OsamaClient.newbridge.config.Config.init();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                c -> com.OsamaClient.newbridge.UI.gui.ClickGui.INSTANCE.shutdown());

        LevelRenderEvents.START_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && AimAssist.INSTANCE != null && AimAssist.INSTANCE.enabled) {
                AimAssist.INSTANCE.onUpdate(client);
                Scaffold.INSTANCE.onUpdate(client);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> com.OsamaClient.newbridge.Hacks.Dupeing.PacketHub.onClientTick());

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

            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.tick();

            // Only one place writes movement keys and mining input each tick.
            Navigator.INSTANCE.onTick(client);
        });

        // TeammateList, Nametags, ModuleList und HudOverlay hängen NICHT mehr hier über
        // HudElementRegistry - sie laufen alle über dieselbe eigene UiRenderer-Pipeline und werden
        // deshalb zentral vom GameRendererUiMixin aus gezeichnet (siehe dessen Klassenkommentar).
    }
}
