package com.qdrppl.newbridge.Hacks.Combat;

import com.qdrppl.newbridge.UI.components.Module;
import com.qdrppl.newbridge.UI.components.Slider;

public class Velocity extends Module {

    public static Velocity INSTANCE;
    public double horizontal = 0.0;
    public double vertical = 0.0;

    public Velocity() {
        super("Velocity", "(Allows you to get no Knockback)", Category.COMBAT);
        this.settings.add(new Slider("Horizontal", 0.0, 100.0, horizontal, val -> horizontal = val));
        this.settings.add(new Slider("Vertical", 0.0, 100.0, vertical, val -> vertical = val));
        INSTANCE = this;
    }
}