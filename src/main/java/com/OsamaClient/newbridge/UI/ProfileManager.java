package com.OsamaClient.newbridge.UI;

import com.OsamaClient.newbridge.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Verwaltet benannte Profile: vollständige Snapshots aus Modul-Zuständen,
 * Keybinds und GUI-Settings, die unabhängig von der zentralen
 * {@code newbridge.json} gespeichert und geladen werden können.
 *
 * Nutzt bewusst dieselbe Serialisierung wie {@link Config}
 * ({@link Config#serializeState()} / {@link Config#deserializeState(JsonObject)}),
 * statt sie zu duplizieren - ein Profil ist damit exakt so aufgebaut wie die
 * normale Config und bleibt automatisch kompatibel, wenn sich das Format
 * dort ändert.
 */
public final class ProfileManager {

    private ProfileManager() {}

    private static final Path PROFILES_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("newbridge_profiles");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Name des zuletzt geladenen/gespeicherten Profils - rein für die GUI-Anzeige,
     *  nicht persistiert (muss also pro Session neu geladen/gesetzt werden). */
    public static String activeProfile = null;

    private static void ensureDir() {
        try {
            Files.createDirectories(PROFILES_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Entfernt Zeichen, die auf den meisten Dateisystemen in Dateinamen
     *  Probleme machen. Leere/nur-Leerzeichen-Namen fallen auf "profile" zurück,
     *  damit saveProfile() nie einen leeren/ungültigen Dateinamen erzeugt. */
    public static String sanitize(String name) {
        if (name == null) return "profile";
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return cleaned.isEmpty() ? "profile" : cleaned;
    }

    private static Path fileFor(String name) {
        return PROFILES_DIR.resolve(sanitize(name) + ".json");
    }

    /** Alle vorhandenen Profilnamen, alphabetisch (ohne Beachtung der
     *  Groß-/Kleinschreibung) sortiert. */
    public static List<String> listProfiles() {
        ensureDir();

        File[] files = PROFILES_DIR.toFile().listFiles((dir, n) -> n.toLowerCase().endsWith(".json"));
        if (files == null) return new ArrayList<>();

        return Arrays.stream(files)
                .map(File::getName)
                .map(n -> n.substring(0, n.length() - ".json".length()))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    public static boolean exists(String name) {
        return fileFor(name).toFile().exists();
    }

    /**
     * Schreibt den aktuellen Live-Zustand (Module, Keybinds, GUI-Settings)
     * als neues bzw. überschriebenes Profil auf die Festplatte.
     */
    public static boolean saveProfile(String name) {
        ensureDir();

        JsonObject root = Config.serializeState();

        try (Writer writer = new FileWriter(fileFor(name).toFile())) {
            GSON.toJson(root, writer);
            activeProfile = sanitize(name);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lädt ein Profil von der Festplatte und wendet es sofort auf den
     * Live-Zustand an (Module toggeln/umkonfigurieren sich live).
     */
    public static boolean loadProfile(String name) {
        File file = fileFor(name).toFile();
        if (!file.exists()) return false;

        try (Reader reader = new FileReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return false;

            Config.deserializeState(root);
            activeProfile = sanitize(name);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteProfile(String name) {
        File file = fileFor(name).toFile();
        boolean deleted = file.exists() && file.delete();

        if (deleted && sanitize(name).equals(activeProfile)) {
            activeProfile = null;
        }
        return deleted;
    }

    /**
     * Schreibt den aktuellen Live-Zustand in das gerade aktive Profil zurück
     * (No-Op, falls kein Profil aktiv ist). Damit bleiben nachträgliche
     * Änderungen - Modul togglen, Keybind setzen, ein Setting im UI-Settings-
     * Modul verstellen, ... - automatisch im geladenen Profil erhalten,
     * statt dass man das Profil manuell neu über "+ New Profile" speichern
     * müsste. Nutzt denselben Dateinamen wie {@link #saveProfile(String)},
     * setzt {@link #activeProfile} dabei aber NICHT neu (ist ja schon aktiv).
     */
    public static boolean syncActiveProfile() {
        if (activeProfile == null) return false;

        ensureDir();
        JsonObject root = Config.serializeState();

        try (Writer writer = new FileWriter(fileFor(activeProfile).toFile())) {
            GSON.toJson(root, writer);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** true, falls unter diesem Namen bereits ein Profil existiert - für
     *  Überschreib-Warnungen/Bestätigungen in der GUI. */
    public static boolean wouldOverwrite(String name) {
        return exists(name);
    }
}