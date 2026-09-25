package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.Component;

/** Maps a setting data holder to its inline editor. */
public final class SettingWidgets {

    private SettingWidgets() {}

    public static SettingWidget create(Component c) {
        return new LabelWidget(c);
    }
}
