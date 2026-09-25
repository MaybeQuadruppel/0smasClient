package com.OsamaClient.newbridge.UI.gui.setting;

import com.OsamaClient.newbridge.UI.components.Component;
import com.OsamaClient.newbridge.UI.gui.Theme;
import com.OsamaClient.newbridge.UI.gui.render.Ui;

/** Fallback for setting types without an editor: just shows the label. */
public class LabelWidget extends SettingWidget {

    public LabelWidget(Component setting) {
        super(setting);
    }

    @Override
    protected void draw(Ui ui) {
        background(ui, height());
        ui.text(label(setting), x + Theme.PAD, y, height(), Theme.FONT_SMALL, Theme.TEXT_DIM);
    }
}
