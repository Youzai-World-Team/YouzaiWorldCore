package top.csituka.youzaiworldcore.client.animation;

import top.csituka.youzaiworldcore.client.config.YzuiVisualStyle;

/** 页面与弹窗共用的短促、无回弹过渡。几何换算不依赖 Minecraft，便于验证命中区域。 */
public final class YzuiMotion {
    private YzuiMotion() { }

    public static long enterDuration(YzuiVisualStyle style) { return style == YzuiVisualStyle.FROSTED ? 280L : 200L; }
    public static long exitDuration(YzuiVisualStyle style) { return style == YzuiVisualStyle.FROSTED ? 180L : 130L; }
    public static long switchDuration(YzuiVisualStyle style) { return style == YzuiVisualStyle.FROSTED ? 260L : 200L; }

    public static float progress(long elapsed, long duration) {
        return duration <= 0 ? 1f : Math.clamp(elapsed / (float) duration, 0f, 1f);
    }

    public static float decelerate(float progress) {
        float remaining = 1f - Math.clamp(progress, 0f, 1f);
        return 1f - remaining * remaining * remaining;
    }

    public static float accelerate(float progress) {
        float t = Math.clamp(progress, 0f, 1f);
        return t * t * t;
    }

    public static float distance(YzuiVisualStyle style, int height) {
        return Math.min(Math.max(0, height) * 0.025f, style == YzuiVisualStyle.FROSTED ? 12f : 7f);
    }

    public static Frame enter(YzuiVisualStyle style, float progress, int height) {
        if (style == YzuiVisualStyle.MINIMAL) return Frame.IDENTITY;
        return hidden(style, height).towards(Frame.IDENTITY, decelerate(progress));
    }

    public static Frame exit(YzuiVisualStyle style, Frame from, float progress, int height) {
        if (style == YzuiVisualStyle.MINIMAL) return Frame.IDENTITY;
        return from.towards(hidden(style, height), accelerate(progress));
    }

    private static Frame hidden(YzuiVisualStyle style, int height) {
        return new Frame(0f, style == YzuiVisualStyle.FROSTED ? 0.978f : 0.992f, distance(style, height));
    }

    public record Frame(float opacity, float scale, float offsetY) {
        public static final Frame IDENTITY = new Frame(1f, 1f, 0f);

        public float translateX(int width) { return width * (1f - scale) / 2f; }
        public float translateY(int height) { return height * (1f - scale) / 2f + offsetY; }
        public double toLocalX(double x, int width) { return (x - translateX(width)) / scale; }
        public double toLocalY(double y, int height) { return (y - translateY(height)) / scale; }
        public double toScreenX(double x, int width) { return x * scale + translateX(width); }
        public double toScreenY(double y, int height) { return y * scale + translateY(height); }

        public Frame towards(Frame target, float amount) {
            float t = Math.clamp(amount, 0f, 1f);
            return new Frame(opacity + (target.opacity - opacity) * t,
                    scale + (target.scale - scale) * t, offsetY + (target.offsetY - offsetY) * t);
        }
    }
}
