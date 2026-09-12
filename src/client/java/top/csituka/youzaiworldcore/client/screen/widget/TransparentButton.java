package top.csituka.youzaiworldcore.client.screen.widget;

import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;

/** 共用 MD3 按钮，支持实时主题、键盘焦点、禁用状态和可选图标。 */
public class TransparentButton extends AbstractWidget {
    private final Runnable onPress;
    private float externalAlpha = 1f;
    private boolean backgroundVisible = true;
    private boolean textLeftAligned;
    private int textInsetLeft = -1;
    private int textInsetRight = -1;
    private IntSupplier textColor;
    private Identifier icon;
    private final YzuiHover hoverState = new YzuiHover();
    private YzuiTheme.ButtonStyle style = YzuiTheme.ButtonStyle.TONAL;

    public TransparentButton(int x, int y, int width, int height, Component message, Runnable onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public void setExternalAlpha(float alpha) { externalAlpha = Math.clamp(alpha, 0f, 1f); }
    protected float externalAlpha() { return externalAlpha; }
    public void setBackgroundVisible(boolean visible) { backgroundVisible = visible; }
    public void setTextLeftAligned(boolean aligned) { textLeftAligned = aligned; }
    /** 为行内状态、距离等附加信息留出独立空间。 */
    public void setTextInsets(int left, int right) {
        textInsetLeft = Math.max(0, left);
        textInsetRight = Math.max(0, right);
    }
    public void setTextColor(int color) { textColor = () -> YzuiTheme.legacyText(color); }
    public void setTextColor(IntSupplier color) { textColor = color; }
    public TransparentButton setStyle(YzuiTheme.ButtonStyle value) { style = value; textColor = null; return this; }
    public void setIcon(Identifier value) { icon = value; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        isHovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float hover = hoverState.sample(active && isHovered);
        float opacity = externalAlpha * getAlpha();
        YzuiTheme.ButtonStyle actualStyle = backgroundVisible ? style : YzuiTheme.ButtonStyle.TEXT;
        YzuiTheme.button(g, x, y, w, h, hover, isFocused(), active, opacity, actualStyle);

        int foreground = textColor == null ? YzuiTheme.buttonText(actualStyle, active) : textColor.getAsInt();
        if (!active) foreground = YzuiTheme.textMuted();
        foreground = YzuiTheme.alpha(foreground, opacity * (active ? 1f : 0.68f));
        int padding = w < 30 ? 2 : 8;
        int leftInset = textInsetLeft < 0 ? padding : textInsetLeft;
        int rightInset = textInsetRight < 0 ? padding : textInsetRight;
        int textX = x + leftInset;
        int available = Math.max(0, w - leftInset - rightInset);
        if (icon != null) {
            int size = Math.max(1, Math.min(24, Math.min(h - 8, w - 8)));
            boolean iconOnly = w < 76;
            int iconX = iconOnly ? x + (w - size) / 2 : x + 8;
            int iconY = y + (h - size) / 2;
            g.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0, 0,
                    size, size, size, size, YzuiTheme.alpha(0xFFFFFFFF, opacity));
            if (iconOnly) return;
            textX += size + 6;
            available -= size + 6;
        }
        var font = Minecraft.getInstance().font;
        YzuiTheme.label(g, font, getMessage(), textX, y + (h - font.lineHeight) / 2,
                available, foreground, icon == null && !textLeftAligned);
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractWidgetRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (active && visible && onPress != null) onPress.run();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!active || !visible || !isFocused()) return false;
        if (event.key() != 257 && event.key() != 335 && event.key() != 32) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        if (onPress != null) onPress.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
