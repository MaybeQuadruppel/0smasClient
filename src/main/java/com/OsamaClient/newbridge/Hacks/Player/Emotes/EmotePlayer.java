package com.OsamaClient.newbridge.Hacks.Player.Emotes;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.UUID;

/**
 * Starts emotes for the local player and handles third-person perspective cleanly.
 * Players can freely toggle perspective with F5 (e.g. front view / third person front) during emotes.
 */
public final class EmotePlayer {

    private static final int GRACE_TICKS = 6;

    private static boolean registered;
    private static UUID activeId;
    private static CameraType previousCamera;
    private static int ticks;

    private EmotePlayer() {}

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(EmotePlayer::tick);
    }

    public static void play(EmoteAnimation anim) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;

        // Remember the original camera view only once when starting a sequence of emotes
        if (activeId == null) {
            previousCamera = mc.options.getCameraType();
        }

        activeId = p.getUUID();
        ticks = 0;
        EmoteAnimator.INSTANCE.play(activeId, anim);

        // Switch to third person if currently in first person, but allow F5 toggles freely after
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
    }

    public static void stop() {
        if (activeId != null) EmoteAnimator.INSTANCE.stop(activeId);
    }

    private static void finish(Minecraft mc, boolean clearAll) {
        if (clearAll) EmoteAnimator.INSTANCE.clear();
        if (previousCamera != null) {
            mc.options.setCameraType(previousCamera);
        }
        previousCamera = null;
        activeId = null;
    }

    private static boolean wantsToMove(Minecraft mc) {
        var o = mc.options;
        return o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
                || o.keyJump.isDown() || o.keyShift.isDown();
    }

    private static void tick(Minecraft mc) {
        if (activeId == null) return;
        LocalPlayer p = mc.player;
        if (p == null || !p.isAlive()) {
            finish(mc, true);
            return;
        }
        if (!EmoteAnimator.INSTANCE.isActive(activeId)) {
            finish(mc, false);
            return;
        }

        ticks++;
        if (ticks > GRACE_TICKS && EmoteAnimator.INSTANCE.isPlaying(activeId) && wantsToMove(mc)) {
            EmoteAnimator.INSTANCE.stop(activeId);
        }
    }
}