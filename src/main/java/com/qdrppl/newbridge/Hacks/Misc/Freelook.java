package com.qdrppl.newbridge.Hacks.Misc;

import com.qdrppl.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

public class Freelook extends Module {
    public static Freelook instance;

    public float cameraYaw;
    public float cameraPitch;
    private CameraType oldPerspective;

    public Freelook() {
        super("Freelook", "Free perspective (Hold Left Alt)", Category.MISC);
        instance = this;
    }

    @Override
    public void onTick(Minecraft client) {
        if (client.player == null) return;

        if (isFreelooking()) {
            if (client.options.getCameraType() != CameraType.THIRD_PERSON_BACK) {
                oldPerspective = client.options.getCameraType();
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);

                cameraYaw = client.player.getYRot();
                cameraPitch = client.player.getXRot();
            }
        } else if (oldPerspective != null) {
            client.options.setCameraType(oldPerspective);
            oldPerspective = null;
        }
    }

    public static boolean isFreelooking() {
        return instance != null && instance.enabled && isAltHeld();
    }

    public static boolean isAltHeld() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
    }
}