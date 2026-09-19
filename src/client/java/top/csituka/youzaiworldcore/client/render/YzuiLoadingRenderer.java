package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * 首次启动与资源重载共用的主题卡片。仅用几何和原版提前注册的标志纹理，
 * 不访问字体、语言管理器或尚未完成重载的模组纹理。
 */
public final class YzuiLoadingRenderer {
    // 3×5 数字字形；初次重载前 FontManager 尚不可用，百分比直接绘制几何像素。
    private static final String[] DIGITS = {
            "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
            "111100111001111", "111100111101111", "111001001001001", "111101111101111", "111101111001111"
    };

    public record Layout(int x, int y, int width, int height, int logoWidth, int barX, int barY, int barWidth) {
        public static Layout of(int screenWidth, int screenHeight) {
            int width = Math.min(420, screenWidth - 40);
            int height = Math.min(184, screenHeight - 40);
            int x = (screenWidth - width) / 2;
            int y = (screenHeight - height) / 2;
            return new Layout(x, y, width, height, Math.min(276, width - 56),
                    x + 28, y + height - 56, width - 56);
        }
    }

    private YzuiLoadingRenderer() { }

    /** 在原版已准备好的背景层绘制，保留其淡入/淡出透明度与绘制顺序。 */
    public static void background(GuiGraphicsExtractor g, float alpha) {
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), YzuiTheme.alpha(YzuiTheme.palette().background(), alpha));
    }

    public static void render(GuiGraphicsExtractor g, Identifier earlyLogo, float progress, float alpha) {
        if (alpha <= 0f) return;
        Layout l = Layout.of(g.guiWidth(), g.guiHeight());
        var p = YzuiTheme.palette();
        // 纯几何背景与实体卡片无需额外资源；标志、进度和卡片一起淡出。
        RoundedRect.fill(g, l.x() - 14, l.y() + 18, l.width() + 28, l.height() - 36, 24,
                YzuiTheme.alpha(p.primaryContainer(), alpha * 0.24f));
        RoundedRect.fill(g, l.x(), l.y(), l.width(), l.height(), 16,
                YzuiTheme.alpha(p.outlineVariant(), alpha * 0.65f));
        RoundedRect.fill(g, l.x() + 1, l.y() + 1, l.width() - 2, l.height() - 2, 15,
                YzuiTheme.alpha(p.surface(), alpha));
        int center = g.guiWidth() / 2;
        for (int i = 0; i < 3; i++) {
            RoundedRect.fill(g, center - 15 + i * 12, l.y() + 22, 6, 6, 3,
                    YzuiTheme.alpha(i == 1 ? p.primary() : p.primaryContainer(), alpha));
        }
        // 提前注册的纹理也读取官网彩色标志，整图等比绘制并保留原色。
        YzuiBrandLogo.render(g, earlyLogo, center - l.logoWidth() / 2, l.y() + 50, l.logoWidth(), alpha);
        int tint = YzuiTheme.alpha(p.primary(), alpha);
        float value = Float.isFinite(progress) ? Math.clamp(progress, 0f, 1f) : 0f;
        RoundedRect.fill(g, l.barX(), l.barY(), l.barWidth(), 6, 3, YzuiTheme.alpha(p.surfaceHigh(), alpha));
        int filled = Math.round(l.barWidth() * value);
        if (filled > 0) RoundedRect.fill(g, l.barX(), l.barY(), filled, 6, 3, tint);
        percentage(g, center, l.barY() + 18, Math.round(value * 100), YzuiTheme.alpha(p.textMuted(), alpha));
    }

    private static void percentage(GuiGraphicsExtractor g, int center, int y, int percentage, int color) {
        String digits = Integer.toString(percentage);
        int x = center - (digits.length() * 8 + 10) / 2;
        for (int i = 0; i < digits.length(); i++) {
            glyph(g, DIGITS[digits.charAt(i) - '0'], x + i * 8, y, color);
        }
        glyph(g, "101001010100101", x + digits.length() * 8 + 2, y, color);
    }

    private static void glyph(GuiGraphicsExtractor g, String bits, int x, int y, int color) {
        for (int i = 0; i < 15; i++) {
            if (bits.charAt(i) == '1') g.fill(x + i % 3 * 2, y + i / 3 * 2,
                    x + i % 3 * 2 + 2, y + i / 3 * 2 + 2, color);
        }
    }
}
