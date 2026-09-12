package top.csituka.youzaiworldcore.client.screen.widget;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * 邮件表单使用的紧凑复选框，方框位于标签左侧。
 */
@SuppressWarnings("null")
public class MailCheckboxButton extends AbstractWidget {

    private boolean checked;
    private final YzuiHover hoverState = new YzuiHover();
    private final Runnable onToggle;

    public MailCheckboxButton(int x, int y, int width, Component message, boolean checked, Runnable onToggle) {
        super(x, y, width, 14, message);
        this.checked = checked;
        this.onToggle = onToggle;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        YzuiTheme.button(graphics, getX() - 2, getY(), getWidth() + 4, getHeight(),
                hoverState.sample(active && isMouseOver(mouseX, mouseY)), isFocused(), active, getAlpha(),
                YzuiTheme.ButtonStyle.TEXT);
        int fill = checked ? YzuiTheme.primary() : YzuiTheme.surfaceHigh();
        if (!active) fill = YzuiTheme.surfaceHigh();
        top.csituka.youzaiworldcore.client.render.RoundedRect.fill(graphics, getX(), getY() + 2, 10, 10, 2, fill);
        YzuiTheme.border(graphics, getX(), getY() + 2, 10, 10, 2, active ? YzuiTheme.primary() : YzuiTheme.outline());
        if (checked) graphics.text(Minecraft.getInstance().font, "✓", getX() + 1, getY() + 1,
                active ? YzuiTheme.onPrimary() : YzuiTheme.textMuted(), false);
        YzuiTheme.label(graphics, Minecraft.getInstance().font, getMessage(), getX() + 14, getY() + 3,
                getWidth() - 14, active ? YzuiTheme.text() : YzuiTheme.textMuted(), false);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (!active) {
            return;
        }
        checked = !checked;
        if (onToggle != null) {
            onToggle.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!active || !visible || !isFocused() || (event.key() != 32 && event.key() != 257 && event.key() != 335)) return false;
        onClick(null, false);
        return true;
    }
}
