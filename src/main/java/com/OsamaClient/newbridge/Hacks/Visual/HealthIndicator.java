package com.OsamaClient.newbridge.Hacks.Visual;

import com.OsamaClient.newbridge.UI.components.Module;
import com.OsamaClient.newbridge.UI.components.Slider;

public class HealthIndicator extends Module {
    public static HealthIndicator instance;
    public static double greenThreshold = 0.75; // 75% HP
    public static double yellowThreshold = 0.35; // 35% HP
    public HealthIndicator() {
        super("HealthIndicator", "Shows entity health in nametags", Category.VISUAL);
        instance = this;
        this.settings.add(new Slider("Green at %", 0.5, 1.0, 0.75, val -> greenThreshold = val));
        this.settings.add(new Slider("Yellow at %", 0.2, 0.4, 0.35, val -> yellowThreshold = val));
    }
}