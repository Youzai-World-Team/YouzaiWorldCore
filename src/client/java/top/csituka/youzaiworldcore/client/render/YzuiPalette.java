package top.csituka.youzaiworldcore.client.render;

/**
 * 官网薄荷绿的 MD3 语义色。颜色均为不透明 ARGB，透明度由视觉效果层统一处理。
 * 主按钮使用官网深绿以保证文字对比度，薄荷绿与浅黄绿用于容器和选中状态。
 */
public record YzuiPalette(int background, int surface, int surfaceLow, int surfaceHigh,
        int primary, int onPrimary, int primaryContainer, int onPrimaryContainer,
        int secondaryContainer, int text, int textMuted, int outline, int outlineVariant,
        int error, int errorContainer, int success, int warning, int info) {

    private static final double[] LINEAR_CHANNELS = linearChannels();

    private static double[] linearChannels() {
        double[] channels = new double[256];
        for (int i = 0; i < channels.length; i++) {
            double c = i / 255.0;
            channels[i] = c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        }
        return channels;
    }

    /** WCAG 相对亮度，用于有色提示文字的可读性校验。 */
    public static double luminance(int color) {
        return 0.2126 * LINEAR_CHANNELS[(color >>> 16) & 255]
                + 0.7152 * LINEAR_CHANNELS[(color >>> 8) & 255]
                + 0.0722 * LINEAR_CHANNELS[color & 255];
    }

    public static double contrast(int first, int second) {
        double a = luminance(first), b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    /** 将半透明表面合成到背景，用于实际可见颜色的对比度检查。 */
    public static int composite(int surface, int background, float opacity) {
        float a = Math.clamp(opacity, 0f, 1f);
        int result = 0xFF000000;
        for (int shift = 0; shift < 24; shift += 8) {
            int foreground = (surface >>> shift) & 255;
            int behind = (background >>> shift) & 255;
            result |= Math.round(foreground * a + behind * (1f - a)) << shift;
        }
        return result;
    }

    /** 用最不利的黑/白世界背景校正小字号前景色。 */
    public int translucentText(int color, float opacity) {
        int behind = luminance(surface) < 0.18 ? 0xFFFFFF : 0;
        return readable(color, composite(surface, behind, opacity));
    }

    /** 只在对比不足时调节亮度，避免稀有度或状态色丢失。 */
    public static int readable(int color, int background) {
        int result = 0xFF000000 | (color & 0xFFFFFF);
        boolean lighten = luminance(background) < 0.18;
        for (int step = 0; step < 64 && contrast(result, background) < 4.5; step++) {
            int next = 0xFF000000;
            for (int shift = 0; shift < 24; shift += 8) {
                int channel = (result >>> shift) & 255;
                channel = lighten ? Math.min(255, channel + Math.max(1, (255 - channel) / 12))
                        : Math.max(0, channel - Math.max(1, channel / 12));
                next |= channel << shift;
            }
            result = next;
        }
        return result;
    }

    public static final YzuiPalette LIGHT = new YzuiPalette(
            0xFFF9FDFB, 0xFFF9FDFB, 0xFFE8F7F1, 0xFFDDEEE6,
            0xFF345E54, 0xFFFFFFFF, 0xFFA8E6CF, 0xFF193B31,
            0xFFDCEDC1, 0xFF1C3028, 0xFF4C6359, 0xFF6D8378, 0xFFC4D8CC,
            0xFFB3261E, 0xFFF9DEDC, 0xFF286544, 0xFF775900, 0xFF245D78);

    public static final YzuiPalette DARK = new YzuiPalette(
            0xFF101B17, 0xFF17251F, 0xFF1D3028, 0xFF2A4035,
            0xFFA8E6CF, 0xFF12382C, 0xFF2D5143, 0xFFC7F4DF,
            0xFF35452C, 0xFFE3F0E7, 0xFFB8CCBF, 0xFF879F91, 0xFF42594B,
            0xFFFFB4AB, 0xFF60302B, 0xFF9ED9B1, 0xFFE4C46C, 0xFFA5D5F1);
}
