package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiMenuPanel;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** Shift+F 入口卡片：图标和本地化文字独立绘制，焦点与悬停共用 MD3 状态层。 */
public final class MenuNavigationButton extends TransparentButton {
    private final MenuIcon icon;
    private final YzuiHover hoverState = new YzuiHover();

    public MenuNavigationButton(int x, int y, int width, int height, String name, MenuIcon icon, Runnable action) {
        super(x, y, width, height, Component.translatable("screen.youzaiworldcore.navigation." + name), action);
        this.icon = icon;
        setTooltip(Tooltip.create(getMessage()));
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        float hover = hoverState.sample(active && isMouseOver(mouseX, mouseY));
        float opacity = externalAlpha() * getAlpha() * (active ? 1f : 0.45f);
        int surface = YzuiTheme.mix(YzuiTheme.palette().surface(), YzuiTheme.primaryContainer(),
                0.08f + hover * 0.18f + (isFocused() ? 0.08f : 0));
        RoundedRect.fill(g, x, y, w, h, 12, YzuiTheme.alpha(surface, YzuiMenuPanel.opacity() * opacity));
        YzuiTheme.border(g, x, y, w, h, 12, YzuiTheme.alpha(isFocused() ? YzuiTheme.primary() : YzuiTheme.outline(),
                opacity * (isFocused() ? 1f : 0.26f + hover * 0.3f)));
        icon.render(g, x + 12, y + 9, YzuiTheme.alpha(YzuiTheme.primary(), opacity));
        var font = Minecraft.getInstance().font;
        YzuiTheme.label(g, font, getMessage(), x + 12, y + h - font.lineHeight - 10, w - 24,
                YzuiTheme.alpha(YzuiMenuPanel.textOn(YzuiTheme.text(), surface), opacity), false);
    }
}
