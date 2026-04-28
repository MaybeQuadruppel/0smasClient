package com.qdrppl.newbridge.UI.components;

import com.sun.jdi.CharType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.CharacterEvent;

public abstract class Component {
    public int x, y, width, height;


    public Component() {}


    public abstract void render(GuiGraphics guiGraphics, int mouseX, int mouseY);


    public abstract boolean mouseClicked(double mouseX, double mouseY, int button);

    public abstract boolean mouseReleased(double mouseX, double mouseY, int button);


    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }


    public boolean handleKeyboard(net.minecraft.client.input.KeyEvent event) {
        return false;
    }

    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        return false;
    }

    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        return false;
    }


    protected boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}