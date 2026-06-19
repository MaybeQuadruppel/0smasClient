package com.qdrppl.newbridge.Utils;

import com.qdrppl.newbridge.Hacks.Movement.GoTo;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatHandler {

    public static void register() {

        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (message.startsWith(".goto")) {
                String[] args = message.split(" ");
                Minecraft mc = Minecraft.getInstance();

                if (args.length == 2 && args[1].equalsIgnoreCase("stop")) {
                    GoTo.stopNavigation();
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[GoTo] Navigation stopped" +
                            "."));
                    return false;
                }

                try {
                    // Fall 2: ".goto X Z"
                    if (args.length == 3) {
                        int x = Integer.parseInt(args[1]);
                        int z = Integer.parseInt(args[2]);
                        int y = (int) mc.player.getY();
                        GoTo.setTarget(x, y, z, false);
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§a[GoTo] Walking to X: " + x + " Z: " + z));
                    }
                    // Fall 3: ".goto X Y Z"
                    else if (args.length == 4) {
                        int x = Integer.parseInt(args[1]);
                        int y = Integer.parseInt(args[2]);
                        int z = Integer.parseInt(args[3]);
                        GoTo.setTarget(x, y, z, true);
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§a[GoTo] Walking to X: " + x + " Y: " + y + " Z: " + z));
                    }
                    else {
                        mc.gui.getChat().addClientSystemMessage(Component.literal("§6[GoTo] Uses: .goto <x> <z> ODER .goto <x> <y> <z> OR .goto stop"));
                    }
                } catch (NumberFormatException e) {
                    mc.gui.getChat().addClientSystemMessage(Component.literal("§c[GoTo] Error! Please enter Digit"));
                }

                return false;
            }

            return true;
        });
    }
}