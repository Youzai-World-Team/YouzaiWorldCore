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
    private static final int CORNER_RADIUS = 8;

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
        int x = this.getX();
        int y = this.getY();
        int w = this.width;
        int h = this.height;
        int r = CORNER_RADIUS;

        // Step 1: Draw the textured background as a full rectangle
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                x, y, 0, 0,
                w, h, w, h);

        // Step 2: "Erase" the 4 extreme corner tips by drawing over them with the
        // menu's semi-transparent black background color. This clips the texture
        // to a rounded rectangle shape.
        // The quarter-circle with radius r sits at each corner. Pixels BEYOND the
        // circle (the extreme tips) are drawn over with the bg color.
        int cornerColor = colorWithAlpha(0x000000, 0.45f);
        clipCornerTips(guiGraphics, x, y, w, h, r, cornerColor);

        // Step 3: Hover highlight (brighten overlay) with rounded shape
        if (this.isHovered()) {
            int hlColor = colorWithAlpha(0xFFFFFF, 0.15f * finalAlpha);
            fillRoundedShape(guiGraphics, x, y, w, h, r, hlColor);
        }
    }

    /**
     * Clips the 4 corners to a rounded shape by drawing over the corner tip pixels
     * (those outside the quarter-circle) with the background color.
     * 
     * For the top-left corner: the quarter-circle center is at (x+r-1, y+r-1).
     * A pixel at (cx, cy) has distance d = sqrt(cx² + cy²) where cx,cy are offsets
     * from the center. Pixels with d >= r are the TIPS that get clipped.
     */
    private void clipCornerTips(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // We iterate over a grid of size r×r for each corner quadrant.
        // Pixel at index (i, j) within the quadrant has coordinates:
        //   TL: (x+i, y+j), TR: (x+w-1-i, y+j)
        //   BL: (x+i, y+h-1-j), BR: (x+w-1-i, y+h-1-j)
        // Distance from quarter-circle center (r-1, r-1): dx = r-1-i, dy = r-1-j
        // Pixel is OUTSIDE the circle (TIP) when dx² + dy² >= r²
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy >= r * r) {
                    // This pixel is outside the quarter-circle → it's a corner tip
                    // Top-left
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    // Top-right
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    // Bottom-left
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    // Bottom-right
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }

    /**
     * Fills the rounded-rectangle shape (interior + quarter-circle corners),
     * used for the hover highlight overlay.
     */
    private void fillRoundedShape(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }

    private int colorWithAlpha(int color, float alpha) {
        float clamped = Math.max(0f, Math.min(1f, alpha));
        return ((int) (clamped * 255)) << 24 | (color & 0x00FFFFFF);
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