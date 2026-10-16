package com.OsamaClient.newbridge.Utils;

import com.OsamaClient.newbridge.Hacks.Movement.GoTo;
import com.OsamaClient.newbridge.Hacks.Movement.Teleport;
import com.OsamaClient.newbridge.Hacks.Movement.pathing.Navigator;
import com.OsamaClient.newbridge.UI.components.ModuleManager;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ChatHandler {

    static java.util.List<String> waypointNames() {
        var w = com.OsamaClient.newbridge.Hacks.Misc.Waypoints.INSTANCE; var mc = Minecraft.getInstance();
        java.util.List<String> o = new java.util.ArrayList<>();
        if (w != null && mc.player != null) for (var x : w.current(mc)) o.add(x.name);
        return o;
    }

    private static void say(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal(s));
    }

    /** ".keys save|load|delete <name> | list" - hotkey profiles. */
    public static void handleKeys(String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "list";
        switch (sub) {
            case "save" -> {
                if (a.length < 3) { say("§6Usage: .keys save <name>"); return; }
                int n = com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.save(a[2]);
                say(n >= 0 ? "§a[Keys] Saved §f" + a[2] + "§a (" + n + " keybinds)" : "§c[Keys] Could not save.");
            }
            case "load" -> {
                if (a.length < 3) { say("§6Usage: .keys load <name>"); return; }
                int n = com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.load(a[2]);
                say(n >= 0 ? "§a[Keys] Loaded §f" + a[2] + "§a (" + n + " keybinds)" : "§c[Keys] Not found: §f" + a[2]);
            }
            case "delete", "del" -> {
                if (a.length < 3) { say("§6Usage: .keys delete <name>"); return; }
                say(com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.delete(a[2]) ? "§e[Keys] Deleted §f" + a[2] : "§c[Keys] Not found: §f" + a[2]);
            }
            default -> {
                var l = com.OsamaClient.newbridge.Hacks.Client.KeyProfiles.list();
                say(l.isEmpty() ? "§e[Keys] No hotkey profiles yet. Use .keys save <name>" : "§6[Keys] Profiles: §f" + String.join(", ", l));
            }
        }
    }

    /** ".friend add|del <name> | list". */
    public static void handleFriend(String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "list";
        String name = a.length > 2 ? a[2] : null;
        switch (sub) {
            case "add" -> say(name == null ? "§c[Friends] .friend add <name>" : com.OsamaClient.newbridge.Hacks.Client.Friends.add(name) ? "§a[Friends] Added §f" + name : "§e[Friends] " + name + " is already a friend.");
            case "del", "remove" -> say(name == null ? "§c[Friends] .friend del <name>" : com.OsamaClient.newbridge.Hacks.Client.Friends.remove(name) ? "§a[Friends] Removed §f" + name : "§c[Friends] " + name + " is not a friend.");
            default -> {
                var online = com.OsamaClient.newbridge.Hacks.Client.Friends.onlineNames();
                StringBuilder sb = new StringBuilder();
                for (String f : com.OsamaClient.newbridge.Hacks.Client.Friends.friends)
                    sb.append(sb.length() == 0 ? "" : "§7, ").append(online.contains(f.toLowerCase(java.util.Locale.ROOT)) ? "§a" : "§7").append(f);
                say(com.OsamaClient.newbridge.Hacks.Client.Friends.friends.isEmpty() ? "§e[Friends] No friends yet." : "§6[Friends] §f" + sb + " §7(green = online)");
            }
        }
    }

    /** ".config backup | restore [name] | list | export | import". */
    public static void handleConfig(String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase() : "list";
        switch (sub) {
            case "backup" -> {
                com.OsamaClient.newbridge.config.Config.save();
                String n = com.OsamaClient.newbridge.config.Config.backupNow();
                say(n != null ? "§a[Config] Backup created: §f" + n : "§c[Config] Backup failed.");
            }
            case "restore" -> {
                boolean ok = com.OsamaClient.newbridge.config.Config.restoreBackup(a.length > 2 ? a[2] : null);
                say(ok ? "§a[Config] Backup restored and applied." : "§c[Config] No such backup.");
            }
            case "export" -> {
                String t = com.OsamaClient.newbridge.config.Config.exportString();
                if (t == null) { say("§c[Config] Export failed."); break; }
                com.OsamaClient.newbridge.UI.gui.input.UiInput.setClipboard(t);
                say("§a[Config] Copied your modules, binds and settings to the clipboard §7(" + t.length() + " chars)§a. Send it to a friend; they load it with §f.config import§a.");
            }
            case "import" -> {
                String t = com.OsamaClient.newbridge.UI.gui.input.UiInput.clipboard();
                boolean ok = com.OsamaClient.newbridge.config.Config.importString(t);
                say(ok ? "§a[Config] Imported from the clipboard. Your old setup was backed up first (§f.config restore§a)."
                        : "§c[Config] The clipboard does not contain a NewBridge config (copy one made with .config export).");
            }
            default -> {
                var l = com.OsamaClient.newbridge.config.Config.listBackups();
                say(l.isEmpty() ? "§e[Config] No backups yet." : "§6[Config] Backups (newest first): §f" + String.join(", ", l.subList(0, Math.min(8, l.size()))));
            }
        }
    }

    public static void register() {
        Commands.args(".wp goto", ChatHandler::waypointNames);
        Commands.args(".wp del", ChatHandler::waypointNames);
        Commands.args(".profile load", com.OsamaClient.newbridge.config.Config::listProfiles);
        Commands.args(".profile delete", com.OsamaClient.newbridge.config.Config::listProfiles);
        Commands.args(".keys load", com.OsamaClient.newbridge.Hacks.Client.KeyProfiles::list);
        Commands.args(".keys delete", com.OsamaClient.newbridge.Hacks.Client.KeyProfiles::list);
        Commands.args(".friend del", () -> new java.util.ArrayList<>(com.OsamaClient.newbridge.Hacks.Client.Friends.friends));
        Commands.args(".friend add", () -> {
            java.util.List<String> l = new java.util.ArrayList<>();
            var mc = Minecraft.getInstance();
            if (mc.getConnection() != null) for (var pi : mc.getConnection().getOnlinePlayers()) {
                String n = pi.getProfile().name();
                if (!com.OsamaClient.newbridge.Hacks.Client.Friends.isFriendName(n)) l.add(n);
            }
            return l;
        });
        Commands.args(".config restore", com.OsamaClient.newbridge.config.Config::listBackups);
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
            if (lower.equals(".vanish") || lower.startsWith(".vanish ")) {
                if (com.OsamaClient.newbridge.Hacks.Misc.AntiVanish.INSTANCE != null) {
                    com.OsamaClient.newbridge.Hacks.Misc.AntiVanish.INSTANCE.handleCommand(message.trim().split("\\s+"));
                }
                return false;
            }
            if (lower.equals(".keys") || lower.startsWith(".keys ")) {
                handleKeys(message.trim().split("\\s+"));
                return false;
            }
            if (lower.equals(".friend") || lower.startsWith(".friend ") || lower.equals(".friends")) {
                handleFriend(message.trim().split("\\s+"));
                return false;
            }
            if (lower.equals(".music") || lower.startsWith(".music ")) {
                if (com.OsamaClient.newbridge.Hacks.Visual.music.MusicOverlay.INSTANCE != null)
                    com.OsamaClient.newbridge.Hacks.Visual.music.MusicOverlay.INSTANCE.handleCommand(message.trim().split("\\s+"));
                return false;
            }
            if (lower.equals(".config") || lower.startsWith(".config ")) {
                handleConfig(message.trim().split("\\s+"));
                return false;
            }
            if (lower.equals(".help")) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    for (Commands.Cmd c : Commands.ALL) mc.player.sendSystemMessage(Component.literal("§f" + c.usage() + " §7- " + c.desc()));
                    return false;
                }
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
