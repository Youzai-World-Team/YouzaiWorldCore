package top.csituka.youzaiworldcore.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 简单的空白测试页面，仅显示标题文字。
 */
public class TestScreen extends Screen {

    public TestScreen(Component title) {
        super(title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        // 居中显示标题
        graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2, 0xFFFFFF);
    }
}
