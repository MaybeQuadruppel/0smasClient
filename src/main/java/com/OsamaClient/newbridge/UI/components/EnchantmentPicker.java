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
    // Speichert ausgewählte Verzauberungen + ihren individuellen Maximalpreis (Default 20)
    public final Map<String, Integer> selectedEnchantments = new HashMap<>();

    private boolean open = false;
    private int scrollOffset = 0;
    private final int maxVisible = 10;
    private final int itemHeight;
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

    private static final int C_GREEN = 0xFF55FF55; // Preis-Indikator (Kann bleiben, da es eine spezifische Signal-Farbe ist)

    public EnchantmentPicker(String label) {
        this.label = label;
        this.width  = UISettings.scaled(100);
        this.height = UISettings.scaled(14);
        this.itemHeight = UISettings.scaled(13);
    }

    public String getLabel() { return this.label; }

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

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        Theme.Palette p = Theme.getActive().palette;

        guiGraphics.text(Minecraft.getInstance().font, label + ":", x, y - 11, p.accent, false);

        boolean hov = isHovered(mouseX, mouseY);
        int btnBg = lerpColor(p.bg, p.bgHover, hov ? 1f : 0f);
        drawRoundedRect(guiGraphics, x, y, width, height, UISettings.withPanelAlpha(btnBg));
        drawRoundedOutline(guiGraphics, x, y, width, height, open ? p.accent : p.border);

        String arrow = open ? " \u25b2" : " \u25bc";
        int selCount = selectedEnchantments.size();
        String btnLabel = selCount > 0 ? selCount + " selected" + arrow : "Choose enchants" + arrow;
        guiGraphics.text(Minecraft.getInstance().font, btnLabel, x + 4, y + (height / 2) - 4, open ? p.accent : p.text, false);

        if (!open) return;

        List<String> items = getFilteredEnchantments();
        int dropW = width + UISettings.scaled(50);
        int searchH = UISettings.scaled(14);
        int listH   = maxVisible * itemHeight;

        int priceSliderH = (activePriceEnchant != null && selectedEnchantments.containsKey(activePriceEnchant)) ? UISettings.scaled(24) : 0;
        int dropH   = searchH + 2 + listH + priceSliderH;
        int dropX   = x;
        int dropY   = y + height + 3;

        drawShadow(guiGraphics, dropX, dropY, dropW, dropH);
        drawRoundedRect(guiGraphics, dropX, dropY, dropW, dropH + 2, UISettings.withPanelAlpha(p.bg));
        drawRoundedOutline(guiGraphics, dropX, dropY, dropW, dropH + 2, p.accent);

        drawRoundedRect(guiGraphics, dropX + 1, dropY + 1, dropW - 2, searchH, p.bgHover);
        guiGraphics.fill(dropX + 1, dropY + searchH, dropX + dropW - 1, dropY + searchH + 1, p.border);

        boolean showCursor = open && ((System.currentTimeMillis() / 500) % 2 == 0);
        String cursor = showCursor ? "|" : "";
        String display = searchQuery.isEmpty() ? "\u26b2 Search..." : "\u26b2 " + searchQuery + cursor;
        guiGraphics.text(Minecraft.getInstance().font, display, dropX + 5, dropY + (searchH / 2) - 4, searchQuery.isEmpty() ? p.textDim : p.text, false);

        int listY = dropY + searchH + 2;
        int selBgColor = (p.accent & 0x00FFFFFF) | 0x40000000;

        for (int i = 0; i < maxVisible; i++) {
            int index = i + scrollOffset;
            if (index >= items.size()) break;

            String item = items.get(index);
            boolean selected = selectedEnchantments.containsKey(item);
            int itemY = listY + i * itemHeight;
            boolean itemHov = mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= itemY && mouseY <= itemY + itemHeight;

            if (selected) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, selBgColor);
            else if (itemHov) guiGraphics.fill(dropX + 1, itemY, dropX + dropW - 1, itemY + itemHeight, 0x1AFFFFFF);

            if (selected) guiGraphics.text(Minecraft.getInstance().font, "\u2714", dropX + 5, itemY + (itemHeight / 2) - 4, p.text, false);

            if (selected) {
                int price = selectedEnchantments.get(item);
                String priceStr = price + " Emeralds";
                int priceW = Minecraft.getInstance().font.width(priceStr);
                guiGraphics.text(Minecraft.getInstance().font, priceStr, dropX + dropW - priceW - 6, itemY + (itemHeight / 2) - 4, C_GREEN, false);
            }

            String name = item;
            if (name.length() > 14) name = name.substring(0, 12) + "\u2026";
            guiGraphics.text(Minecraft.getInstance().font, name, dropX + (selected ? 17 : 7), itemY + (itemHeight / 2) - 4, selected ? p.text : (itemHov ? p.text : p.textDim), false);
        }

        if (items.size() > maxVisible) {
            int sbX = dropX + dropW - 4;
            float tp = scrollOffset / (float)(items.size() - maxVisible);
            int th = Math.max(16, (int)((maxVisible / (float) items.size()) * listH));
            int ty = listY + (int)(tp * (listH - th));
            guiGraphics.fill(sbX, listY, sbX + 3, listY + listH, p.bgHover);
            guiGraphics.fill(sbX, ty, sbX + 3, ty + th, p.border);
        }

        if (activePriceEnchant != null && selectedEnchantments.containsKey(activePriceEnchant)) {
            int startSliderY = listY + listH + 3;
            int sliderX = dropX + 6;
            int sliderW = dropW - 12;

            guiGraphics.fill(dropX + 1, startSliderY - 2, dropX + dropW - 1, startSliderY - 1, p.border);

            int currentPrice = selectedEnchantments.get(activePriceEnchant);

            if (draggingPriceSlider) {
                float pct = Math.min(1f, Math.max(0f, (mouseX - sliderX) / (float) sliderW));
                int newPrice = Math.max(1, Math.min(64, Math.round(1 + (pct * 63))));

                if (newPrice != currentPrice) {
                    selectedEnchantments.put(activePriceEnchant, newPrice);
                    currentPrice = newPrice;
                }
            }

            guiGraphics.text(Minecraft.getInstance().font, activePriceEnchant + " Max Price: " + currentPrice, sliderX, startSliderY, p.accent, false);

            int barY = startSliderY + 11;
            drawRoundedRect(guiGraphics, sliderX, barY, sliderW, 4, p.bgHover);

            float pct = (currentPrice - 1) / 63f;
            int fillW = (int) (pct * sliderW);
            drawRoundedRect(guiGraphics, sliderX, barY, fillW, 4, C_GREEN);

            int thumbX = sliderX + fillW;
            guiGraphics.fill(Math.max(sliderX, Math.min(sliderX + sliderW - 2, thumbX - 1)), barY - 1, Math.max(sliderX, Math.min(sliderX + sliderW - 1, thumbX + 2)), barY + 5, p.text);
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
        if (key == 256 || key == 257 || key == 335) { // ESC / Enter
            open = false;
            activePriceEnchant = null;
            Sounds.deselect();
            return true;
        }
        if (key == 259 && !searchQuery.isEmpty()) { // Backspace
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
        if (isHovered(mouseX, mouseY) && button == 0) {
            open = !open;
            activePriceEnchant = null;
            if (open) Sounds.select(); else Sounds.deselect();
            return true;
        }

        if (open) {
            int dropX = x, dropW = width + UISettings.scaled(50), listY = y + height + 5 + UISettings.scaled(14);
            int listH = maxVisible * itemHeight;

            if (activePriceEnchant != null && selectedEnchantments.containsKey(activePriceEnchant)) {
                int startSliderY = listY + listH + 3;
                int barY = startSliderY + 11;

                if (mouseX >= dropX + 6 && mouseX <= dropX + dropW - 6 && mouseY >= barY - 3 && mouseY <= barY + 7 && button == 0) {
                    this.draggingPriceSlider = true;
                    Sounds.select();
                    return true;
                }
            }

            if (mouseX >= dropX && mouseX <= dropX + dropW && mouseY >= listY && mouseY <= listY + listH) {
                int idx = (int)((mouseY - listY) / itemHeight) + scrollOffset;
                List<String> items = getFilteredEnchantments();

                if (idx >= 0 && idx < items.size()) {
                    String item = items.get(idx);
                    if (button == 0) {
                        if (selectedEnchantments.containsKey(item)) {
                            selectedEnchantments.remove(item);
                            if (activePriceEnchant != null && activePriceEnchant.equals(item)) activePriceEnchant = null;
                            Sounds.deselect();
                        } else {
                            selectedEnchantments.put(item, 20);
                            activePriceEnchant = item;
                            Sounds.select();
                        }
                    } else if (button == 1) {
                        if (selectedEnchantments.containsKey(item)) {
                            activePriceEnchant = (item.equals(activePriceEnchant)) ? null : item;
                            if (activePriceEnchant != null) Sounds.select(); else Sounds.deselect();
                        }
                    }
                }
                return true;
            }

            if (mouseX < dropX || mouseX > dropX + dropW || mouseY < y + height) {
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