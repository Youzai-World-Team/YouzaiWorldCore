package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.MenuIcon;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.YzuiMenuScreen;

/** 关闭控件与 Esc 共用同一权限；处理请求期间立即隐藏，不留下无效关闭入口。 */
public final class MenuCloseButton extends TransparentButton {
    private final YzuiMenuScreen owner;

    public MenuCloseButton(int x, int y, YzuiMenuScreen owner) {
        super(x, y, 28, 28, Component.translatable("screen.youzaiworldcore.account_management.button_close"), owner::closeMenu);
        this.owner = owner;
        setTooltip(Tooltip.create(Component.translatable("screen.youzaiworldcore.account_management.button_close").append(" · Esc")));
        refresh();
    }

    public void refresh() { active = visible = owner.canCloseMenu(); }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        refresh();
        if (!visible) return;
        YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), isMouseOver(mouseX, mouseY) ? 1f : 0f,
                isFocused(), true, getAlpha(), YzuiTheme.ButtonStyle.TEXT);
        MenuIcon.CLOSE.render(g, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2,
                YzuiTheme.alpha(YzuiTheme.text(), getAlpha()));
    }
}
