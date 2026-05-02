package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

public class DropdownButton extends AbstractWidget {

    private static final int TEXT_COLOR = 0x00FFFFFF;
    private static final int DROPDOWN_BG = 0xC0000000;
    private static final int HOVER_BG = 0x30FFFFFF;
    private static final int ARROW_COLOR = 0x00AAAAAA;

    private final List<String> options;
    private int selectedIndex;
    private final int closedHeight;
    private boolean open;
    private float externalAlpha = 1f;
    private final IntConsumer onSelectionChanged;
    private final Runnable onToggleOpen;
    private int hoveredOption = -1;

    public DropdownButton(int x, int y, int width, int height, Component message,
                          List<String> options, int selectedIndex, boolean open,
                          IntConsumer onSelectionChanged, Runnable onToggleOpen) {
        super(x, y, width, open ? height + options.size() * height : height, message);
        this.options = options;
        this.selectedIndex = selectedIndex;
        this.closedHeight = height;
        this.open = open;
        this.onSelectionChanged = onSelectionChanged;
        this.onToggleOpen = onToggleOpen;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public void setExternalAlpha(float alpha) {
        this.externalAlpha = alpha;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) (externalAlpha * 255);
        int textColor = (alpha << 24) | TEXT_COLOR;
        int arrowColor = (alpha << 24) | ARROW_COLOR;

        var font = Minecraft.getInstance().font;
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = closedHeight;

        int textY = y + (h - 8) / 2;

        guiGraphics.text(font, this.getMessage(), x + 4, textY, textColor, false);

        String currentValue = selectedIndex >= 0 && selectedIndex < options.size() ? options.get(selectedIndex) : "";
        String arrow = open ? "▲" : "▼";
        String displayText = currentValue + " " + arrow;
        int displayWidth = font.width(displayText);
        guiGraphics.text(font, Component.literal(displayText), x + w - displayWidth - 4, textY, arrowColor, false);

        if (open && !options.isEmpty()) {
            int dropdownY = y + h;

            guiGraphics.fill(x, dropdownY, x + w, dropdownY + options.size() * h, DROPDOWN_BG);

            hoveredOption = -1;
            if (mouseX >= x && mouseX < x + w && mouseY >= dropdownY && mouseY < dropdownY + options.size() * h) {
                hoveredOption = (mouseY - dropdownY) / h;
            }

            for (int i = 0; i < options.size(); i++) {
                int optY = dropdownY + i * h;
                int optTextY = optY + (h - 8) / 2;

                if (i == hoveredOption) {
                    guiGraphics.fill(x, optY, x + w, optY + h, HOVER_BG);
                }

                int optColor = (i == selectedIndex) ? textColor : ((alpha << 24) | 0x00CCCCCC);
                guiGraphics.text(font, Component.literal(options.get(i)), x + 8, optTextY, optColor, false);
            }
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (!open) {
            open = true;
            this.height = closedHeight + options.size() * closedHeight;
            if (onToggleOpen != null) onToggleOpen.run();
            return;
        }

        if (hoveredOption >= 0 && hoveredOption < options.size()) {
            selectedIndex = hoveredOption;
            if (onSelectionChanged != null) onSelectionChanged.accept(selectedIndex);
        }

        open = false;
        this.height = closedHeight;
        if (onToggleOpen != null) onToggleOpen.run();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
