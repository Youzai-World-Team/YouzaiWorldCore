package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.util.DebugLogger;

/** 与全屏地图共用的关闭按钮：主题圆角底色、线条图标、悬停提示与键盘激活。 */
public final class YzuiCloseButton extends TransparentButton {
    private final YzuiHover hover = new YzuiHover();

    /** 使用已有的多语言关闭文案，不将字形作为按钮图标。 */
    public YzuiCloseButton(int x, int y, int size, Runnable action) {
        this(x, y, size, Component.translatable("screen.youzaiworldcore.account_management.button_close"), action);
    }

    /** 允许地图保留自身提示文案，所有界面共用同一套图标与交互样式。 */
    public YzuiCloseButton(int x, int y, int size, @NonNull Component label, Runnable action) {
        super(x, y, size, size, label, () -> {
            DebugLogger.debug("YzuiCloseButton", "激活关闭按钮");
            action.run();
        });
        setTooltip(Tooltip.create(label));
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!visible) return;
        isHovered = isMouseOver(mouseX, mouseY);
        float opacity = getAlpha() * externalAlpha();
        YzuiTheme.button(g, getX(), getY(), getWidth(), getHeight(), hover.sample(active && isHovered),
                isFocused(), active, opacity, YzuiTheme.ButtonStyle.TONAL);
        int color = YzuiTheme.alpha(YzuiTheme.buttonText(YzuiTheme.ButtonStyle.TONAL, active), opacity);
        float size = getWidth() * 0.6f;
        g.pose().pushMatrix();
        g.pose().translate(getX() + (getWidth() - size) / 2, getY() + getHeight() * 0.2f);
        g.pose().scale(size / 16, size / 16);
        for (int i = 0; i <= 10; i++) {
            int x = 3 + i;
            g.fill(x, x, x + 1, x + 1, color);
            int y = 13 - i;
            g.fill(x, y, x + 1, y + 1, color);
        }
        g.pose().popMatrix();
    }
}
