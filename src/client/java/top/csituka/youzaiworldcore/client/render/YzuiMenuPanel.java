package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 菜单专用的单层半透明表面，不叠加阴影或模糊游戏画面。 */
public final class YzuiMenuPanel {
    private YzuiMenuPanel() { }

    public static float opacity() { return YzuiTheme.frosted() ? 0.70f : 0.74f; }

    /** 悬停色会改变底色，按当前表面与最不利的世界底色重新保证文字对比度。 */
    public static int textOn(int color, int surface) {
        int world = YzuiPalette.luminance(YzuiTheme.palette().surface()) < 0.18 ? 0xFFFFFF : 0;
        return YzuiPalette.readable(color, YzuiPalette.composite(surface, world, opacity()));
    }

    public static void card(GuiGraphicsExtractor g, int x, int y, int width, int height) {
        RoundedRect.fill(g, x, y, width, height, 16,
                YzuiTheme.alpha(YzuiTheme.palette().surface(), opacity()));
        YzuiTheme.border(g, x, y, width, height, 16,
                YzuiTheme.alpha(YzuiTheme.outline(), 0.32f));
    }

    /** 认证页面统一标题区，右侧保留关闭控件空间；受限页面只显示账户图标。 */
    public static void header(GuiGraphicsExtractor g, Font font, Component title, Component subtitle,
            int x, int y, int width) {
        RoundedRect.fill(g, x + 24, y + 16, 24, 24, 8,
                YzuiTheme.alpha(YzuiTheme.primaryContainer(), 0.55f));
        MenuIcon.ACCOUNT.render(g, x + 28, y + 20, YzuiTheme.primary());
        YzuiTheme.label(g, font, title, x + 58, y + 22, width - 110, YzuiTheme.text(), false);
        YzuiTheme.wrapped(g, font, subtitle, x + 24, y + 42, width - 48, 2, YzuiTheme.textMuted());
    }
}
