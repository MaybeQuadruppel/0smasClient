package com.qdrppl.newbridge.Hacks.Dupeing;

//import com.qdrppl.newbridge.UI.components.Module;
//import com.qdrppl.newbridge.Hacks.Dupeing.PacketState;
//import net.minecraft.client.Minecraft;
//
///**
// * PacketControl — activates the floating container overlay.
// *
// * When this module is enabled:
// *  - The ContainerOverlay becomes visible whenever a container screen is open.
// *  - "Send" toggle: hold / release all outgoing C2S packets.
// *  - "Desync" toggle: silently drop movement packets (position desync).
// *  - "Flush" button: release all held packets at once.
// *  - "Clear" button: discard all held packets.
// *
// * The overlay itself is rendered by ContainerScreenMixin + ContainerOverlay.
// * This module only acts as the on/off gate and the entry in the ClickGUI.
// */
//public class PacketControl extends Module {
//
//    public static PacketControl instance;
//
//    public PacketControl() {
//        super("PacketControl",
//                "Floating packet overlay on container screens",
//                Category.MISC);
//        instance = this;
//        // No extra settings — everything is controlled in the overlay UI
//    }
//
//    @Override
//    public void onEnable() {
//        // Nothing to do — overlay becomes visible because isVisible() checks instance.enabled
//    }
//
//    @Override
//    public void onDisable() {
//        // Safety: re-enable sending and clear queue when module is toggled off
//        PacketState.sendEnabled  = true;
//        PacketState.desyncEnabled = false;
//        PacketState.flush();
//    }
//
//    @Override
//    public void onTick(Minecraft client) {
//        // Optional: could display queue size in the ArrayList HUD here
//    }
//}