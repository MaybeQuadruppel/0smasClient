package com.OsamaClient.newbridge.Hacks.Client;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Friends: look at a player and press the pick-block key (middle mouse) to add or remove them, or manage them in the
 * Friends tab. Friends count as teammates, so aura/aim/trigger modules skip them and TeammateList shows them.
 * The list is saved in config/0smasclient-friends.json together with when and where each friend was last seen.
 */
public class Friends extends Module {

    public static Friends INSTANCE;
    public static final Set<String> friends = new LinkedHashSet<>();

    /** Extra info per friend (keyed by name). */
    public static final class Info {
        public long added, lastSeen;
        public String lastServer = "";
    }

    public static final Map<String, Info> info = new LinkedHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Path testFile;

    public boolean joinAlerts = true;
    private boolean wasDown;
    private Set<String> lastOnline = null;
    private int tick;
    /** Last join/leave message (tests). */
    public String lastAlert;

    public Friends() {
        super("Friends", "Middle-click a player to make them a friend (treated as a teammate).", Category.CLIENT);
        INSTANCE = this;
        settings.add(new ToggleButton("Join Alerts", joinAlerts, v -> joinAlerts = v).withDescription("Tells you when a friend joins or leaves the server you are on."));
        load();
    }

    public static boolean isFriend(Entity e) {
        Friends f = INSTANCE;
        return f != null && f.enabled && e != null && isFriendName(e.getScoreboardName());
    }

    public static boolean isFriendName(String name) {
        if (name == null) return false;
        for (String f : friends) if (f.equalsIgnoreCase(name)) return true;
        return false;
    }

    /** @return true if added, false if removed */
    public static boolean toggleFriend(String name) {
        if (remove(name)) return false;
        add(name);
        return true;
    }

    public static boolean add(String name) {
        if (name == null || name.isBlank() || isFriendName(name)) return false;
        name = name.trim();
        friends.add(name);
        Info i = new Info();
        i.added = System.currentTimeMillis();
        info.put(name, i);
        save();
        return true;
    }

    public static boolean remove(String name) {
        String hit = null;
        for (String f : friends) if (f.equalsIgnoreCase(name)) hit = f;
        if (hit == null) return false;
        friends.remove(hit);
        info.remove(hit);
        save();
        return true;
    }

    public static Info infoOf(String name) { return info.computeIfAbsent(name, k -> new Info()); }

    /** Names of players in the tab list right now. */
    public static Set<String> onlineNames() {
        Set<String> out = new HashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) return out;
        for (PlayerInfo pi : mc.getConnection().getOnlinePlayers()) out.add(pi.getProfile().name().toLowerCase(Locale.ROOT));
        return out;
    }

    public static String serverName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        return mc.isLocalServer() ? "Singleplayer" : "";
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) { lastOnline = null; wasDown = false; return; }
        if (mc.gui.screen() == null) {
            boolean down = mc.options.keyPickItem.isDown();
            if (down && !wasDown && mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p) {
                boolean added = toggleFriend(p.getScoreboardName());
                Notify.bar(p.getName().getString() + (added ? " added to friends" : " removed from friends"));
            }
            wasDown = down;
        } else wasDown = false;
        if (++tick % 20 != 0) return;
        // last seen + join / leave alerts from the tab list
        Set<String> online = onlineNames();
        String server = serverName();
        boolean changed = false;
        for (String f : friends) {
            boolean on = online.contains(f.toLowerCase(Locale.ROOT));
            if (on) {
                Info i = infoOf(f);
                i.lastSeen = System.currentTimeMillis();
                if (!server.equals(i.lastServer)) { i.lastServer = server; changed = true; }
            }
            if (lastOnline != null && joinAlerts) {
                boolean was = lastOnline.contains(f.toLowerCase(Locale.ROOT));
                if (on && !was) alert(mc, "§a" + f + " joined");
                if (!on && was) alert(mc, "§7" + f + " left");
            }
        }
        lastOnline = online;
        if (changed || tick % 1200 == 0) save();
    }

    private void alert(Minecraft mc, String text) {
        lastAlert = text;
        mc.gui.hud.getChat().addClientSystemMessage(net.minecraft.network.chat.Component.literal("§d[Friends] §r" + text));
    }

    /** Tests: feed a tab list snapshot. */
    public void setLastOnline(Set<String> s) { lastOnline = s; }

    // ------------------------------------------------------------------ persistence

    public static Path file() {
        return testFile != null ? testFile : FabricLoader.getInstance().getConfigDir().resolve("0smasclient-friends.json");
    }

    public static void save() {
        try {
            JsonArray arr = new JsonArray();
            for (String f : friends) {
                Info i = infoOf(f);
                JsonObject o = new JsonObject();
                o.addProperty("name", f);
                o.addProperty("added", i.added);
                o.addProperty("lastSeen", i.lastSeen);
                o.addProperty("lastServer", i.lastServer);
                arr.add(o);
            }
            JsonObject root = new JsonObject();
            root.add("friends", arr);
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    public static void load() {
        friends.clear();
        info.clear();
        try {
            Path f = file();
            if (!Files.isRegularFile(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : root.getAsJsonArray("friends")) {
                JsonObject o = e.getAsJsonObject();
                String n = o.get("name").getAsString();
                friends.add(n);
                Info i = new Info();
                i.added = o.has("added") ? o.get("added").getAsLong() : 0;
                i.lastSeen = o.has("lastSeen") ? o.get("lastSeen").getAsLong() : 0;
                i.lastServer = o.has("lastServer") ? o.get("lastServer").getAsString() : "";
                info.put(n, i);
            }
        } catch (Exception ignored) {
        }
    }
}
