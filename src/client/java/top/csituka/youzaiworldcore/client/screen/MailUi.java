package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

/**
 * 邮件界面的共享视觉常量与绘制工具。
 */
@SuppressWarnings("null")
final class MailUi {

    static int pageBackground() { return YzuiTheme.surface(); }
    static int panelBackground() { return YzuiTheme.surface(); }
    static int panelHeader() { return YzuiTheme.surfaceLow(); }
    static int rowSelected() { return YzuiTheme.primaryContainer(); }
    static int rowHovered() { return YzuiTheme.surfaceHigh(); }
    static int rowAlternate() { return YzuiTheme.surfaceLow(); }
    static int inputBackground() { return YzuiTheme.slot(); }
    static int divider() { return YzuiTheme.outlineVariant(); }
    static int textPrimary() { return YzuiTheme.text(); }
    static int textSecondary() { return YzuiTheme.textMuted(); }
    static int textMuted() { return YzuiTheme.textMuted(); }
    static int green() { return YzuiTheme.success(); }
    static int yellow() { return YzuiTheme.warning(); }
    static int red() { return YzuiTheme.error(); }
    static int orange() { return YzuiTheme.warning(); }

    private MailUi() {
    }

    /** 绘制主题页面容器。 */
    static void drawPage(GuiGraphicsExtractor graphics, Rect page) {
        YzuiTheme.card(graphics, page.x(), page.y(), page.width(), page.height());
    }

    /**
     * 在设计空间（{@link MailViewport#DESIGN_WIDTH}×{@link MailViewport#DESIGN_HEIGHT}）内
     * 计算居中的页面容器。
     * <p>
     * 真实屏幕尺寸的适配由 {@link MailViewport} 统一做等比缩放，这里不再参与，
     * 因此页面尺寸恒定，排版在任何界面尺寸下都保持一致。
     * </p>
     */
    static Rect centeredPage(int maxWidth, int maxHeight) {
        int pageWidth = Math.min(maxWidth, MailViewport.DESIGN_WIDTH - 28);
        int pageHeight = Math.min(maxHeight, MailViewport.DESIGN_HEIGHT - 28);
        return new Rect((MailViewport.DESIGN_WIDTH - pageWidth) / 2,
                (MailViewport.DESIGN_HEIGHT - pageHeight) / 2, pageWidth, pageHeight);
    }

    /**
     * 绘制圆角矩形。
     * <p>三块矩形必须互不重叠：中间列 + 左右侧条。若改用「整宽横条 + 竖条」两块写法，
     * 二者会在中央重叠，半透明色被混合两次，呈现「中心偏实、四周偏透」的假边框
     * （宽度恰为 {@code radius}）。多数调用使用不透明色看不出差异，但
     * {@link #panelBackground()}、邮件类型标签（alpha {@code 0x55}）、
     * {@link MailToast} 与 {@link #yzuiInputBackground} 均为半透明，必须避免重叠。</p>
     */
    static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        // 行扫描实现，见 RoundedRect：中段与圆角行严格互不重叠，
        // 半透明色（PANEL_BACKGROUND / 邮件类型标签 / MailToast / yzuiInputBackground）
        // 不会被二次混合。点亮像素与原逐像素实现完全一致。
        RoundedRect.fill(graphics, x, y, width, height, radius, color);
    }

    /** 绘制手动交互按钮。 */
    static void button(GuiGraphicsExtractor graphics, Font font, Rect rect, String label,
                       int background, int textColor, boolean hovered, boolean enabled) {
        int color = enabled ? background : YzuiTheme.surfaceHigh();
        int foreground = enabled ? textColor : textMuted();
        if (hovered && enabled) color = YzuiTheme.mix(color, foreground, 0.08f);
        boolean filled = enabled && (background & 0xFFFFFF) == (YzuiTheme.primary() & 0xFFFFFF);
        RoundedRect.fill(graphics, rect.x(), rect.y(), rect.width(), rect.height(),
                Math.min(10, rect.height() / 2), YzuiTheme.multiplyAlpha(color, filled ? 1f : YzuiTheme.visualStyle().controlOpacity()));
        if (hovered && enabled) YzuiTheme.border(graphics, rect.x(), rect.y(), rect.width(), rect.height(),
                Math.min(10, rect.height() / 2), YzuiTheme.alpha(filled ? YzuiTheme.onPrimary() : YzuiTheme.primary(), 0.32f));
        YzuiTheme.label(graphics, font, Component.literal(label), rect.x() + 8,
                rect.y() + (rect.height() - font.lineHeight) / 2, rect.width() - 16, foreground, true);
    }

    /** 将文本裁剪为指定宽度并追加省略号。 */
    static String ellipsize(Font font, String text, int maxWidth) {
        String value = text == null || text.isBlank() ? "-" : text;
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(suffix))) + suffix;
    }

    /** 在给定矩形中绘制居中文字。 */
    static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, Rect rect, int color) {
        int x = rect.x() + (rect.width() - font.width(text)) / 2;
        int y = rect.y() + (rect.height() - font.lineHeight) / 2;
        graphics.text(font, text, x, y, color, false);
    }

    /** 绘制 YZUI 风格圆角输入框背景。 */
    static void yzuiInputBackground(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                    boolean enabled) {
        YzuiTheme.field(graphics, x, y, width, height, false, enabled, enabled ? 1f : 0.65f);
    }

    /** 简单的不可变界面矩形。 */
    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < right() && mouseY >= y && mouseY < bottom();
        }
    }
}
