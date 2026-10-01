package top.csituka.youzaiworldcore.client.screen.map;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/** 全屏地图的线绘图标按钮；保留中文提示、朗读与键盘激活，无额外纹理依赖。 */
final class MapIconButton extends TransparentButton {
    enum Icon { MAP, CLOSE, PLUS, MINUS, CENTER, SETTINGS, PIN, PLAYERS, RADAR, PEN }
    private final Icon icon;
    private final YzuiHover hover = new YzuiHover();

    MapIconButton(int x, int y, int size, Component label, Icon icon, Runnable action) {
        super(x, y, size, size, label, action);
        this.icon = icon;
        setTooltip(Tooltip.create(label));
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        isHovered = isMouseOver(mx, my);
        YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), hover.sample(active && isHovered),
                isFocused(), active, 1, YzuiTheme.ButtonStyle.TONAL);
        int color = active ? YzuiTheme.onPrimaryContainer() : YzuiTheme.textMuted();
        g.pose().pushMatrix();
        g.pose().translate(getX() + getWidth() * 0.2f, getY() + getHeight() * 0.2f);
        float scale = getWidth() * 0.6f / 16;
        g.pose().scale(scale, scale);
        switch (icon) {
            case MAP -> {
                line(g, 1, 3, 5, 1, color); line(g, 5, 1, 11, 4, color); line(g, 11, 4, 15, 2, color);
                line(g, 1, 3, 1, 14, color); line(g, 5, 1, 5, 12, color); line(g, 11, 4, 11, 15, color); line(g, 15, 2, 15, 13, color);
                line(g, 1, 14, 5, 12, color); line(g, 5, 12, 11, 15, color); line(g, 11, 15, 15, 13, color);
            }
            case CLOSE -> { line(g, 3, 3, 13, 13, color); line(g, 3, 13, 13, 3, color); }
            case PLUS, MINUS -> { line(g, 2, 8, 14, 8, color); if (icon == Icon.PLUS) line(g, 8, 2, 8, 14, color); }
            case CENTER -> {
                circle(g, 8, 8, 5, color); circle(g, 8, 8, 1, color);
                line(g, 8, 0, 8, 4, color); line(g, 8, 12, 8, 16, color);
                line(g, 0, 8, 4, 8, color); line(g, 12, 8, 16, 8, color);
            }
            case SETTINGS -> {
                circle(g, 8, 8, 5, color); circle(g, 8, 8, 2, color);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    line(g, 8 + (int) Math.round(Math.cos(a) * 5), 8 + (int) Math.round(Math.sin(a) * 5),
                            8 + (int) Math.round(Math.cos(a) * 7), 8 + (int) Math.round(Math.sin(a) * 7), color);
                }
            }
            case PIN -> {
                circle(g, 8, 6, 5, color); circle(g, 8, 6, 1, color);
                line(g, 4, 9, 8, 15, color); line(g, 12, 9, 8, 15, color);
            }
            case PLAYERS -> {
                circle(g, 5, 5, 3, color); circle(g, 12, 5, 3, color);
                line(g, 3, 9, 0, 15, color); line(g, 7, 9, 10, 15, color); line(g, 0, 15, 10, 15, color);
                line(g, 14, 9, 16, 15, color); line(g, 12, 15, 16, 15, color);
            }
            case RADAR -> { circle(g, 8, 8, 7, color); circle(g, 8, 8, 4, color); line(g, 8, 8, 13, 2, color); }
            case PEN -> {
                line(g, 2, 14, 4, 9, color); line(g, 4, 9, 12, 1, color); line(g, 12, 1, 15, 4, color);
                line(g, 15, 4, 7, 12, color); line(g, 7, 12, 2, 14, color); line(g, 10, 3, 13, 6, color);
            }
        }
        g.pose().popMatrix();
    }

    private static void circle(GuiGraphicsExtractor g, int x, int y, int radius, int color) {
        for (int i = 0; i < 32; i++) {
            double a = i * Math.PI / 16, b = (i + 1) * Math.PI / 16;
            line(g, x + (int) Math.round(Math.cos(a) * radius), y + (int) Math.round(Math.sin(a) * radius),
                    x + (int) Math.round(Math.cos(b) * radius), y + (int) Math.round(Math.sin(b) * radius), color);
        }
    }

    private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2 || y1 == y2) {
            g.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
            return;
        }
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        for (int i = 0; i <= steps; i++) {
            int x = steps == 0 ? x1 : x1 + Math.round((x2 - x1) * (float) i / steps);
            int y = steps == 0 ? y1 : y1 + Math.round((y2 - y1) * (float) i / steps);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
