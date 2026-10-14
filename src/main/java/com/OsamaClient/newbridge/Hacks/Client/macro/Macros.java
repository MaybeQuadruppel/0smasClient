package com.OsamaClient.newbridge.Hacks.Client.macro;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.UI.gui.input.UiInput;
import com.OsamaClient.newbridge.UI.gui.window.WindowManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Macros: eine Taste führt Chat-Nachrichten, /Befehle, .Client-Befehle und Pausen aus.
 * Verwaltung im eigenen Fenster (Dock "Macros" oder ".macro").
 */
public class Macros extends Module {

    public static Macros INSTANCE;
    public final MacroStore store;
    public final MacroRunner runner = new MacroRunner();
    private final MacroRunner.Trigger trigger = new MacroRunner.Trigger();
    /** Für Tests: Ausgabe umleiten. */
    public MacroRunner.Sink sinkOverride;

    public Macros() {
        this(FabricLoader.getInstance().getConfigDir().resolve("0smasclient-macros.json"));
    }

    public Macros(java.nio.file.Path file) {
        super("Macros", "Keys that send chat messages, /commands and .client commands", Category.CLIENT);
        INSTANCE = this;
        store = new MacroStore(file);
        ToggleButton[] open = new ToggleButton[1];
        open[0] = new ToggleButton("Open Manager", false, v -> {
            if (v) {
                open[0].enabled = false;
                WindowManager.INSTANCE.open(new MacrosWindow());
            }
        }).withDescription("Create and edit macros (also: .macro)");
        settings.add(open[0]);
        this.enabled = true;
    }

    @Override
    public void onTick(Minecraft mc) {
        if (mc.player == null) return;
        boolean active = mc.gui.screen() == null;
        for (MacroStore.Macro m : store.list()) {
            if (m.key < 0) { trigger.forget(m); continue; }
            boolean down = UiInput.keyDown(m.key);
            if (trigger.pressed(m, down) && active && m.enabled) run(m);
        }
        runner.tick(sink(mc));
    }

    public void run(MacroStore.Macro m) {
        runner.start(m);
    }

    private MacroRunner.Sink sink(Minecraft mc) {
        if (sinkOverride != null) return sinkOverride;
        return new MacroRunner.Sink() {
            @Override public void chat(String text) {
                if (mc.getConnection() != null) mc.getConnection().sendChat(text);
            }
            @Override public void command(String cmd) {
                if (mc.getConnection() != null) mc.getConnection().sendCommand(cmd);
            }
        };
    }

    public void handleCommand(String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("run")) {
            String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            MacroStore.Macro m = store.find(name);
            Minecraft mc = Minecraft.getInstance();
            if (m == null) {
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[Macros] Not found: §f" + name));
                return;
            }
            run(m);
            return;
        }
        WindowManager.INSTANCE.openLater(new MacrosWindow());
    }
}
