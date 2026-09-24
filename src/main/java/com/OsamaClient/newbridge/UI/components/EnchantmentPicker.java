package com.OsamaClient.newbridge.UI.components;

import com.OsamaClient.newbridge.UI.Sounds;
import com.OsamaClient.newbridge.UI.Theme;
import com.OsamaClient.newbridge.UI.UISettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EnchantmentPicker extends Component {

    private final String label;
    public final Map<String, Integer> selectedEnchantments = new HashMap<>();

    private boolean open = false;
    private int scrollOffset = 0;
    private final int maxVisible = 10;
    private String searchQuery = "";

    private String activePriceEnchant = null;
    private boolean draggingPriceSlider = false;

    private static final List<String> ENCHANTMENTS = List.of(
            "Mending", "Unbreaking", "Efficiency", "Silk Touch", "Fortune",
            "Protection", "Fire Protection", "Feather Falling", "Blast Protection", "Projectile Protection",
            "Respiration", "Aqua Affinity", "Thorns", "Depth Strider", "Frost Walker", "Soul Speed", "Swift Sneak",
            "Sharpness", "Smite", "Bane of Arthropods", "Knockback", "Fire Aspect", "Looting", "Sweeping Edge",
            "Power", "Punch", "Flame", "Infinity",
            "Loyalty", "Impaling", "Riptide", "Channeling",
            "Multishot", "Quick Charge", "Piercing",
            "Density", "Breach", "Wind Burst",
            "Lure", "Luck of the Sea"
    );

    private static final int C_GREEN = 0xFF55FF55;
    private static final int BASE_BOX_HEIGHT = 16;
    private static final int BASE_TOTAL_HEIGHT = 28; // 12px Label + 16px Box

    public EnchantmentPicker(String label) {
        super(0, 0, 100, BASE_TOTAL_HEIGHT);
        this.label = label;
        syncScaledSize(100, BASE_TOTAL_HEIGHT, 60, BASE_TOTAL_HEIGHT);
    }

    public String getLabel() { return this.label; }

    public EnchantmentPicker withDescription(String description) {
        this.description = description;
        return this;
    }

    private List<String> getFilteredEnchantments() {
        return ENCHANTMENTS.stream()
                .filter(e -> e.toLowerCase().contains(searchQuery.toLowerCase()))
                .sorted((e1, e2) -> {
                    boolean s1 = selectedEnchantments.containsKey(e1);
                    boolean s2 = selectedEnchantments.containsKey(e2);
                    if (s1 && !s2) return -1;
                    if (!s1 && s2) return 1;
                    return e1.compareTo(e2);
                })
                .collect(Collectors.toList());
    }

    private int getButtonY() {
        return y + UISettings.scaled(12);
    }

    private int getButtonHeight() {
        return UISettings.scaled(BASE_BOX_HEIGHT);
    }

    private boolean isButtonHovered(int mouseX, int mouseY) {
        int btnY = getButtonY();
        int btnH = getButtonHeight();
        return mouseX >= x && mouseX <= x + width && mouseY >= btnY && mouseY <= btnY + btnH;
    }

    @Override
    public void render(Object graphics, int mouseX, int mouseY) {
        if (!(graphics instanceof GuiGraphicsExtractor guiGraphics)) return;

        syncHeight(BASE_TOTAL_HEIGHT, UISettings.scaled(BASE_TOTAL_HEIGHT));
        Theme.Palette p = Theme.getActive().palette;

        // Label oberhalb der Box
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, label + ":", x, y + 1, p.accent, false);

        int btnY = getButtonY();
        int btnH = getButtonHeight();
        boolean hov = isButtonHovered(mouseX, mouseY);
        int btnBg = lerpColor(p.bg, p.bgHover, hov ? 1f : 0f);

        // Haupt-Button mit Panel-Alpha
        drawRoundedRect(guiGraphics, x, btnY, width, btnH, UISettings.withPanelAlpha(btnBg));
        drawRoundedOutline(guiGraphics, x, btnY, width, btnH, open ? p.accent : p.border);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedEnchantments.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose enchants" + arrow;
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, btnLabel, x + UISettings.scaled(6), btnY + (btnH / 2) - UISettings.scaled(4), open ? p.accent : p.text, false);

        if (!open) return;

        // Dropdown Menü
        List<String> items = getFilteredEnchantments();
        boolean showPriceSlider = activePriceEnchant != null && selectedEnchantments.containsKey(activePriceEnchant);

        int itemH = UISettings.scaled(14);
        int dropW = width + UISettings.scaled(50);
        int searchH = UISettings.scaled(16);
        int listH = maxVisible * itemH;
        int priceSliderH = showPriceSlider ? UISettings.scaled(28) : 0;
        int dropH = searchH + 2 + listH + priceSliderH;
        int dropX = x;
        int dropY = btnY + btnH + UISettings.scaled(3);

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, UISettings.withPanelAlpha(p.bg));
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, p.accent);

        // Suchfeld
        drawRoundedRect(guiGraphics, dropX + 2, dropY + 2, dropW - 4, searchH, UISettings.withPanelAlpha(p.bgHover));
        guiGraphics.fill(dropX + 2, dropY + searchH + 1, dropX + dropW - 2, dropY + searchH + 2, p.border);

        boolean showCursor = open && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursor = showCursor ? "|" : "";
        String display = searchQuery.isEmpty() ? "\u2315 Search..." : "\u2315 " + searchQuery + cursor;
        UISettings.drawText(guiGraphics, Minecraft.getInstance().font, display, dropX + UISettings.scaled(6), dropY + (searchH / 2) - UISettings.scaled(3), searchQuery.isEmpty() ? p.textDim : p.text, false);

        int listY = dropY + searchH + 3;
        int selBgColor = (p.accent & 0x00FFFFFF) | 0x35000000;

        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;

            String item = items.get(index);
            boolean selected = selectedEnchantments.containsKey(item);
            int itemY = listY + i * itemH;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemH;

            if (selected) guiGraphics.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + itemH, selBgColor);
            else if (itemHov) guiGraphics.fill(dropX + 2, itemY, dropX + dropW - 2, itemY + itemH, UISettings.withPanelAlpha(p.bgHover));

            if (selected) UISettings.drawText(guiGraphics, Minecraft.getInstance().font, "\u2714", dropX + UISettings.scaled(5), itemY + (itemH / 2) - UISettings.scaled(4), p.accent, false);

            if (selected) {
                int price = selectedEnchantments.get(item);
                String priceStr = price + " Em";
                int priceW = UISettings.textWidth(Minecraft.getInstance().font, priceStr);
                UISettings.drawText(guiGraphics, Minecraft.getInstance().font, priceStr, dropX + dropW - priceW - UISettings.scaled(6), itemY + (itemH / 2) - UISettings.scaled(4), C_GREEN, false);
            }

            String name = item;
            if (name.length() > 14) name = name.substring(0, 12) + "…";
            UISettings.drawText(guiGraphics, Minecraft.getInstance().font, name, dropX + UISettings.scaled(selected ? 17 : 7), itemY + (itemH / 2) - UISettings.scaled(4), selected ? p.text : (itemHov ? p.text : p.textDim), false);
        }

        // Scrollbar
        if (items.size() > maxVisible) {
            int sbX = dropX + dropW - UISettings.scaled(4);
            float tp = scrollOffset / (float)(items.size() - maxVisible);
            int th = Math.max(UISettings.scaled(14), (int)((maxVisible / (float) items.size()) * listH));
            int ty = listY + (int)(tp * (listH - th));
            guiGraphics.fill(sbX, listY, sbX + UISettings.scaled(2), listY + listH, p.bgHover);
            guiGraphics.fill(sbX, ty, sbX + UISettings.scaled(2), ty + th, p.accent);
        }

        // Emerald Max Price Slider
        if (showPriceSlider) {
            int startSliderY = listY + listH + UISettings.scaled(4);
            int sliderX = dropX + UISettings.scaled(6);
            int sliderW = dropW - UISettings.scaled(12);

            guiGraphics.fill(dropX + 2, startSliderY - UISettings.scaled(3), dropX + dropW - 2, startSliderY - UISettings.scaled(2), p.border);

            int currentPrice = selectedEnchantments.get(activePriceEnchant);

            if (draggingPriceSlider) {
                float pct = Math.min(1f, Math.max(0f, (mouseX - sliderX) / (float) sliderW));
                int newPrice = Math.max(1, Math.min(64, Math.round(1 + (pct * 63))));

                if (newPrice != currentPrice) {
                    selectedEnchantments.put(activePriceEnchant, newPrice);
                    currentPrice = newPrice;
                }
            }

            UISettings.drawText(guiGraphics, Minecraft.getInstance().font, activePriceEnchant + " Max: " + currentPrice + " Em", sliderX, startSliderY, p.accent, false);

            int barY = startSliderY + UISettings.scaled(11);
            drawRoundedRect(guiGraphics, sliderX, barY, sliderW, UISettings.scaled(4), p.bgHover);

            float pct = (currentPrice - 1) / 63f;
            int fillW = (int) (pct * sliderW);
            drawRoundedRect(guiGraphics, sliderX, barY, fillW, UISettings.scaled(4), C_GREEN);

            int thumbX = sliderX + fillW;
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX - 1)), barY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX + 2)), barY + UISettings.scaled(5), 0xFFFFFFFF);
        } else {
            activePriceEnchant = null;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!open) return false;
        char c = (char) event.codepoint();
        if (c >= 32 && c != 127) {
            searchQuery += c;
            scrollOffset = 0;
            Sounds.select();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!open) return false;
        int key = event.key();
        if (key == 256 || key == 257 || key == 335) {
            open = false;
            activePriceEnchant = null;
            Sounds.deselect();
            return true;
        }
        if (key == 259 && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollOffset = 0;
            Sounds.select();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (open && mouseX >= x && mouseX <= x + width + UISettings.scaled(50)) {
            int oldScroll = scrollOffset;
            if (amount > 0 && scrollOffset > 0) scrollOffset--;
            else if (amount < 0 && scrollOffset < Math.max(0, getFilteredEnchantments().size() - maxVisible)) scrollOffset++;

            if (scrollOffset != oldScroll) {
                Sounds.select();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int imx = (int) mouseX, imy = (int) mouseY;

        if (isButtonHovered(imx, imy) && button == 0) {
            open = !open;
            activePriceEnchant = null;
            if (open) Sounds.select(); else Sounds.deselect();
            return true;
        }

        if (open) {
            int itemH = UISettings.scaled(14);
            int dropX = x, dropW = width + UISettings.scaled(50);
            int searchH = UISettings.scaled(16);
            int dropY = getButtonY() + getButtonHeight() + UISettings.scaled(3);
            int listY = dropY + searchH + 3;
            int listH = maxVisible * itemH;

            boolean showPriceSlider = activePriceEnchant != null && selectedEnchantments.containsKey(activePriceEnchant);

            if (showPriceSlider) {
                int startSliderY = listY + listH + UISettings.scaled(4);
                int barY = startSliderY + UISettings.scaled(11);

                if (imx >= dropX + UISettings.scaled(6) && imx <= dropX + dropW - UISettings.scaled(6) && imy >= barY - 3 && imy <= barY + 7 && button == 0) {
                    this.draggingPriceSlider = true;
                    Sounds.select();
                    return true;
                }
            }

            if (imx >= dropX && imx <= dropX + dropW && imy >= listY && imy <= listY + listH) {
                int idx = ((imy - listY) / itemH) + scrollOffset;
                List<String> items = getFilteredEnchantments();

                if (idx >= 0 && idx < items.size()) {
                    String item = items.get(idx);

                    if (button == 0) {
                        if (selectedEnchantments.containsKey(item)) {
                            selectedEnchantments.remove(item);
                            if (item.equals(activePriceEnchant)) activePriceEnchant = null;
                            Sounds.deselect();
                        } else {
                            selectedEnchantments.put(item, 20);
                            activePriceEnchant = item;
                            Sounds.select();
                        }
                    } else if (button == 1) {
                        if (selectedEnchantments.containsKey(item)) {
                            activePriceEnchant = item.equals(activePriceEnchant) ? null : item;
                            if (activePriceEnchant != null) Sounds.select(); else Sounds.deselect();
                        }
                    }
                }
                return true;
            }

            if (imx < dropX || imx > dropX + dropW || imy < getButtonY() || imy > listY + listH + (showPriceSlider ? UISettings.scaled(28) : 0)) {
                open = false;
                activePriceEnchant = null;
                Sounds.deselect();
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) this.draggingPriceSlider = false;
        return false;
    }
}