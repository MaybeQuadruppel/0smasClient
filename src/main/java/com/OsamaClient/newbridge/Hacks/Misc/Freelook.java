package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import com.OsamaClient.newbridge.UI.gui.input.Keys;

public class Freelook extends Module {

    public static Freelook instance;
    public float maxYaw = 360.0f;
    public boolean noClip = true;

    public Freelook() {
        super("Freelook", "Look around freely in third person while holding Left Alt.", Category.MISC);
        instance = this;

        this.settings.add(new ToggleButton("NoClip", noClip, val -> noClip = val)
                .withDescription("Allows the camera to pass through blocks while freelooking."));
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        super.onDisable();
    }

    public static boolean isFreelooking() {
        return instance != null
                && instance.enabled
                && isAltHeld();
    }

    public static boolean isNoClipActive() {
        return isFreelooking() && instance.noClip;
    }

    public static boolean isAltHeld() {
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(Keys.KEY_LEFT_ALT);
    }

    public interface CameraOverriddenEntity {
        float freelook$getCameraPitch();
        float freelook$getCameraYaw();
        void freelook$setCameraPitch(float pitch);
        void freelook$setCameraYaw(float yaw);
    }
}