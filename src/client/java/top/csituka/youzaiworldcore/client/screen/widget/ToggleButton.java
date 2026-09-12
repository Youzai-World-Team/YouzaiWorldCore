package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 紧凑的可选按钮，状态由原调用方同步，选择不再使用警告红表示。 */
public class ToggleButton extends TransparentButton {
    private boolean toggled;
    public ToggleButton(int x, int y, int size, Runnable onToggle) {
        super(x, y, size, size, Component.empty(), onToggle);
    }
    public void setToggled(boolean value) { toggled = value; }
    public boolean isToggled() { return toggled; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        setMessage(Component.literal(toggled ? "✓" : "−"));
        setStyle(toggled ? YzuiTheme.ButtonStyle.FILLED : YzuiTheme.ButtonStyle.TONAL);
        super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
    }
}
