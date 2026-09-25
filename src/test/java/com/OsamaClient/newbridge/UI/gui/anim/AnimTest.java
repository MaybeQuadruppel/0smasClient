package com.OsamaClient.newbridge.UI.gui.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnimTest {

    @Test
    void startsAtInitialValueAndIsDone() {
        Anim a = new Anim(0.25f, 10f);
        assertEquals(0.25f, a.value());
        assertEquals(0.25f, a.target());
        assertTrue(a.done());
    }

    @Test
    void movesTowardTargetByExponentialSmoothing() {
        Anim a = new Anim(0f, 10f);
        a.setTarget(1f);
        a.update(0.1f, 1f);
        // value = 1 - exp(-10 * 0.1) = 0.6321
        assertEquals(1f - (float) Math.exp(-1.0), a.value(), 1e-4);
        assertFalse(a.done());
    }

    @Test
    void speedMultiplierScalesTheRate() {
        Anim slow = new Anim(0f, 10f);
        Anim fast = new Anim(0f, 10f);
        slow.setTarget(1f);
        fast.setTarget(1f);
        slow.update(0.05f, 1f);
        fast.update(0.05f, 2f);
        assertEquals(1f - (float) Math.exp(-1.0), fast.value(), 1e-4);
        assertTrue(fast.value() > slow.value());
    }

    @Test
    void sameResultRegardlessOfFrameRate() {
        Anim at30 = new Anim(0f, 8f);
        Anim at240 = new Anim(0f, 8f);
        at30.setTarget(1f);
        at240.setTarget(1f);
        for (int i = 0; i < 3; i++) at30.update(1f / 30f, 1f);
        for (int i = 0; i < 24; i++) at240.update(1f / 240f, 1f);
        assertEquals(at30.value(), at240.value(), 1e-4);
    }

    @Test
    void snapsToTargetWhenCloseAndReportsDone() {
        Anim a = new Anim(0f, 20f);
        a.setTarget(1f);
        for (int i = 0; i < 200; i++) a.update(1f / 60f, 1f);
        assertEquals(1f, a.value());
        assertTrue(a.done());
    }

    @Test
    void snapSetsValueAndTargetImmediately() {
        Anim a = new Anim(0f, 10f);
        a.setTarget(1f);
        a.snap(0.5f);
        assertEquals(0.5f, a.value());
        assertEquals(0.5f, a.target());
        assertTrue(a.done());
    }

    @Test
    void negativeOrHugeDeltaIsClamped() {
        Anim a = new Anim(0f, 10f);
        a.setTarget(1f);
        a.update(-1f, 1f);
        assertEquals(0f, a.value());
        a.update(5f, 1f); // clamped to 0.1 s
        assertEquals(1f - (float) Math.exp(-1.0), a.value(), 1e-4);
    }

    @Test
    void easingCurvesHitTheirEndpoints() {
        assertEquals(0f, Anim.easeOutCubic(0f), 1e-6);
        assertEquals(1f, Anim.easeOutCubic(1f), 1e-6);
        assertEquals(0.875f, Anim.easeOutCubic(0.5f), 1e-6);
        assertEquals(0f, Anim.easeInOutQuad(0f), 1e-6);
        assertEquals(0.5f, Anim.easeInOutQuad(0.5f), 1e-6);
        assertEquals(1f, Anim.easeInOutQuad(1f), 1e-6);
        assertEquals(1f, Anim.easeOutCubic(2f), 1e-6); // clamped
    }
}
