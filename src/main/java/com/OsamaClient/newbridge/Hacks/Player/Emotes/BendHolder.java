package com.OsamaClient.newbridge.Hacks.Player.Emotes;

/**
 * Bend state of a limb ModelPart (added to ModelPart by ModelPartBendMixin).
 * The part below {@code pivotY} (model pixels, limb-local, y points down) is rotated by (x, y, z) radians
 * around the axis through the limb origin, blended smoothly over {@link BendRender#RADIUS} pixels.
 */
public interface BendHolder {
    void newbridge$setBend(float pivotY, float xRot, float yRot, float zRot);

    void newbridge$clearBend();

    boolean newbridge$hasBend();

    float newbridge$bendPivotY();

    float newbridge$bendX();

    float newbridge$bendY();

    float newbridge$bendZ();
}
