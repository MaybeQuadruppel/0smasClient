package com.OsamaClient.newbridge.Hacks.Client;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.ToggleButton;
import com.OsamaClient.newbridge.Utils.Notify;

/** Shows an action-bar message whenever you toggle a module. */
public class Notifications extends Module {

    public static Notifications INSTANCE;

    public boolean showState = true;
    public String lastMessage;

    public Notifications() {
        super("Notifications", "Action bar message when a module is toggled.", Category.CLIENT);
        INSTANCE = this;
        settings.add(new ToggleButton("Show State", showState, v -> showState = v).withDescription("Append ON/OFF to the message."));
    }

    /** Called from {@link Module#toggle()}. */
    public static void moduleToggled(Module m) {
        Notifications n = INSTANCE;
        if (n == null || !n.enabled || m == n) return;
        String msg = n.showState ? m.name + (m.enabled ? " enabled" : " disabled") : m.name;
        n.lastMessage = msg;
        Notify.bar(msg);
    }
}
