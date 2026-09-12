package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.RoundedRect;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.animation.YzuiHover;

/** 设置行开关：清晰区分启用、禁用、键盘焦点与鼠标悬停。 */
public class CheckboxButton extends AbstractWidget {
    private boolean checked;
    private final YzuiHover hoverState = new YzuiHover();
    private final Runnable onToggle;
    private float externalAlpha = 1f;
    private boolean wrapMessage;

    public CheckboxButton(int x, int y, int width, int height, Component message, boolean checked, Runnable onToggle) {
        super(x, y, width, height, message);
        this.checked = checked;
        this.onToggle = onToggle;
    }

    public boolean isChecked() { return checked; }
    public void setExternalAlpha(float alpha) { externalAlpha = Math.clamp(alpha, 0f, 1f); }
    public CheckboxButton setWrapMessage(boolean value) { wrapMessage = value; return this; }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (!visible) return;
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        float opacity = externalAlpha * getAlpha() * (active ? 1f : 0.6f);
        YzuiTheme.field(g, x, y, w, h, hoverState.sample(active && hovered), isFocused(), active, opacity);
        int trackW = Math.min(28, w / 3), trackH = Math.min(14, h - 4);
        int trackX = x + w - trackW - 6, trackY = y + (h - trackH) / 2;
        int track = checked ? YzuiTheme.primary() : YzuiTheme.outlineVariant();
        RoundedRect.fill(g, trackX, trackY, trackW, trackH, trackH / 2, YzuiTheme.alpha(track, opacity));
        int thumb = Math.max(2, trackH - 4);
        int thumbX = checked ? trackX + trackW - thumb - 2 : trackX + 2;
        RoundedRect.fill(g, thumbX, trackY + 2, thumb, thumb, thumb / 2,
                YzuiTheme.alpha(checked ? YzuiTheme.onPrimary() : YzuiTheme.textMuted(), opacity));

        var font = Minecraft.getInstance().font;
        int available = Math.max(1, trackX - x - 14);
        int textColor = YzuiTheme.alpha(YzuiTheme.text(), opacity);
        if (wrapMessage) {
            var lines = font.split(getMessage(), available);
            int lineHeight = font.lineHeight + 2;
            int textY = y + Math.max(0, (h - lines.size() * lineHeight + 2) / 2);
            for (var line : lines) {
                if (textY + font.lineHeight > y + h) break;
                g.text(font, line, x + 6, textY, textColor, false);
                textY += lineHeight;
            }
        } else {
            YzuiTheme.label(g, font, getMessage(), x + 6, y + (h - font.lineHeight) / 2,
                    available, textColor, false);
        }
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        extractWidgetRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isActuallyClick) {
        if (!active || !visible) return;
        checked = !checked;
        if (onToggle != null) onToggle.run();
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (!active || !visible || !isFocused()) return false;
        if (event.key() != 257 && event.key() != 335 && event.key() != 32) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        checked = !checked;
        if (onToggle != null) onToggle.run();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
