package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;

public class TrueSight extends Module {

    public static TrueSight instance;

    public TrueSight() {
        super("TrueSight", "Renders invisible entities visible", Category.VISUAL);
        instance = this;
    }
}