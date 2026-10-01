package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** 菜单的 16×16 线性图标；几何绘制跟随主题，不依赖字体符号或图片内文字。 */
public enum MenuIcon {
    WORLD, MAIL, LEVEL, ACCOUNT, CROWN, SETTINGS, WEBSITE, BOOK, EVENTS, SURVEY, REPORT, ADMIN,
    HOME, GAME, BUILD, COMMAND, PORTAL, CLOSE, BACK;

    /** 在逻辑 GUI 坐标中绘制图标，颜色已包含调用方的透明度。 */
    public void render(GuiGraphicsExtractor g, int x, int y, int color) {
        switch (this) {
            case CLOSE -> {
                line(g, x, y, 4, 4, 12, 12, color);
                line(g, x, y, 12, 4, 4, 12, color);
            }
            case BACK -> {
                line(g, x, y, 9, 3, 4, 8, color);
                line(g, x, y, 4, 8, 9, 13, color);
                line(g, x, y, 4, 8, 13, 8, color);
            }
            case MAIL -> {
                box(g, x, y, 2, 4, 12, 9, color);
                line(g, x, y, 2, 4, 8, 9, color);
                line(g, x, y, 8, 9, 14, 4, color);
            }
            case ACCOUNT -> {
                box(g, x, y, 6, 2, 4, 4, color);
                line(g, x, y, 3, 13, 3, 10, color);
                line(g, x, y, 3, 10, 6, 8, color);
                line(g, x, y, 6, 8, 10, 8, color);
                line(g, x, y, 10, 8, 13, 10, color);
                line(g, x, y, 13, 10, 13, 13, color);
                line(g, x, y, 3, 13, 13, 13, color);
            }
            case WORLD, WEBSITE -> {
                YzuiTheme.border(g, x + 1, y + 1, 15, 15, 7, color);
                YzuiTheme.border(g, x + 5, y + 1, 7, 15, 3, color);
                line(g, x, y, 2, 8, 14, 8, color);
            }
            case LEVEL -> {
                line(g, x, y, 2, 13, 2, 9, color);
                line(g, x, y, 6, 13, 6, 6, color);
                line(g, x, y, 10, 13, 10, 3, color);
                line(g, x, y, 14, 13, 14, 1, color);
            }
            case CROWN -> {
                line(g, x, y, 2, 4, 4, 12, color);
                line(g, x, y, 4, 12, 12, 12, color);
                line(g, x, y, 12, 12, 14, 4, color);
                line(g, x, y, 14, 4, 10, 7, color);
                line(g, x, y, 10, 7, 8, 2, color);
                line(g, x, y, 8, 2, 6, 7, color);
                line(g, x, y, 6, 7, 2, 4, color);
            }
            case SETTINGS -> {
                for (int row = 0; row < 3; row++) {
                    int yy = 3 + row * 5, knob = row == 1 ? 10 : 5;
                    line(g, x, y, 2, yy, 14, yy, color);
                    box(g, x, y, knob - 1, yy - 1, 2, 2, color);
                }
            }
            case BOOK -> {
                box(g, x, y, 2, 2, 12, 12, color);
                line(g, x, y, 7, 2, 7, 14, color);
                line(g, x, y, 10, 5, 12, 5, color);
            }
            case EVENTS -> {
                box(g, x, y, 2, 3, 12, 11, color);
                line(g, x, y, 2, 6, 14, 6, color);
                line(g, x, y, 5, 1, 5, 4, color);
                line(g, x, y, 11, 1, 11, 4, color);
                box(g, x, y, 5, 9, 2, 2, color);
            }
            case SURVEY -> {
                box(g, x, y, 3, 2, 10, 12, color);
                line(g, x, y, 6, 5, 10, 5, color);
                line(g, x, y, 6, 8, 10, 8, color);
                line(g, x, y, 6, 11, 10, 11, color);
            }
            case REPORT, ADMIN -> {
                line(g, x, y, 2, 3, 8, 1, color);
                line(g, x, y, 8, 1, 14, 3, color);
                line(g, x, y, 14, 3, 13, 10, color);
                line(g, x, y, 13, 10, 8, 14, color);
                line(g, x, y, 8, 14, 3, 10, color);
                line(g, x, y, 3, 10, 2, 3, color);
                line(g, x, y, 8, 4, 8, 8, color);
                line(g, x, y, 8, 11, 8, 11, color);
            }
            case HOME -> {
                line(g, x, y, 1, 7, 8, 1, color);
                line(g, x, y, 8, 1, 15, 7, color);
                box(g, x, y, 3, 7, 10, 7, color);
                box(g, x, y, 7, 10, 3, 4, color);
            }
            case GAME -> {
                box(g, x, y, 1, 4, 14, 9, color);
                line(g, x, y, 3, 8, 7, 8, color);
                line(g, x, y, 5, 6, 5, 10, color);
                line(g, x, y, 11, 7, 11, 7, color);
                line(g, x, y, 13, 10, 13, 10, color);
            }
            case BUILD -> {
                box(g, x, y, 2, 2, 12, 12, color);
                line(g, x, y, 2, 6, 14, 6, color);
                line(g, x, y, 2, 10, 14, 10, color);
                line(g, x, y, 8, 2, 8, 6, color);
                line(g, x, y, 5, 6, 5, 10, color);
                line(g, x, y, 11, 6, 11, 10, color);
                line(g, x, y, 8, 10, 8, 14, color);
            }
            case COMMAND -> {
                line(g, x, y, 2, 4, 6, 8, color);
                line(g, x, y, 6, 8, 2, 12, color);
                line(g, x, y, 8, 12, 14, 12, color);
            }
            case PORTAL -> {
                box(g, x, y, 3, 1, 10, 14, color);
                box(g, x, y, 6, 4, 4, 11, color);
            }
        }
    }

    private static void box(GuiGraphicsExtractor g, int x, int y, int left, int top, int width, int height, int color) {
        YzuiTheme.border(g, x + left, y + top, width + 1, height + 1, 0, color);
    }

    private static void line(GuiGraphicsExtractor g, int x, int y, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0 : i / (float) steps;
            int xx = x + Math.round(x1 + (x2 - x1) * t);
            int yy = y + Math.round(y1 + (y2 - y1) * t);
            g.fill(xx, yy, xx + 1, yy + 1, color);
        }
    }
}
