package com.OsamaClient.newbridge.UI.gui.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressTest {

    private static void run(Progress p, float seconds) {
        for (float t = 0; t < seconds - 1e-6f; t += 0.05f) p.update(0.05f, 1f);
    }

    @Test
    void runsLinearlyForwardOverItsDuration() {
        Progress p = new Progress(0.2f);
        assertEquals(0f, p.raw());
        p.setForward(true);
        p.update(0.1f, 1f);
        assertEquals(0.5f, p.raw(), 1e-6);
        run(p, 0.2f);
        assertEquals(1f, p.raw());
        assertTrue(p.finished());
    }

    @Test
    void runsBackwardToZero() {
        Progress p = new Progress(0.2f);
        p.setForward(true);
        run(p, 0.5f);
        p.setForward(false);
        assertFalse(p.finished());
        p.update(0.05f, 1f);
        assertEquals(0.75f, p.raw(), 1e-6);
        run(p, 1f);
        assertEquals(0f, p.raw());
        assertTrue(p.finished());
    }

    @Test
    void lagSpikeIsClampedSoTheAnimationStaysVisible() {
        Progress p = new Progress(0.2f);
        p.setForward(true);
        p.update(2f, 1f);
        assertEquals(0.5f, p.raw(), 1e-6);
    }

    @Test
    void speedMultiplierShortensTheDuration() {
        Progress p = new Progress(0.2f);
        p.setForward(true);
        p.update(0.05f, 2f);
        assertEquals(0.5f, p.raw(), 1e-6);
    }

    @Test
    void delayedReturnsStaggeredProgressForLaterElements() {
        Progress p = new Progress(0.2f);
        p.setForward(true);
        p.update(0.1f, 1f); // elapsed 0.1 s
        assertEquals(0.5f, p.delayed(0f), 1e-6);
        assertEquals(0.25f, p.delayed(0.05f), 1e-6);
        assertEquals(0f, p.delayed(0.2f), 1e-6);
    }

    @Test
    void easedAppliesEaseOutCubic() {
        Progress p = new Progress(1f);
        p.setForward(true);
        run(p, 0.5f);
        assertEquals(Anim.easeOutCubic(0.5f), p.eased(), 1e-5);
    }
}
