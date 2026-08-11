package com.OsamaClient.newbridge.Utils;

import com.OsamaClient.newbridge.Hacks.Movement.GoTo;
import com.OsamaClient.newbridge.Hacks.Movement.Teleport;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatHandler {

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            if (message.startsWith(".goto")) {
                String[] args = message.split(" ");
                Minecraft mc = Minecraft.getInstance();

                if (mc.player == null) return true;

                // Stop-Funktionalität über den neuen Navigator
                if (args.length == 2 && args[1].equalsIgnoreCase("stop")) {
                    Navigator.INSTANCE.stop(mc);
                    mc.player.sendSystemMessage(Component.literal("§c[GoTo] Navigation stopped."));
                    return false;
                }

                try {
                    // Erstellt das Modul-Objekt
                    GoTo goToModule = new GoTo();

                    if (args.length == 3) {
                        int x = Integer.parseInt(args[1]);
                        int z = Integer.parseInt(args[2]);
                        int y = (int) mc.player.getY(); // Nutzt aktuelle Y-Höhe des Spielers

                        goToModule.enabled = true; // Sicherstellen, dass das Modul ticken darf
                        goToModule.setTarget(x, y, z);
                        mc.player.sendSystemMessage(Component.literal("§a[GoTo] Walking to X: " + x + " Z: " + z));
                    }
                    else if (args.length == 4) {
                        int x = Integer.parseInt(args[1]);
                        int y = Integer.parseInt(args[2]);
                        int z = Integer.parseInt(args[3]);

                        goToModule.enabled = true; // Sicherstellen, dass das Modul ticken darf
                        goToModule.setTarget(x, y, z);
                        mc.player.sendSystemMessage(Component.literal("§a[GoTo] Walking to X: " + x + " Y: " + y + " Z: " + z));
                    }
                    else {
                        mc.player.sendSystemMessage(Component.literal("§6[GoTo] Usage: .goto <x> <z> OR .goto <x> <y> <z> OR .goto stop"));
                    }
                } catch (NumberFormatException e) {
                    mc.player.sendSystemMessage(Component.literal("§c[GoTo] Error! Please enter valid digits."));
                }

                return false; // Verhindert, dass die Nachricht an den Server gesendet wird
            }

            if (message.startsWith(".tp")) {
                String[] args = message.split(" ");
                Minecraft mc = Minecraft.getInstance();

                if (mc.player == null) return true;

                try {
                    Teleport tpModule = Teleport.getInstance();

                    if (args.length == 3) {
                        int x = Integer.parseInt(args[1]);
                        int z = Integer.parseInt(args[2]);
                        int y = (int) mc.player.getY(); // Nutzt aktuelle Y-Höhe des Spielers

                        tpModule.setTarget(x, y, z);
                    }
                    else if (args.length == 4) {
                        int x = Integer.parseInt(args[1]);
                        int y = Integer.parseInt(args[2]);
                        int z = Integer.parseInt(args[3]);

                        tpModule.setTarget(x, y, z);
                    }
                    else {
                        mc.player.sendSystemMessage(Component.literal("§6[TP] Usage: .tp <x> <z> OR .tp <x> <y> <z>"));
                    }
                } catch (NumberFormatException e) {
                    mc.player.sendSystemMessage(Component.literal("§c[TP] Error! Please enter valid digits."));
                }

                return false; // Verhindert, dass die Nachricht an den Server gesendet wird
            }
            return true;
        });
    }
}