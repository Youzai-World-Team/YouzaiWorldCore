package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 侧栏导航项，选中态使用填充胶囊；原版标题页保留原有按钮位置。 */
public class TitleScreenTextButton extends TransparentButton {
    private boolean selected;

    public TitleScreenTextButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message, onPress);
        setTextLeftAligned(true);
    }

    public void setRenderAlpha(float alpha) { setExternalAlpha(alpha); }
    public void setSelected(boolean value) { selected = value; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        setStyle(selected ? YzuiTheme.ButtonStyle.FILLED
                : YzuiTheme.isCustomScreen(Minecraft.getInstance().gui.screen())
                        ? YzuiTheme.ButtonStyle.TEXT : YzuiTheme.ButtonStyle.TONAL);
        super.extractWidgetRenderState(g, mouseX, mouseY, partialTick);
    }
}
