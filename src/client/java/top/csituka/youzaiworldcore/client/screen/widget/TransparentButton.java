package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

@SuppressWarnings("null")
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
        currentAlpha = GuiAnimationController.isEnabled()
                ? lerp(currentAlpha, targetAlpha, LERP_SPEED)
                : targetAlpha;

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
        Component msg = this.getMessage();
        int textWidth = font.width(msg);
        int availW = width - 8;  // 两侧 4px 边距后可用宽度
        int textY = y + (height - 8) / 2;

        if (textWidth > availW) {
            // 文字超宽 → 裁剪到按钮边界，悬停时横向滚动
            int textX = x + 4;
            // 始终往返滚动，头尾各停顿 2 秒
            int scrollRange = textWidth - availW;  // 恰好滚完多余部分，不附加空白
            int period = Math.max(2000, scrollRange * 30);
            int pauseMs = 2000;
            long cycle = period * 2 + pauseMs * 2;
            long t = System.currentTimeMillis() % cycle;
            int scrollPx;
            if (t < period) {
                // 前滚：0 → range
                scrollPx = (int)((float)t / period * scrollRange);
            } else if (t < period + pauseMs) {
                // 尾停顿：range
                scrollPx = scrollRange;
            } else if (t < period * 2 + pauseMs) {
                // 回滚：range → 0
                float p = (float)(t - period - pauseMs) / period;
                scrollPx = (int)((1.0f - p) * scrollRange);
            } else {
                // 头停顿：0
                scrollPx = 0;
            }
            guiGraphics.enableScissor(x, y, x + width, y + height);
            guiGraphics.text(font, msg, textX - scrollPx, textY, textColor, false);
            guiGraphics.disableScissor();
        } else {
            int textX = textLeftAligned ? x + 4 : x + (width - textWidth) / 2;
            guiGraphics.text(font, msg, textX, textY, textColor, false);
        }
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 圆角绘制统一走 RoundedRect（行扫描：r=6 时 135 次 fill -> 13 次）。
        // 点亮像素与原逐像素实现一致（45253 组尺寸/半径已逐一比对）；
        // 原实现未做尺寸校验，r > min(w,h)/2 时会画出坐标反转/重叠的结果，此处会钳制半径。
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    private int colorWithAlpha(int color, float alpha) {
        int a = (int) (Math.max(0, Math.min(255, alpha * 255)));
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
