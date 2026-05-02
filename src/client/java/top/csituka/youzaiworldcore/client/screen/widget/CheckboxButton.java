package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class CheckboxButton extends AbstractWidget {

    private static final int TEXT_COLOR = 0x00FFFFFF;

    private boolean checked;
    private final Runnable onToggle;
    private float externalAlpha = 1f;

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

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int alpha = (int) (externalAlpha * 255);
        int textColor = (alpha << 24) | TEXT_COLOR;

        var font = Minecraft.getInstance().font;
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;

        int textY = y + (h - 8) / 2;
        guiGraphics.text(font, this.getMessage(), x + 4, textY, textColor, false);

        String box = checked ? "☑" : "☐";
        int boxWidth = font.width(box);
        guiGraphics.text(font, Component.literal(box), x + w - boxWidth - 4, textY, textColor, false);
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
