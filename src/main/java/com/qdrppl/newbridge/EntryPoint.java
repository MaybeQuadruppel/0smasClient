package com.qdrppl.newbridge;

import com.qdrppl.newbridge.Hacks.Combat.AimAssist;
import com.qdrppl.newbridge.Hacks.Visual.ESP.*;
import com.qdrppl.newbridge.UI.ClickGuiScreen;
import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier; // Corrected from IdentifierPattern
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.LivingEntity;
import org.lwjgl.glfw.GLFW;


public class EntryPoint implements ClientModInitializer {


    public static KeyMapping guiKeyBind;

    @Override
    public void onInitializeClient() {

        guiKeyBind = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "GUI NewBridge",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("newbridge", "main"))
        ));

        ModuleManager.init();
        RenderUtils.getInstance().init(Minecraft.getInstance());
        PlayerESP.getInstance().init(Minecraft.getInstance());


        net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents.START_MAIN.register(context -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player!= null && AimAssist.INSTANCE!= null && AimAssist.INSTANCE.enabled) {
                float tickDelta = client.getDeltaTracker().getRealtimeDeltaTicks();
                AimAssist.INSTANCE.onRender(client, tickDelta);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            while (guiKeyBind.consumeClick()) {
                client.setScreen(new ClickGuiScreen());
            }
            if (client.options.keyAttack.isDown()) {
                HitResult targetResult = client.hitResult;
                if (targetResult!= null && targetResult.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityHit = (EntityHitResult) targetResult;

                    if (entityHit.getEntity() instanceof LivingEntity target) {
                        if (AimAssist.INSTANCE!= null) {
                            AimAssist.INSTANCE.setLockedTarget(target);
                        }
                    }
                }
            }


            if (ModuleManager.modules!= null) {
                for (Module m : ModuleManager.modules) {
                    if (m.enabled &&!(m instanceof AimAssist)) {
                        m.onTick(client);
                    }
                }
            }
        });
    }
}