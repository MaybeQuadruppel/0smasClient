package com.OsamaClient.newbridge.Hacks.Movement;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.mixin.ServerboundMovePlayerPacketAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * AntiHunger: hides sprinting and the on-ground flag from the server so it stops draining your food.
 * Based on the AntiHunger module of the Autism client (GPL-3.0), merged with the old NoHunger module (landing
 * packets are let through). Will flag anticheats.
 */
public class AntiHunger extends Module {

    public static AntiHunger INSTANCE;

    public boolean noSprint = true;
    public boolean keepFloating = true;
    public boolean allowLandings = true;
    /** Set while the player is in the air; the first onGround packet afterwards is the landing. */
    private boolean airborne;
    /** Number of packets rewritten (for tests / debugging). */
    public static int rewritten;

    public AntiHunger() {
        super("AntiHunger", "Stops hunger drain by hiding sprinting and ground contact from the server.", Category.MOVEMENT);
        INSTANCE = this;
        settings.add(new ToggleButton("No Sprint", noSprint, v -> noSprint = v).withDescription("Never tell the server you are sprinting."));
        settings.add(new ToggleButton("Keep Floating", keepFloating, v -> keepFloating = v).withDescription("Report onGround = false in movement packets."));
        settings.add(new ToggleButton("Allow Landings", allowLandings, v -> allowLandings = v)
                .withDescription("Let the packet of the tick you land through unchanged, so the server still sees the landing (no fall damage desync)."));
    }

    @Override
    public void onEnable() {
        var p = Minecraft.getInstance().player;
        airborne = p != null && !p.onGround();
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        if (!mc.player.onGround()) airborne = true;
    }

    /** Called from the connection mixin for every outgoing packet; returns true to drop it. */
    public static boolean handle(Packet<?> packet) {
        AntiHunger m = INSTANCE;
        if (m == null || !m.enabled) return false;
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null) return false;
        if (packet instanceof ServerboundPlayerCommandPacket c && m.noSprint
                && c.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
            rewritten++;
            return true;
        }
        if (packet instanceof ServerboundMovePlayerPacket mv0 && mv0.isOnGround() && m.airborne) {
            m.airborne = false;
            if (m.allowLandings) return false;   // landing packet goes through unchanged
        }
        if (packet instanceof ServerboundMovePlayerPacket mv && m.keepFloating && mv.isOnGround()
                && !p.isPassenger() && !p.isInWater() && !p.isUnderWater() && !p.isSwimming() && p.fallDistance <= 0
                && !(mc.gameMode != null && mc.gameMode.isDestroying())) {
            ((ServerboundMovePlayerPacketAccessor) mv).setOnGround(false);
            rewritten++;
        }
        return false;
    }
}
