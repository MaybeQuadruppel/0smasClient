package com.OsamaClient.newbridge.Hacks.Dupeing;

//import com.qdrppl.newbridge.Hacks.Dupeing.PacketControl;
//import components.UI.com.OsamaClient.newbridge.Component;
//import com.qdrppl.newbridge.Hacks.Dupeing.PacketState;
//import net.minecraft.client.Minecraft;
//import net.minecraft.client.gui.GuiGraphicsExtractor;
//import org.lwjgl.glfw.GLFW;
//
///**
// * Floating overlay rendered on top of every AbstractContainerScreen.
// * Styled to match the existing black/white ClickGUI.
// * Draggable via LMB on the header bar.
// */
//public class ContainerOverlay {
//
//    // ── Position ──────────────────────────────────────────────────────────────
//    private static int posX = -1;   // -1 = not yet initialised
//    private static int posY = 60;
//
//    private static boolean dragging   = false;
//    private static int     dragOffX   = 0;
//    private static int     dragOffY   = 0;
//
//    // ── Layout ────────────────────────────────────────────────────────────────
//    private static final int W       = 130;
//    private static final int HDR_H   = 14;
//    private static final int PAD     = 6;
//    private static final int BTN_H   = 12;
//    private static final int BTN_GAP = 3;
//
//    // ── Palette (mirrors ClickGuiScreen) ──────────────────────────────────────
//    private static final int C_PANEL    = 0xF00A0A0A;
//    private static final int C_HEADER   = 0xFF181818;
//    private static final int C_SEP      = 0xFF2C2C2C;
//    private static final int C_ACCENT   = 0xFFFFFFFF;
//    private static final int C_TEXT     = 0xFFEEEEEE;
//    private static final int C_DIM      = 0xFF666666;
//    private static final int C_ON_BG    = 0xFF1A1A1A;
//    private static final int C_OFF_BG   = 0xFF0A0A0A;
//
//    // ── Hovered button tracking ───────────────────────────────────────────────
//    private static int hoveredBtn = -1;   // index of currently hovered button
//
//    // Button indices
//    private static final int BTN_SEND   = 0;
//    private static final int BTN_DESYNC = 1;
//    private static final int BTN_FLUSH  = 2;
//    private static final int BTN_CLEAR  = 3;
//
//    private static final float[] btnHover = new float[4];  // per-button hover anim
//
//    // ─────────────────────────────────────────────────────────────────────────
//    //  Draw  (called from ContainerScreenMixin every frame)
//    // ─────────────────────────────────────────────────────────────────────────
//
//    public static void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
//        if (!isVisible()) return;
//
//        Minecraft mc = Minecraft.getInstance();
//        int screenW  = mc.getWindow().getGuiScaledWidth();
//
//        // Default position: top-left of the container, with a small margin
//        if (posX < 0) posX = 4;
//
//        // ── Shadow ────────────────────────────────────────────────────────────
//        Component.drawShadow(g, posX, posY, W, totalHeight());
//
//        // ── Panel background ──────────────────────────────────────────────────
//        Component.drawRoundedRect(g, posX, posY, W, totalHeight(), C_PANEL);
//
//        // ── Header bar ────────────────────────────────────────────────────────
//        Component.drawRoundedRect(g, posX, posY, W, HDR_H, C_HEADER);
//        g.fill(posX, posY, posX + W, posY + 2, C_ACCENT);  // white top line
//        g.text(mc.font, "\u2630  Packet Utils",
//                posX + 5, posY + (HDR_H / 2) - 4, C_ACCENT, false);
//
//        // ── Separator ─────────────────────────────────────────────────────────
//        g.fill(posX, posY + HDR_H, posX + W, posY + HDR_H + 1, C_SEP);
//
//        // ── Section label ─────────────────────────────────────────────────────
//        int cy = posY + HDR_H + PAD;
//        g.text(mc.font, "PACKET", posX + PAD, cy, C_DIM, false);
//        cy += mc.font.lineHeight + 3;
//
//        // ── Row 1: Send | Desync ──────────────────────────────────────────────
//        int halfW = (W - PAD * 2 - BTN_GAP) / 2;
//
//        drawToggleBtn(g, mouseX, mouseY, BTN_SEND,
//                posX + PAD,           cy, halfW, BTN_H,
//                PacketState.sendEnabled  ? "Send: ON"  : "Send: OFF",
//                PacketState.sendEnabled);
//
//        drawToggleBtn(g, mouseX, mouseY, BTN_DESYNC,
//                posX + PAD + halfW + BTN_GAP, cy, halfW, BTN_H,
//                PacketState.desyncEnabled ? "Desync" : "Desync",
//                PacketState.desyncEnabled);
//
//        cy += BTN_H + BTN_GAP;
//
//        // ── Row 2: Flush | Clear ──────────────────────────────────────────────
//        drawActionBtn(g, mouseX, mouseY, BTN_FLUSH,
//                posX + PAD,           cy, halfW, BTN_H, "Flush");
//
//        drawActionBtn(g, mouseX, mouseY, BTN_CLEAR,
//                posX + PAD + halfW + BTN_GAP, cy, halfW, BTN_H, "Clear");
//
//        cy += BTN_H + PAD;
//
//        // ── Separator ─────────────────────────────────────────────────────────
//        g.fill(posX + PAD, cy, posX + W - PAD, cy + 1, C_SEP);
//        cy += 4;
//
//        // ── Queue counter ─────────────────────────────────────────────────────
//        String queueInfo = "Queue: " + PacketState.heldCount() + " pkts";
//        g.text(mc.font, queueInfo,
//                posX + PAD, cy,
//                PacketState.heldCount() > 0 ? C_ACCENT : C_DIM, false);
//
//        // ── Panel outline ─────────────────────────────────────────────────────
//        Component.drawRoundedOutline(g, posX, posY, W, totalHeight(), C_SEP);
//
//        // ── Step hover animations ─────────────────────────────────────────────
//        for (int i = 0; i < btnHover.length; i++) {
//            boolean h = (hoveredBtn == i);
//            btnHover[i] = h ? Math.min(1f, btnHover[i] + 0.15f)
//                    : Math.max(0f, btnHover[i] - 0.15f);
//        }
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    //  Button helpers
//    // ─────────────────────────────────────────────────────────────────────────
//
//    private static void drawToggleBtn(GuiGraphicsExtractor g, int mx, int my,
//                                      int btnIdx, int x, int y, int w, int h,
//                                      String label, boolean active) {
//        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
//        if (hov) hoveredBtn = btnIdx;
//
//        float hp = btnHover[btnIdx];
//        int bg  = active ? Component.lerpColor(C_ON_BG,  0xFF222222, hp)
//                : Component.lerpColor(C_OFF_BG, 0xFF141414, hp);
//        int bdr = active ? Component.lerpColor(C_SEP, C_ACCENT, 0.6f + hp * 0.4f)
//                : Component.lerpColor(C_SEP, C_DIM,   hp);
//        int fg  = active ? Component.lerpColor(C_TEXT, C_ACCENT, hp)
//                : Component.lerpColor(C_DIM,  C_TEXT,  hp);
//
//        Component.drawRoundedRect(   g, x, y, w, h, bg);
//        Component.drawRoundedOutline(g, x, y, w, h, bdr);
//
//        // Active indicator dot
//        if (active) g.fill(x + 3, y + h / 2 - 1, x + 5, y + h / 2 + 1, C_ACCENT);
//
//        int lw = Minecraft.getInstance().font.width(label);
//        g.text(Minecraft.getInstance().font, label,
//                x + (w - lw) / 2, y + (h / 2) - 4, fg, false);
//    }
//
//    private static void drawActionBtn(GuiGraphicsExtractor g, int mx, int my,
//                                      int btnIdx, int x, int y, int w, int h,
//                                      String label) {
//        boolean hov = mx >= x && mx <= x + w && my >= y && my <= y + h;
//        if (hov) hoveredBtn = btnIdx;
//
//        float hp = btnHover[btnIdx];
//        int bg  = Component.lerpColor(C_OFF_BG, 0xFF181818, hp);
//        int bdr = Component.lerpColor(C_SEP, C_ACCENT, hp * 0.7f);
//        int fg  = Component.lerpColor(C_DIM, C_TEXT, hp);
//
//        Component.drawRoundedRect(   g, x, y, w, h, bg);
//        Component.drawRoundedOutline(g, x, y, w, h, bdr);
//
//        int lw = Minecraft.getInstance().font.width(label);
//        g.text(Minecraft.getInstance().font, label,
//                x + (w - lw) / 2, y + (h / 2) - 4, fg, false);
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    //  Input handling  (called from ContainerScreenMixin)
//    // ─────────────────────────────────────────────────────────────────────────
//
//    /**
//     * @return true if the click was consumed by the overlay (screen should not
//     *         process it further).
//     */
//    public static boolean handleClick(double mx, double my, int button) {
//        if (!isVisible()) return false;
//        if (button != 0) return false;
//
//        // ── Header drag start ─────────────────────────────────────────────────
//        if (mx >= posX && mx <= posX + W
//                && my >= posY && my <= posY + HDR_H) {
//            dragging = true;
//            dragOffX = (int) mx - posX;
//            dragOffY = (int) my - posY;
//            return true;
//        }
//
//        // Recalculate button positions (must match draw())
//        int cy  = posY + HDR_H + Minecraft.getInstance().font.lineHeight + 3 + 6 + 3;
//        int half = (W - PAD * 2 - BTN_GAP) / 2;
//
//        int bx1 = posX + PAD,             by1 = cy;
//        int bx2 = posX + PAD + half + BTN_GAP, by2 = cy;
//        cy += BTN_H + BTN_GAP;
//        int bx3 = posX + PAD,             by3 = cy;
//        int bx4 = posX + PAD + half + BTN_GAP, by4 = cy;
//
//        if (inBtn(mx, my, bx1, by1, half, BTN_H)) {
//            PacketState.sendEnabled = !PacketState.sendEnabled;
//            if (PacketState.sendEnabled) PacketState.flush(); // release held on re-enable
//            return true;
//        }
//        if (inBtn(mx, my, bx2, by2, half, BTN_H)) {
//            PacketState.desyncEnabled = !PacketState.desyncEnabled;
//            return true;
//        }
//        if (inBtn(mx, my, bx3, by3, half, BTN_H)) {
//            PacketState.flush();
//            return true;
//        }
//        if (inBtn(mx, my, bx4, by4, half, BTN_H)) {
//            PacketState.clear();
//            return true;
//        }
//
//        // Consume clicks inside the panel so they don't pass to the container
//        return mx >= posX && mx <= posX + W && my >= posY && my <= posY + totalHeight();
//    }
//
//    public static boolean handleDrag(double mx, double my) {
//        if (!dragging) return false;
//        Minecraft mc = Minecraft.getInstance();
//        int screenW  = mc.getWindow().getGuiScaledWidth();
//        int screenH  = mc.getWindow().getGuiScaledHeight();
//        posX = Math.max(0, Math.min(screenW - W,       (int) mx - dragOffX));
//        posY = Math.max(0, Math.min(screenH - totalHeight(), (int) my - dragOffY));
//        return true;
//    }
//
//    public static void handleRelease() {
//        dragging = false;
//    }
//
//    // ─────────────────────────────────────────────────────────────────────────
//    //  Helpers
//    // ─────────────────────────────────────────────────────────────────────────
//
//    private static boolean inBtn(double mx, double my, int x, int y, int w, int h) {
//        return mx >= x && mx <= x + w && my >= y && my <= y + h;
//    }
//
//    private static int totalHeight() {
//        // HDR + sep + label + row1 + gap + row2 + gap + sep + queue
//        Minecraft mc = Minecraft.getInstance();
//        int lh = mc != null ? mc.font.lineHeight : 9;
//        return HDR_H + 1 + PAD + lh + 3 + BTN_H + BTN_GAP + BTN_H + PAD + 1 + 4 + lh + PAD;
//    }
//
//    /**
//     * Only show the overlay when the PacketControl module is enabled.
//     */
//    public static boolean isVisible() {
//        return PacketControl.instance != null && PacketControl.instance.enabled;
//    }
//}