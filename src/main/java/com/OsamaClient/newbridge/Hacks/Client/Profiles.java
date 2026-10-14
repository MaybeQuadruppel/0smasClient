package com.OsamaClient.newbridge.Hacks.Client;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.TextBox;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Config-Profile: speichert/lädt den kompletten Modul-Zustand (an/aus, Keybinds, alle Einstellungen)
 * unter einem Namen, z.B. "pvp", "mining", "legit".
 *
 * Bedienung:
 *  - ClickGUI: Name eintippen, dann "Save" / "Load" / "Delete" anklicken
 *  - Chat:     .profile save|load|delete <name>, .profile list
 *  - "Per-Server Auto Load": beim Betreten eines Servers wird automatisch das Profil
 *    "server_<ip>" geladen (falls vorhanden). Mit ".profile bind" wird das aktuelle
 *    Setup als Profil für den aktuellen Server gespeichert.
 *
 * Profile liegen in {@code config/0smasclient-profiles/<name>.json}.
 * Das GUI-Layout (Panel-Positionen, HUD) ist global und wird von Profilen nicht verändert.
 */
public class Profiles extends Module {

    public static Profiles INSTANCE;

    private final TextBox nameBox = new TextBox("Profile Name", "default", null).withMaxLength(48);
    private final ToggleButton autoPerServer;
    private boolean perServer = true;
    private String lastServerKey = null;
    /** Zuletzt geladenes/gespeichertes Profil (für die Anzeige "aktiv"). */
    private String activeProfile = null;

    public String activeProfile() { return activeProfile; }
    public boolean perServer() { return perServer; }
    public void setPerServer(boolean v) { autoPerServer.setValue(v); }

    public Profiles() {
        super("Profiles", "Save and switch complete client setups (modules, binds, settings)", Category.CLIENT);
        INSTANCE = this;

        this.settings.add(nameBox.withDescription("Name of the profile to save / load / delete."));
        this.settings.add(action("Save", "Saves all modules, keybinds and settings under the name above.",
                () -> save(nameBox.getText())));
        this.settings.add(action("Load", "Loads the profile with the name above.",
                () -> load(nameBox.getText())));
        this.settings.add(action("Delete", "Deletes the profile with the name above.",
                () -> delete(nameBox.getText())));
        this.settings.add(action("List", "Prints all saved profiles to chat.", this::list));
        this.settings.add(0, action("Open Manager", "Opens the profile manager (also: .profile)",
                () -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.open(new ProfilesWindow())));
        autoPerServer = new ToggleButton("Per-Server Auto Load", perServer, v -> perServer = v)
                .withDescription("Automatically loads profile 'server_<ip>' when you join a server (create it with .profile bind).");
        this.settings.add(autoPerServer);
        // Läuft immer im Hintergrund (Server-Wechsel erkennen), Modul-Toggle ist nur fürs GUI.
        this.enabled = true;
    }

    /** "Knopf": ein Toggle, der sofort wieder auf aus springt und eine Aktion auslöst. */
    private static ToggleButton action(String label, String description, Runnable run) {
        ToggleButton[] holder = new ToggleButton[1];
        holder[0] = new ToggleButton(label, false, v -> {
            if (v) {
                run.run();
                holder[0].enabled = false;
            }
        }).withDescription(description);
        return holder[0];
    }

    @Override
    public void toggle() {
        // Nie wirklich ausschalten – sonst funktioniert Per-Server-Auto-Load nicht mehr.
        enabled = true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (!perServer || mc.player == null || mc.level == null) {
            if (mc.level == null) lastServerKey = null;
            return;
        }
        String key = serverKey(mc);
        if (key == null || key.equals(lastServerKey)) return;
        lastServerKey = key;
        String profile = "server_" + key;
        if (Config.listProfiles().contains(Config.sanitizeProfileName(profile))) {
            if (Config.loadProfile(profile)) {
                activeProfile = Config.sanitizeProfileName(profile);
                msg("§a[Profiles] Loaded server profile §f" + profile);
            }
        }
    }

    /** Eindeutiger Schlüssel für den aktuellen Server / die aktuelle Singleplayer-Welt. */
    public static String serverKey(Minecraft mc) {
        ServerData data = mc.getCurrentServer();
        if (data != null && data.ip != null && !data.ip.isBlank()) {
            return Config.sanitizeProfileName(data.ip.toLowerCase());
        }
        if (mc.getSingleplayerServer() != null) {
            return Config.sanitizeProfileName("sp_" + mc.getSingleplayerServer().getWorldData().getLevelName());
        }
        return null;
    }

    // ------------------------------------------------------------------ actions

    public void save(String name) {
        String clean = Config.sanitizeProfileName(name);
        if (clean == null) { msg("§c[Profiles] Invalid name."); return; }
        if (Config.saveProfile(clean)) {
            activeProfile = clean;
            msg("§a[Profiles] Saved profile §f" + clean);
        }
        else msg("§c[Profiles] Could not save §f" + clean);
    }

    public void load(String name) {
        String clean = Config.sanitizeProfileName(name);
        if (clean == null) { msg("§c[Profiles] Invalid name."); return; }
        if (Config.loadProfile(clean)) {
            activeProfile = clean;
            msg("§a[Profiles] Loaded profile §f" + clean);
        }
        else msg("§c[Profiles] Profile §f" + clean + "§c not found.");
    }

    public void delete(String name) {
        String clean = Config.sanitizeProfileName(name);
        if (clean == null) { msg("§c[Profiles] Invalid name."); return; }
        if (Config.deleteProfile(clean)) {
            if (clean.equals(activeProfile)) activeProfile = null;
            msg("§e[Profiles] Deleted profile §f" + clean);
        }
        else msg("§c[Profiles] Profile §f" + clean + "§c not found.");
    }

    public void list() {
        List<String> names = Config.listProfiles();
        msg(names.isEmpty() ? "§e[Profiles] No profiles saved yet."
                : "§e[Profiles] " + names.size() + " profile(s): §f" + String.join(", ", names));
    }

    /** Speichert das aktuelle Setup als Auto-Profil für den aktuellen Server. */
    public void bindToServer() {
        String key = serverKey(Minecraft.getInstance());
        if (key == null) { msg("§c[Profiles] Not connected to a server/world."); return; }
        save("server_" + key);
        lastServerKey = key;
    }

    public boolean rename(String from, String to) {
        boolean ok = Config.renameProfile(from, to);
        if (ok && Config.sanitizeProfileName(from).equals(activeProfile)) activeProfile = Config.sanitizeProfileName(to);
        msg(ok ? "§a[Profiles] Renamed §f" + from + "§a -> §f" + to : "§c[Profiles] Could not rename (name used?)");
        return ok;
    }

    /** Chat-Befehl: .profile <save|load|delete|list|bind> [name] */
    public void handleCommand(String[] args) {
        if (args.length < 2) {
            com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.openLater(new ProfilesWindow());
            return;
        }
        String sub = args[1].toLowerCase();
        String name = args.length >= 3 ? args[2] : nameBox.getText();
        switch (sub) {
            case "save" -> save(name);
            case "load" -> load(name);
            case "delete", "del", "remove" -> delete(name);
            case "list", "ls" -> list();
            case "bind" -> bindToServer();
            case "ui", "gui", "menu" -> com.OsamaClient.newbridge.UI.gui.window.WindowManager.INSTANCE.openLater(new ProfilesWindow());
            default -> msg("§6[Profiles] Usage: .profile save|load|delete <name>, .profile list, .profile bind");
        }
    }

    private static void msg(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) mc.player.sendSystemMessage(Component.literal(text));
    }
}
