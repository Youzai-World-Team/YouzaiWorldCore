package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

@SuppressWarnings("null")
public class CheckboxButton extends AbstractWidget {

    private static final int TEXT_COLOR = 0x00FFFFFF;

    private boolean checked;
    private final Runnable onToggle;
    private float externalAlpha = 1f;
    private boolean wrapMessage;

    public CheckboxButton(int x, int y, int width, int height, Component message, boolean checked, Runnable onToggle) {
        super(x, y, width, height, message);
        this.checked = checked;
        this.onToggle = onToggle;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setExternalAlpha(float alpha) {
        this.externalAlpha = alpha;
    }

    /**
     * 启用标签自动换行。调用方应同时给控件提供足够的高度。
     */
    public CheckboxButton setWrapMessage(boolean wrapMessage) {
        this.wrapMessage = wrapMessage;
        return this;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) (externalAlpha * 255);
        int textColor = (alpha << 24) | TEXT_COLOR;

        var font = Minecraft.getInstance().font;
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        String box = checked ? "☑" : "☐";
        int boxWidth = font.width(box);
        int boxX = x + w - boxWidth - 4;

        if (!wrapMessage) {
            int textY = y + (h - 8) / 2;
            guiGraphics.text(font, this.getMessage(), x + 4, textY, textColor, false);
            guiGraphics.text(font, Component.literal(box), boxX, textY, textColor, false);
            return;
        }

        int maxTextWidth = Math.max(1, boxX - x - 8);
        List<FormattedCharSequence> lines = font.split(this.getMessage(), maxTextWidth);
        int lineHeight = font.lineHeight;
        int totalHeight = lines.size() * lineHeight + Math.max(0, lines.size() - 1) * 2;
        int textY = y + Math.max(0, (h - totalHeight) / 2);
        for (FormattedCharSequence line : lines) {
            guiGraphics.text(font, line, x + 4, textY, textColor, false);
            textY += lineHeight + 2;
        }

        int boxY = y + Math.max(0, (h - 8) / 2);
        guiGraphics.text(font, Component.literal(box), boxX, boxY, textColor, false);
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        checked = !checked;
        if (onToggle != null) {
            onToggle.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
