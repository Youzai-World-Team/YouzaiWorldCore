package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

public class TextureTileButton extends AbstractWidget {

    private static final float LERP_SPEED = 0.15f;

    private final Identifier texture;
    private final Runnable onPress;
    private float currentAlpha = 0.5f;
    private float targetAlpha = 0.5f;
    private float externalAlpha = 1f;

    public TextureTileButton(int x, int y, int width, int height, Identifier texture, Runnable onPress) {
        super(x, y, width, height, Component.empty());
        this.texture = texture;
        this.onPress = onPress;
    }

    public void setExternalAlpha(float alpha) {
        this.externalAlpha = alpha;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        targetAlpha = this.isHovered() ? 1.0f : 0.85f;
        currentAlpha = lerp(currentAlpha, targetAlpha, LERP_SPEED);

        float finalAlpha = currentAlpha * externalAlpha;
        int alpha = (int) (finalAlpha * 255);

        // Render the texture as the button background
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                this.getX(), this.getY(), 0, 0,
                this.width, this.height, this.width, this.height);

        // Hover overlay - slightly brighten on hover
        if (this.isHovered()) {
            int overlayColor = (int) (0.15f * finalAlpha * 255) << 24 | 0xFFFFFF;
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, overlayColor);
        }
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (this.onPress != null) {
            this.onPress.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    private float lerp(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.001f) {
            return target;
        }
        return current + (target - current) * speed;
    }
}