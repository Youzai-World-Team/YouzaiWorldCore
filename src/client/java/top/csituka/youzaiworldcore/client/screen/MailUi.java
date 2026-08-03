package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * 邮件界面的共享视觉常量与绘制工具。
 */
final class MailUi {

    static final int PAGE_BACKGROUND = 0xF20D0D0D;
    static final int PANEL_BACKGROUND = 0xF2555555;
    static final int PANEL_HEADER = 0xFF666666;
    static final int ROW_SELECTED = 0xFF999999;
    static final int ROW_HOVERED = 0xFF6A6A6A;
    static final int ROW_ALTERNATE = 0xFF595959;
    static final int INPUT_BACKGROUND = 0xFF4C4C4C;
    static final int DIVIDER = 0xFF707070;
    static final int TEXT_PRIMARY = 0xFFFFFFFF;
    static final int TEXT_SECONDARY = 0xFFB8B8B8;
    static final int TEXT_MUTED = 0xFF888888;
    static final int GREEN = 0xFF55FF55;
    static final int YELLOW = 0xFFFFD800;
    static final int RED = 0xFFFF5555;
    static final int ORANGE = 0xFFFFB000;

    private MailUi() {
    }

    /** 绘制覆盖游戏画面的深色背景。 */
    static void drawBackdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0xE8202020);
    }

    /** 绘制参考稿中的深色页面容器。 */
    static void drawPage(GuiGraphicsExtractor graphics, Rect page) {
        graphics.fill(page.x(), page.y(), page.right(), page.bottom(), PAGE_BACKGROUND);
    }

    /**
     * 计算居中的页面容器，保留小屏幕安全边距。
     */
    static Rect centeredPage(int width, int height, int maxWidth, int maxHeight) {
        int pageWidth = Math.min(maxWidth, Math.max(1, width - 28));
        int pageHeight = Math.min(maxHeight, Math.max(1, height - 28));
        return new Rect((width - pageWidth) / 2, (height - pageHeight) / 2, pageWidth, pageHeight);
    }

    /** 绘制圆角矩形。 */
    static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        graphics.fill(x + r, y, x + width - r, y + height, color);
        graphics.fill(x, y + r, x + width, y + height - r, color);
        for (int ix = 0; ix < r; ix++) {
            for (int iy = 0; iy < r; iy++) {
                int dx = r - 1 - ix;
                int dy = r - 1 - iy;
                if (dx * dx + dy * dy < r * r) {
                    graphics.fill(x + ix, y + iy, x + ix + 1, y + iy + 1, color);
                    graphics.fill(x + width - 1 - ix, y + iy, x + width - ix, y + iy + 1, color);
                    graphics.fill(x + ix, y + height - 1 - iy, x + ix + 1, y + height - iy, color);
                    graphics.fill(x + width - 1 - ix, y + height - 1 - iy,
                            x + width - ix, y + height - iy, color);
                }
            }
        }
    }

    /** 绘制手动交互按钮。 */
    static void button(GuiGraphicsExtractor graphics, Font font, Rect rect, String label,
                       int background, int textColor, boolean hovered, boolean enabled) {
        int color = enabled ? background : 0xFF4C4C4C;
        if (hovered && enabled) {
            color = brighten(color, 22);
        }
        roundedRect(graphics, rect.x(), rect.y(), rect.width(), rect.height(), 5, color);
        int labelColor = enabled ? textColor : TEXT_MUTED;
        int textX = rect.x() + (rect.width() - font.width(label)) / 2;
        int textY = rect.y() + (rect.height() - font.lineHeight) / 2;
        graphics.text(font, label, textX, textY, labelColor, false);
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

    private static int brighten(int color, int amount) {
        int alpha = color >>> 24;
        int red = Math.min(255, ((color >> 16) & 0xFF) + amount);
        int green = Math.min(255, ((color >> 8) & 0xFF) + amount);
        int blue = Math.min(255, (color & 0xFF) + amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
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
