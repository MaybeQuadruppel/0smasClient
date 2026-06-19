package com.qdrppl.newbridge;

import com.qdrppl.newbridge.Hacks.Combat.AimAssist;
import com.qdrppl.newbridge.Hacks.Combat.AutoDihhTap;
import com.qdrppl.newbridge.Hacks.Misc.ModuleList;
import com.qdrppl.newbridge.Hacks.Misc.Scaffold;
import com.qdrppl.newbridge.Hacks.Visual.ESP.*;
import com.qdrppl.newbridge.UI.ClickGuiScreen;
import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import com.qdrppl.newbridge.Utils.ChatHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;

public class EntryPoint implements ClientModInitializer {

    public static KeyMapping guiKeyBind;
    private static final Identifier MODULE_LIST_HUD_ID = Identifier.fromNamespaceAndPath("newbridge", "module_list");
    String CategoryName = "Client";
    @Override
    public void onInitializeClient() {

        Identifier catId = Identifier.parse("client");
        KeyMapping.Category myCategory = KeyMapping.Category.register(catId);
        guiKeyBind = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "GUI NewBridge",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                myCategory


        ));

        ModuleManager.init();
        RenderUtils.getInstance().init(Minecraft.getInstance());
        PlayerESP.getInstance().init(Minecraft.getInstance());
        Config.load();
        ChatHandler.register();

        net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.START_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null && AimAssist.INSTANCE != null && AimAssist.INSTANCE.enabled) {
                float tickDelta = client.getFps();
                AimAssist.INSTANCE.onUpdate(client); //AimAssist.INSTANCE.onUpdate(client, tickDelta);
                Scaffold.INSTANCE.onUpdate(client);
            }
        });

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (guiKeyBind.consumeClick()) {
                client.setScreen(new ClickGuiScreen());
            }

            if (client.screen == null) {
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
        HudElementRegistry.addLast(MODULE_LIST_HUD_ID, (guiGraphics, deltaTracker) -> ModuleList.draw(guiGraphics));
    }
}