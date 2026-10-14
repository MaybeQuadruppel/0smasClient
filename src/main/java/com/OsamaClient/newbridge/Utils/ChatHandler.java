package com.OsamaClient.newbridge.Utils;

import com.OsamaClient.newbridge.Hacks.Movement.GoTo;
import com.OsamaClient.newbridge.Hacks.Movement.Teleport;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatHandler {

    public static void register() {
        ClientSendMessageEvents.ALLOW_CHAT.register((message) -> {
            String lower = message.toLowerCase();
            if (lower.equals(".profile") || lower.startsWith(".profile ") || lower.equals(".profiles") || lower.startsWith(".profiles ")) {
                if (com.OsamaClient.newbridge.Hacks.Client.Profiles.INSTANCE != null) {
                    com.OsamaClient.newbridge.Hacks.Client.Profiles.INSTANCE.handleCommand(message.trim().split("\\s+"));
                }
                return false;
            }
            if (lower.equals(".wp") || lower.startsWith(".wp ") || lower.startsWith(".waypoint")) {
                if (com.OsamaClient.newbridge.Hacks.Misc.Waypoints.INSTANCE != null) {
                    com.OsamaClient.newbridge.Hacks.Misc.Waypoints.INSTANCE.handleCommand(message.trim().split("\\s+"));
                }
                return false;
            }
            if (lower.equals(".trail") || lower.startsWith(".trail ")) {
                if (com.OsamaClient.newbridge.Hacks.Misc.Breadcrumbs.INSTANCE != null) {
                    com.OsamaClient.newbridge.Hacks.Misc.Breadcrumbs.INSTANCE.handleCommand(message.trim().split("\\s+"));
                }
                return false;
            }
            if (lower.equals(".stash") || lower.startsWith(".stash ") || lower.equals(".stashes")) {
                if (com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger.INSTANCE != null) {
                    com.OsamaClient.newbridge.Hacks.Misc.stash.StashLogger.INSTANCE.handleCommand(message.trim().split("\\s+"));
                }
                return false;
            }
            if (lower.equals(".macro") || lower.startsWith(".macro ") || lower.equals(".macros")) {
                if (com.OsamaClient.newbridge.Hacks.Client.macro.Macros.INSTANCE != null) {
                    com.OsamaClient.newbridge.Hacks.Client.macro.Macros.INSTANCE.handleCommand(message.trim().split("\\s+"));
                }
                return false;
            }
            if (lower.equals(".help")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§6Commands: §f.goto <x> [y] <z> | .goto stop | .tp <x> [y] <z>"));
                    mc.player.sendSystemMessage(Component.literal("§f.profile save|load|delete <name> | .profile list | .profile bind"));
                    mc.player.sendSystemMessage(Component.literal("§f.wp add <name> [x y z] | .wp del <name> | .wp list | .wp goto <name> | .wp clear"));
                    mc.player.sendSystemMessage(Component.literal("§f.trail back [blocks] | .trail clear | .stash [clear] | .macro"));
                }
                return false;
            }
            if (message.startsWith(".goto")) {
                String[] args = message.split(" ");
                Minecraft mc = Minecraft.getInstance();

                if (mc.player == null) return true;

                // Stop-Funktionalität über den neuen Navigator
                if (args.length == 2 && args[1].equalsIgnoreCase("stop")) {
                    GoTo goToModule = getGoToModule();
                    if (goToModule != null) goToModule.onDisable();
                    else Navigator.INSTANCE.cancel(mc, Navigator.OWNER_GOTO);
                    mc.player.sendSystemMessage(Component.literal("§c[GoTo] Navigation stopped."));
                    return false;
                }

                try {
                    GoTo goToModule = getGoToModule();
                    if (goToModule == null) {
                        mc.player.sendSystemMessage(Component.literal("§c[GoTo] Module is not initialized."));
                        return false;
                    }

                    if (args.length == 3) {
                        int x = Integer.parseInt(args[1]);
                        int z = Integer.parseInt(args[2]);
                        int y = (int) mc.player.getY(); // Nutzt aktuelle Y-Höhe des Spielers

                        goToModule.setTarget(x, y, z);
                        mc.player.sendSystemMessage(Component.literal("§a[GoTo] Walking to X: " + x + " Z: " + z));
                    }
                    else if (args.length == 4) {
                        int x = Integer.parseInt(args[1]);
                        int y = Integer.parseInt(args[2]);
                        int z = Integer.parseInt(args[3]);

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

    private static GoTo getGoToModule() {
        return ModuleManager.getModuleByName("GoTo") instanceof GoTo goTo ? goTo : null;
    }
}
