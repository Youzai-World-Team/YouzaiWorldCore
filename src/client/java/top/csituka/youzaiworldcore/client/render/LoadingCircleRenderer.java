package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 启动加载界面的 8-bit 加载圈组件。
 */
public final class LoadingCircleRenderer {

    public static final int VISUAL_RADIUS = 11;

    private static final String MODULE_NAME = "LoadingCircle";
    private static final int BLOCK_SIZE = 3;
    private static final int BLOCK_COUNT = 16;

    private static final long ANIMATION_DURATION = 2_000L;
    private static final long PHASE_STEP = 125L;
    private static final long OPAQUE_END = 1_000L;
    private static final long FADE_END = 1_020L;

    private static final int[][] BLOCK_OFFSETS = {
            {-1, -10}, {2, -10}, {5, -7}, {8, -4},
            {8, -1}, {8, 2}, {5, 5}, {2, 8},
            {-1, 8}, {-4, 8}, {-7, 5}, {-10, 2},
            {-10, -1}, {-10, -4}, {-7, -7}, {-4, -10}
    };

    private long animationStartMillis = -1L;

    /**
     * 绘制加载圈，并将组件透明度与启动加载层的透明度相乘。
     *
     * @param graphics 图形提取器
     * @param centerX  加载圈可见区域中心 X
     * @param centerY  加载圈可见区域中心 Y
     * @param alpha    启动加载层透明度，范围为 0.0 到 1.0
     */
    public void render(GuiGraphicsExtractor graphics, int centerX, int centerY, float alpha) {
        long now = Util.getMillis();
        if (this.animationStartMillis == -1L) {
            this.animationStartMillis = now;
            DebugLogger.info(MODULE_NAME, "启动加载圈组件已开始渲染");
        }

        int overlayAlpha = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        if (overlayAlpha == 0) {
            return;
        }

        long elapsedMillis = Math.max(0L, now - this.animationStartMillis);
        for (int index = 0; index < BLOCK_COUNT; index++) {
            float blockOpacity = opacityAt(elapsedMillis, index);
            int blockAlpha = Math.round(overlayAlpha * blockOpacity);
            if (blockAlpha == 0) {
                continue;
            }

            int left = centerX + BLOCK_OFFSETS[index][0];
            int top = centerY + BLOCK_OFFSETS[index][1];
            graphics.fill(left, top, left + BLOCK_SIZE, top + BLOCK_SIZE,
                    ARGB.color(blockAlpha, 255, 255, 255));
        }
    }

    private static float opacityAt(long elapsedMillis, int index) {
        long negativeDelayOffset = (BLOCK_COUNT - 1L - index) * PHASE_STEP;
        long phase = Math.floorMod(elapsedMillis * 2L + negativeDelayOffset, ANIMATION_DURATION);

        if (phase <= OPAQUE_END) {
            return 1.0f;
        }
        if (phase >= FADE_END) {
            return 0.0f;
        }

        float fadeProgress = (float) (phase - OPAQUE_END) / (FADE_END - OPAQUE_END);
        return 1.0f - cssEase(fadeProgress);
    }

    private static float cssEase(float progress) {
        double lower = 0.0;
        double upper = 1.0;

        for (int iteration = 0; iteration < 16; iteration++) {
            double t = (lower + upper) * 0.5;
            double x = cubicBezier(t, 0.25, 0.25);
            if (x < progress) {
                lower = t;
            } else {
                upper = t;
            }
        }

        double t = (lower + upper) * 0.5;
        return (float) cubicBezier(t, 0.1, 1.0);
    }

    private static double cubicBezier(double t, double firstControl, double secondControl) {
        double inverse = 1.0 - t;
        return 3.0 * inverse * inverse * t * firstControl
                + 3.0 * inverse * t * t * secondControl
                + t * t * t;
    }
}
