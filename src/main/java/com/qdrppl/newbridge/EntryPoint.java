package com.qdrppl.newbridge;

import com.qdrppl.newbridge.Hacks.Combat.AimAssist;
import com.qdrppl.newbridge.Hacks.Visual.ESP.*;
import com.qdrppl.newbridge.UI.ClickGuiScreen;
import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

public class EntryPoint implements ClientModInitializer {

    public static KeyMapping guiKeyBind;

    @Override
    public void onInitializeClient() {
        // Keybinding Initialisierung
        guiKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "GUI NewBridge",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "FICKEN"
        ));

        ModuleManager.init();
        RenderUtils.getInstance().init(Minecraft.getInstance());
        PlayerESP.getInstance().init(Minecraft.getInstance());

        // Rendering Event für AimAssist (Smoothing/Visuals)
        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.START_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && AimAssist.INSTANCE != null && AimAssist.INSTANCE.enabled) {
                float tickDelta = client.getFps(); // Für 1.21.1 / Fabric
                AimAssist.INSTANCE.onRender(client, tickDelta);
            }
        });

        // Haupt-Logik im START_CLIENT_TICK (Safe gegen Vulcan/Grim)
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // GUI öffnen
            while (guiKeyBind.consumeClick()) {
                client.setScreen(new ClickGuiScreen());
            }

            // AimAssist Target Lock
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

            // Alle anderen Module ticken
            if (ModuleManager.modules != null) {
                for (Module m : ModuleManager.modules) {
                    if (m.enabled) {
                        m.onTick(client);
                    }
                }
            }
        });
    }
}