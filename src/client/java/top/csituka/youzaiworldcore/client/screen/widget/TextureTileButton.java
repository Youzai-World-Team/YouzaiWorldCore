package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * A textured button with rounded corners and fade animation support.
 *
 * Fade technique: Draw a black overlay with varying alpha on top of the texture.
 * Since the menu background is semi-transparent black, this creates a smooth
 * fade to transparent without white residue artifacts.
 */
public class TextureTileButton extends AbstractWidget {

    private static final int CORNER_RADIUS = 6;

    private final Identifier texture;
    private final Runnable onPress;
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
        float vis = Math.min(1f, Math.max(0f, externalAlpha * getAlpha()));
        if (vis < 0.001f) return;

        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        int r = CORNER_RADIUS;

        // Draw textured background (always when any visibility)
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, 0, 0,
                w, h, w, h);

        // Fade overlay using accelerated quadratic curve:
        // Background alpha = vis * 128; overlay represents darkness needed to obscure the texture.
        // Using a quadratic acceleration (fadeFactor = (1-vis)^2) prevents the button area from
        // accumulating more blackness than the surrounding background during exit animation.
        // At vis=0.5: fadeFactor=0.25, overlay=32 vs bg=64 — button doesn't appear darker than bg
        // At vis=0.2: fadeFactor=0.64, overlay=82 (texture faint + 82 overlay ≈ blends with bg=25)
        // At vis=0:   fadeFactor=1.0,  overlay=128 (fully matches closed bg)
        float fadeFactor = (1f - vis);
        int overlayAlpha = (int) (fadeFactor * 128);
        if (overlayAlpha > 0) {
            guiGraphics.fill(x, y, x + w, y + h, (overlayAlpha << 24));
        }

        // Clip corners
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy >= r * r) {
                    guiGraphics.fill(x + i, y + j, x + i + 1, y + j + 1, 0x73000000);
                    guiGraphics.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, 0x73000000);
                    guiGraphics.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, 0x73000000);
                    guiGraphics.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, 0x73000000);
                }
            }
        }

        // Hover highlight
        if (this.isHovered()) {
            int hlA = (int) (0.15f * vis * 255);
            int hlC = (hlA << 24) | 0xFFFFFF;
            for (int row = 0; row < h; row++) {
                for (int col = 0; col < w; col++) {
                    // Check if pixel is inside the rounded rect
                    boolean inside = true;
                    if (col < r && row < r) {
                        int dx = r - 1 - col;
                        int dy = r - 1 - row;
                        inside = dx * dx + dy * dy < r * r;
                    } else if (col < r && row >= h - r) {
                        int dx = r - 1 - col;
                        int dy = h - 1 - row;
                        inside = dx * dx + dy * dy < r * r;
                    } else if (col >= w - r && row < r) {
                        int dx = w - 1 - col;
                        int dy = r - 1 - row;
                        inside = dx * dx + dy * dy < r * r;
                    } else if (col >= w - r && row >= h - r) {
                        int dx = w - 1 - col;
                        int dy = h - 1 - row;
                        inside = dx * dx + dy * dy < r * r;
                    }
                    if (inside) {
                        guiGraphics.fill(x + col, y + row, x + col + 1, y + row + 1, hlC);
                    }
                }
            }
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
}