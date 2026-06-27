package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.UI.components.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import org.lwjgl.glfw.GLFW;

public class Freelook extends Module {

    public static Freelook instance;
    public float maxYaw = 360.0f;

    public Freelook() {
        super("Freelook", "free third person perspective (hold Left Alt)", Category.MISC);
        instance = this;
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

    public static boolean isAltHeld() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
    }

    public interface CameraOverriddenEntity {
        float freelook$getCameraPitch();
        float freelook$getCameraYaw();
        void freelook$setCameraPitch(float pitch);
        void freelook$setCameraYaw(float yaw);
    }
}