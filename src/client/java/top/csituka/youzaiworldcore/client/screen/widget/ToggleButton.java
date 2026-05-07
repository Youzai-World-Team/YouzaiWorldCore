package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ToggleButton extends AbstractWidget {

    private static final int CORNER_RADIUS = 4;
    private static final float LERP_SPEED = 0.15f;

    private boolean toggled = false;
    private final Runnable onToggle;
    private float currentAlpha = 0.5f;
    private float targetAlpha = 0.5f;

    public ToggleButton(int x, int y, int size, Runnable onToggle) {
        super(x, y, size, size, Component.empty());
        this.onToggle = onToggle;
    }

    public void setToggled(boolean toggled) {
        this.toggled = toggled;
    }

    public boolean isToggled() {
        return this.toggled;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        targetAlpha = this.isHovered() ? 0.69f : 0.5f;
        currentAlpha = lerp(currentAlpha, targetAlpha, LERP_SPEED);

        int x = this.getX();
        int y = this.getY();
        int size = this.width;

        int bgColor = toggled ? colorWithAlpha(0x4CAF50, currentAlpha) : colorWithAlpha(0xF44336, currentAlpha);
        int borderColor = toggled ? colorWithAlpha(0x81C784, 0.7f) : colorWithAlpha(0xEF9A9A, 0.7f);

        fillRoundedRect(guiGraphics, x, y, size, size, CORNER_RADIUS, bgColor);
        drawRoundedBorder(guiGraphics, x, y, size, size, CORNER_RADIUS, borderColor);

        if (toggled) {
            drawCheckMark(guiGraphics, x, y, size);
        } else {
            drawCrossMark(guiGraphics, x, y, size);
        }
    }

    private void drawCheckMark(GuiGraphicsExtractor guiGraphics, int bx, int by, int size) {
        int color = 0xFFFFFFFF;
        int cx = bx + 3;
        int cy = by + size / 2;
        for (int i = 0; i < 2; i++) {
            guiGraphics.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
        }
        for (int i = 0; i < 4; i++) {
            guiGraphics.fill(cx + 2 + i, cy + 2 - i, cx + 3 + i, cy + 3 - i, color);
        }
    }

    private void drawCrossMark(GuiGraphicsExtractor guiGraphics, int bx, int by, int size) {
        int color = 0xFFFFFFFF;
        int margin = 3;
        int x1 = bx + margin;
        int y1 = by + margin;
        int x2 = bx + size - margin - 1;
        int y2 = by + size - margin - 1;
        for (int i = 0; i <= x2 - x1; i++) {
            guiGraphics.fill(x1 + i, y1 + i, x1 + i + 1, y1 + i + 1, color);
            guiGraphics.fill(x2 - i, y1 + i, x2 - i + 1, y1 + i + 1, color);
        }
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                if (i * i + j * j < r * r) {
                    g.fill(x + r - i - 1, y + r - j - 1, x + r - i, y + r - j, color);
                    g.fill(x + w - r + i, y + r - j - 1, x + w - r + i + 1, y + r - j, color);
                    g.fill(x + r - i - 1, y + h - r + j, x + r - i, y + h - r + j + 1, color);
                    g.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
                }
            }
        }
    }

    private void drawRoundedBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        for (int i = 0; i < w; i++) {
            boolean inLeftCorner = i < r;
            boolean inRightCorner = i >= w - r;
            boolean skipCorner = false;

            if (inLeftCorner) {
                int dx = r - i - 1;
                if (dx * dx + (r - 1) * (r - 1) >= r * r) skipCorner = true;
            }
            if (inRightCorner) {
                int dx = i - (w - r);
                if (dx * dx + (r - 1) * (r - 1) >= r * r) skipCorner = true;
            }

            if (!skipCorner) {
                g.fill(x + i, y, x + i + 1, y + 1, color);
                g.fill(x + i, y + h - 1, x + i + 1, y + h, color);
            }
        }

        for (int j = 0; j < h; j++) {
            boolean inTopCorner = j < r;
            boolean inBottomCorner = j >= h - r;
            boolean skipCorner = false;

            if (inTopCorner) {
                int dy = r - j - 1;
                if ((r - 1) * (r - 1) + dy * dy >= r * r) skipCorner = true;
            }
            if (inBottomCorner) {
                int dy = j - (h - r);
                if ((r - 1) * (r - 1) + dy * dy >= r * r) skipCorner = true;
            }

            if (!skipCorner) {
                g.fill(x, y + j, x + 1, y + j + 1, color);
                g.fill(x + w - 1, y + j, x + w, y + j + 1, color);
            }
        }
    }

    private int colorWithAlpha(int color, float alpha) {
        int a = (int) (alpha * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private float lerp(float current, float target, float speed) {
        if (Math.abs(current - target) < 0.001f) {
            return target;
        }
        return current + (target - current) * speed;
    }

    public void render(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (this.onToggle != null) {
            this.onToggle.run();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }
}
