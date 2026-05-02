package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class TransparentButton extends AbstractWidget {

    private static final int BACKGROUND_COLOR = 0xFFFFFF;
    private static final int TEXT_COLOR = 0xFF000000;
    private static final int CORNER_RADIUS = 6;

    private final Runnable onPress;
    private float currentAlpha = 0.5f;
    private float targetAlpha = 0.5f;
    private float externalAlpha = 1f;
    private boolean backgroundVisible = true;
    private int textColorRgb = TEXT_COLOR & 0x00FFFFFF;
    private boolean textLeftAligned = false;
    private static final float LERP_SPEED = 0.15f;

    public TransparentButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public void setExternalAlpha(float alpha) {
        this.externalAlpha = alpha;
    }

    public void setBackgroundVisible(boolean visible) {
        this.backgroundVisible = visible;
    }

    public void setTextColor(int rgb) {
        this.textColorRgb = rgb & 0x00FFFFFF;
    }

    public void setTextLeftAligned(boolean leftAligned) {
        this.textLeftAligned = leftAligned;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        targetAlpha = this.isHovered() ? 0.69f : 0.5f;
        currentAlpha = lerp(currentAlpha, targetAlpha, LERP_SPEED);
        
        float finalAlpha = currentAlpha * externalAlpha;
        int backgroundColor = colorWithAlpha(BACKGROUND_COLOR, finalAlpha);
        
        int x = this.getX();
        int y = this.getY();
        int width = this.width;
        int height = this.height;
        int r = CORNER_RADIUS;

        if (backgroundVisible) {
            fillRoundedRect(guiGraphics, x, y, width, height, r, backgroundColor);
        }

        int textColor = colorWithAlpha(textColorRgb, externalAlpha);
        var font = Minecraft.getInstance().font;
        String text = this.getMessage().getString();
        int textWidth = font.width(text);
        int textX = textLeftAligned ? x + 4 : x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        
        guiGraphics.text(font, this.getMessage(), textX, textY, textColor, false);
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }

    private int colorWithAlpha(int color, float alpha) {
        int a = (int) (alpha * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private float lerp(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.001f) {
            return target;
        }
        return current + (target - current) * speed;
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
