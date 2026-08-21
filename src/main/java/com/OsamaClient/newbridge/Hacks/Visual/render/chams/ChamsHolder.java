package com.OsamaClient.newbridge.Hacks.Visual.render.chams;

public interface ChamsHolder {
    void newbridge$setChams(boolean active, int visibleColor, int occludedColor);

    boolean newbridge$chamsActive();

    int newbridge$chamsVisible();

    int newbridge$chamsOccluded();
}