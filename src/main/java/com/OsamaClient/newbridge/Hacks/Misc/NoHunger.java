package com.OsamaClient.newbridge.Hacks.Misc;

import com.OsamaClient.newbridge.EntryPoint;
import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.event.PacketSendEvent;
import com.OsamaClient.newbridge.event.Subscribe;
import com.OsamaClient.newbridge.mixin.ServerboundMovePlayerPacketAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public final class NoHunger extends Module {

    public static NoHunger INSTANCE;

    public boolean sprintSpoof = true;
    public boolean onGroundSpoof = true;

    private boolean lastOnGround;
    private boolean ignorePacket;

    public NoHunger() {
        super("NoHunger", "Reduces hunger consumption.", Category.MISC);
        INSTANCE = this;

        this.settings.add(new ToggleButton("Sprint Spoof", sprintSpoof, val -> sprintSpoof = val));
        this.settings.add(new ToggleButton("OnGround Spoof", onGroundSpoof, val -> onGroundSpoof = val));

        EntryPoint.EVENT_BUS.subscribe(this);
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            lastOnGround = mc.player.onGround();
        }
        ignorePacket = false;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (!this.enabled || mc.player == null) return;

        if (mc.player.onGround() && !lastOnGround && onGroundSpoof) {
            ignorePacket = true;
        }

        lastOnGround = mc.player.onGround();
    }

    @Subscribe
    public void onPacketSend(PacketSendEvent event) {
        if (!this.enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (ignorePacket && event.getPacket() instanceof ServerboundMovePlayerPacket) {
            ignorePacket = false;
            return;
        }

        if (mc.player.isPassenger() || mc.player.isInWater() || mc.player.isUnderWater()) return;

        if (event.getPacket() instanceof ServerboundPlayerCommandPacket packet && sprintSpoof) {
            if (packet.getAction() == ServerboundPlayerCommandPacket.Action.START_SPRINTING) {
                event.setCancelled(true);
            }
        }

        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet && onGroundSpoof) {
            if (mc.player.onGround() && mc.player.fallDistance <= 0.0f && !mc.gameMode.isDestroying()) {
                ((ServerboundMovePlayerPacketAccessor) packet).setOnGround(false);
            }
        }
    }
}