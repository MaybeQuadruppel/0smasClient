package com.OsamaClient.newbridge.Hacks.Client.macro;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Führt Macros Schritt für Schritt aus (pro Tick), damit Server nicht wegen Spam kicken.
 * Ohne Minecraft-Abhängigkeit: Ausgabe über {@link Sink} -> headless testbar.
 */
public final class MacroRunner {

    public interface Sink {
        void chat(String text);      // normale Nachricht oder ".client-befehl"
        void command(String cmd);    // ohne führendes '/'
    }

    /** Ein Schritt: Text (Chat/Befehl) oder Warten. */
    public record Step(String text, int waitTicks) {
        public boolean isWait() { return text == null; }
    }

    public static final int MAX_WAIT = 20 * 60;  // max. 1 Minute pro wait
    public static final int MAX_STEPS = 32;

    private final Deque<Step> queue = new ArrayDeque<>();
    private int cooldown;
    private int stepDelay;

    /** Zerlegt "a; /b; wait 20; .wp list" in Schritte. */
    public static List<Step> parse(String actions) {
        List<Step> out = new ArrayList<>();
        if (actions == null) return out;
        for (String raw : actions.split(";")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.startsWith("wait ") || lower.equals("wait")) {
                int t = 20;
                try { t = Integer.parseInt(s.substring(4).trim()); } catch (NumberFormatException | StringIndexOutOfBoundsException ignored) {}
                out.add(new Step(null, Math.max(0, Math.min(MAX_WAIT, t))));
            } else {
                out.add(new Step(s.length() > 256 ? s.substring(0, 256) : s, 0));
            }
            if (out.size() >= MAX_STEPS) break;
        }
        return out;
    }

    public boolean busy() { return !queue.isEmpty(); }

    /** Startet ein Macro (ein laufendes wird ersetzt). */
    public void start(MacroStore.Macro m) {
        queue.clear();
        queue.addAll(parse(m.actions));
        stepDelay = Math.max(0, m.delay);
        cooldown = 0;
    }

    public void stop() { queue.clear(); }

    /** Pro Tick aufrufen. */
    public void tick(Sink sink) {
        if (cooldown > 0) { cooldown--; return; }
        Step s = queue.poll();
        if (s == null) return;
        if (s.isWait()) {
            cooldown = s.waitTicks();
            return;
        }
        String t = s.text();
        if (t.startsWith("/")) sink.command(t.substring(1));
        else sink.chat(t);
        cooldown = stepDelay;
    }

    /** Tasten-Flankenerkennung: true genau beim Drücken. */
    public static final class Trigger {
        private final java.util.Map<Object, Boolean> was = new java.util.HashMap<>();

        public boolean pressed(Object id, boolean down) {
            Boolean w = was.put(id, down);
            return down && w != null && !w; // erster Tick zählt nicht (Taste war evtl. schon gedrückt)
        }

        public void forget(Object id) { was.remove(id); }
    }
}
