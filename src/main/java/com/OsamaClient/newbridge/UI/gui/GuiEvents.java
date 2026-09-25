package com.OsamaClient.newbridge.UI.gui;

import java.util.ArrayList;
import java.util.List;

/** Tiny hook points so persistence can listen to the GUI without the GUI knowing about it. */
public final class GuiEvents {

    private static final List<Runnable> CLOSED = new ArrayList<>();
    private static final List<Runnable> CHANGED = new ArrayList<>();

    private GuiEvents() {}

    public static void onClosed(Runnable r) { CLOSED.add(r); }

    /** Something the user changed that should be saved soon (rebind, toggle). */
    public static void onChanged(Runnable r) { CHANGED.add(r); }

    static void fireClosed() { for (Runnable r : CLOSED) r.run(); }

    static void fireChanged() { for (Runnable r : CHANGED) r.run(); }
}
