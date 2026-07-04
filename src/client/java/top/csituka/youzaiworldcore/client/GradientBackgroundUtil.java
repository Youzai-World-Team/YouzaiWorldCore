package top.csituka.youzaiworldcore.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/**
 * 共享工具：绘制 135°（左上到右下）线性渐变背景。
 * 颜色从 {@code #A8E6CF} 渐变到 {@code #DCEDC1}。
 */
public final class GradientBackgroundUtil {

    /** 起始色（#A8E6CF） */
    private static final int COLOR_START = 0xFFA8E6CF;

    /** 结束色（#DCEDC1） */
    private static final int COLOR_END = 0xFFDCEDC1;

    /** 每个垂直条的像素宽度，越小越平滑 */
    private static final int STRIP_WIDTH = 2;

    private GradientBackgroundUtil() {}

    /**
     * 在整个画布上绘制 135° 对角线渐变。
     *
     * @param graphics 图形提取器
     * @param width    画布宽度
     * @param height   画布高度
     * @param alpha    整体透明度（0-255），255=不透明
     */
    public static void drawDiagonalGradient(GuiGraphicsExtractor graphics, int width, int height, int alpha) {
        // 将画布分为多条细垂直条，每条用 fillGradient 绘制一条垂直渐变
        // 配合水平位移实现 135° 对角线效果
        for (int x = 0; x < width; x += STRIP_WIDTH) {
            int x1 = x;
            int x2 = Math.min(x + STRIP_WIDTH, width);
            float centerX = (float) (x + x2) / 2 / width;

            // 135° 对角线进度：(x/width + y/height) / 2
            // 条上边缘进度
            float progressTop = (centerX + 0) / 2.0f;
            // 条下边缘进度
            float progressBottom = (centerX + 1.0f) / 2.0f;

            int topColor = lerpColor(COLOR_START, COLOR_END, progressTop);
            int bottomColor = lerpColor(COLOR_START, COLOR_END, progressBottom);

            // 应用全局透明度
            topColor = applyAlpha(topColor, alpha);
            bottomColor = applyAlpha(bottomColor, alpha);

            graphics.fillGradient(x1, 0, x2, height, topColor, bottomColor);
        }
    }

    /** 在两个 ARGB 颜色之间线性插值 */
    private static int lerpColor(int from, int to, float t) {
        if (t <= 0) return from;
        if (t >= 1) return to;
        int a = lerpComponent(ARGB.alpha(from), ARGB.alpha(to), t);
        int r = lerpComponent(ARGB.red(from), ARGB.red(to), t);
        int g = lerpComponent(ARGB.green(from), ARGB.green(to), t);
        int b = lerpComponent(ARGB.blue(from), ARGB.blue(to), t);
        return ARGB.color(a, r, g, b);
    }

    private static int lerpComponent(int c1, int c2, float t) {
        return Math.round(c1 + (c2 - c1) * t);
    }

    /** 替换颜色中的 alpha 分量 */
    private static int applyAlpha(int color, int newAlpha) {
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }
}
