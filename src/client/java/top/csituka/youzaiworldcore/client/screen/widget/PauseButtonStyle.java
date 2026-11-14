package top.csituka.youzaiworldcore.client.screen.widget;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import top.csituka.youzaiworldcore.client.config.ClientExternalSettings;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 为原版暂停按钮附加图标与操作层级，保留按钮实例、回调、提示及禁用状态。 */
public final class PauseButtonStyle {
    private static final Map<AbstractButton, Decoration> STYLES = new WeakHashMap<>();

    private PauseButtonStyle() { }

    /** 弱引用随页面回收，不保存配置或玩家数据。 */
    public static void set(AbstractButton button, MenuIcon icon, YzuiTheme.ButtonStyle style) {
        STYLES.put(button, new Decoration(icon, style));
    }

    /** 返回是否接管背景；其余按钮继续使用共用控件样式。 */
    public static boolean background(GuiGraphicsExtractor g, AbstractButton button, float hover) {
        Decoration decoration = get(button);
        if (decoration == null) return false;
        int surface = surface(decoration, hover, button.isFocused());
        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        float alpha = button.getAlpha() * (button.active ? 1f : 0.45f);
        boolean filled = decoration.style() == YzuiTheme.ButtonStyle.FILLED;
        RoundedRect.fill(g, x, y, w, h, Math.min(12, h / 2),
                YzuiTheme.alpha(surface, alpha * (filled ? 1f : YzuiMenuPanel.opacity())));
        YzuiTheme.border(g, x, y, w, h, Math.min(12, h / 2),
                YzuiTheme.alpha(button.isFocused() ? YzuiTheme.primary() : YzuiTheme.outline(),
                        alpha * (button.isFocused() ? 1f : 0.28f + hover * 0.25f)));
        return true;
    }

    /** 在文字前绘制线性图标；完整名称继续由原版 tooltip 和朗读提供。 */
    public static boolean label(GuiGraphicsExtractor g, AbstractButton button, float hover) {
        Decoration decoration = get(button);
        if (decoration == null) return false;
        int surface = surface(decoration, hover, button.isFocused());
        int color = decoration.style() == YzuiTheme.ButtonStyle.FILLED ? YzuiTheme.onPrimary()
                : YzuiMenuPanel.textOn(decoration.style() == YzuiTheme.ButtonStyle.DANGER
                        ? YzuiTheme.palette().error() : YzuiTheme.palette().text(), surface);
        color = YzuiTheme.alpha(color, button.getAlpha() * (button.active ? 1f : 0.68f));
        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        decoration.icon().render(g, x + 9, y + (h - 16) / 2, color);
        var font = Minecraft.getInstance().font;
        // 退出按钮右侧留空，保留原版未提交举报草稿的提示图标。
        int trailing = decoration.style() == YzuiTheme.ButtonStyle.DANGER ? 23 : 9;
        YzuiTheme.label(g, font, button.getMessage(), x + 32, y + (h - font.lineHeight) / 2,
                w - 32 - trailing, color, false);
        return true;
    }

    private static Decoration get(AbstractButton button) {
        return ClientExternalSettings.isYzuiEnabled() ? STYLES.get(button) : null;
    }

    private static int surface(Decoration decoration, float hover, boolean focused) {
        var p = YzuiTheme.palette();
        int base = switch (decoration.style()) {
            case FILLED -> p.primary();
            case DANGER -> YzuiTheme.mix(p.surface(), p.errorContainer(), 0.24f);
            default -> p.surface();
        };
        int tint = decoration.style() == YzuiTheme.ButtonStyle.FILLED ? p.onPrimary() : p.primaryContainer();
        return YzuiTheme.mix(base, tint, hover * 0.12f + (focused ? 0.06f : 0));
    }

    private record Decoration(MenuIcon icon, YzuiTheme.ButtonStyle style) { }
}
