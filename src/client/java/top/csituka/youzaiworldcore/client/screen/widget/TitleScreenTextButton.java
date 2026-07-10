package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 无背景文字按钮，鼠标悬浮时文字向右平滑滑动，下方从左往右延伸出一条横线。
 */
public class TitleScreenTextButton extends AbstractWidget {

    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int TEXT_COLOR_HOVER = 0xFFFFFFFF;
    private static final int UNDERLINE_COLOR = 0xFF88FF88;
    private static final float ANIM_SPEED = 0.25f;
    /** 悬浮时文字向右偏移的像素 */
    private static final int HOVER_SHIFT = 6;

    private final Runnable onPress;
    private float underlineProgress = 0f; // 0.0 ~ 1.0
    private float shiftProgress = 0f;     // 0.0 ~ 1.0，向右滑动的动画进度

    public TitleScreenTextButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        var font = Minecraft.getInstance().font;
        String text = this.getMessage().getString();
        int textWidth = font.width(text);

        // 统一更新动画进度（悬浮 → target=1，离开 → target=0）
        float target = this.isHovered() ? 1.0f : 0.0f;
        if (Math.abs(underlineProgress - target) < 0.001f) {
            underlineProgress = target;
            shiftProgress = target;
        } else {
            underlineProgress += (target - underlineProgress) * ANIM_SPEED;
            shiftProgress += (target - shiftProgress) * ANIM_SPEED;
        }

        // 文字位置（左对齐，垂直居中；shiftProgress 控制平滑右移）
        int textX = this.getX() + Math.round(shiftProgress * HOVER_SHIFT);
        int textY = this.getY() + (this.height - 8) / 2;

        // 文字颜色（悬浮时变亮）
        int color = this.isHovered() ? TEXT_COLOR_HOVER : TEXT_COLOR;
        guiGraphics.text(font, this.getMessage(), textX, textY, color, false);

        // 下划线（从左往右延伸；位置跟随文字偏移）
        if (underlineProgress > 0.001f) {
            int underlineY = textY + 8 + 2; // 文字底部 + 2px 间距
            int underlineWidth = (int) (textWidth * underlineProgress);
            int underlineEndX = textX + underlineWidth;

            double smoothProgress = Mth.clamp(underlineProgress * 1.2, 0.0, 1.0);
            int alpha = (int) (smoothProgress * 255);
            int lineColor = (alpha << 24) | (UNDERLINE_COLOR & 0x00FFFFFF);

            guiGraphics.fill(textX, underlineY, underlineEndX, underlineY + 1, lineColor);
        }
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
