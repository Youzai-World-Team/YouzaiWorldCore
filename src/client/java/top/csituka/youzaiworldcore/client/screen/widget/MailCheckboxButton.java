package top.csituka.youzaiworldcore.client.screen.widget;

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
        int boxColor = active ? 0xFFE6E6E6 : 0xFF777777;
        int fillColor = checked && active ? 0xFFF2F2F2 : 0xFF555555;
        graphics.fill(getX(), getY() + 2, getX() + 10, getY() + 12, boxColor);
        graphics.fill(getX() + 1, getY() + 3, getX() + 9, getY() + 11, fillColor);
        if (checked) {
            graphics.text(Minecraft.getInstance().font, "✓", getX() + 1, getY() + 1,
                    active ? 0xFF222222 : 0xFF555555, false);
        }
        graphics.text(Minecraft.getInstance().font, getMessage(), getX() + 14, getY() + 3,
                active ? 0xFFFFFFFF : 0xFF888888, false);
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
}
