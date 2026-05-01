package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

public class TransparentButton extends AbstractWidget {

    private static final int BACKGROUND_COLOR = 0x80FFFFFF;
    private static final int HOVER_COLOR = 0xB0FFFFFF;
    private static final int TEXT_COLOR = 0xFF000000;

    private final Runnable onPress;

    public TransparentButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int color = this.isHovered() ? HOVER_COLOR : BACKGROUND_COLOR;
        
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, color);

        int borderColor = 0x40FFFFFF;
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + 1, borderColor);
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + 1, this.getY() + this.height, borderColor);
        guiGraphics.fill(this.getX() + this.width - 1, this.getY(), this.getX() + this.width, this.getY() + this.height, borderColor);
        guiGraphics.fill(this.getX(), this.getY() + this.height - 1, this.getX() + this.width, this.getY() + this.height, borderColor);

        var font = Minecraft.getInstance().font;
        String text = this.getMessage().getString();
        int textWidth = font.width(text);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;
        
        guiGraphics.text(font, this.getMessage(), textX, textY, TEXT_COLOR, false);
    }

    public void onClick(double mouseX, double mouseY) {
        if (this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
