package top.csituka.youzaiworldcore.client.screen.widget;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import top.csituka.youzaiworldcore.client.animation.YzuiPopupAnimation;
import top.csituka.youzaiworldcore.client.render.YzuiTheme;

/** 自适应模态卡片：背景保留父页面，绘制和命中共用缩放/位移动画。 */
public class ConfirmationDialog {
    private final String title;
    private final String[] messages;
    private final Runnable onConfirm, onCancel;
    private final String confirmButtonText, cancelButtonText;
    private final boolean singleButtonMode;
    private int dialogX, dialogY, dialogWidth, dialogHeight, messageHeight, scrollLine;
    private int canvasWidth, canvasHeight;
    private List<FormattedCharSequence> lines = List.of();
    private TransparentButton confirmButton, cancelButton;
    private final YzuiPopupAnimation animation = new YzuiPopupAnimation();

    public ConfirmationDialog(String title, String[] messages, Runnable onConfirm, Runnable onCancel) {
        this.title = title;
        this.messages = messages;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        confirmButtonText = I18n.get("youzaiworldcore.message.gui.confirm_yes");
        cancelButtonText = I18n.get("youzaiworldcore.message.gui.confirm_cancel");
        singleButtonMode = false;
    }

    public ConfirmationDialog(String title, String[] messages, String buttonText, Runnable onClick) {
        this.title = title;
        this.messages = messages;
        this.onConfirm = onClick;
        this.onCancel = null;
        confirmButtonText = buttonText;
        cancelButtonText = null;
        singleButtonMode = true;
    }

    public void init(int screenWidth, int screenHeight) {
        canvasWidth = screenWidth;
        canvasHeight = screenHeight;
        var font = Minecraft.getInstance().font;
        dialogWidth = Math.max(160, Math.min(440, screenWidth - 40));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String message : messages) {
            wrapped.addAll(font.split(Component.literal(message == null ? "" : message), dialogWidth - 48));
        }
        lines = List.copyOf(wrapped);
        dialogHeight = Math.min(screenHeight - 32, Math.max(168, lines.size() * (font.lineHeight + 3) + 102));
        messageHeight = Math.max(font.lineHeight + 3, dialogHeight - 102);
        scrollLine = Math.clamp(scrollLine, 0, maxScrollLine());
        dialogX = (screenWidth - dialogWidth) / 2;
        dialogY = (screenHeight - dialogHeight) / 2;
        int buttonWidth = Math.min(156, (dialogWidth - 56) / 2);
        int buttonY = dialogY + dialogHeight - 48;
        int firstX = singleButtonMode ? dialogX + (dialogWidth - buttonWidth) / 2
                : dialogX + dialogWidth - 24 - buttonWidth * 2 - 8;
        confirmButton = new TransparentButton(firstX, buttonY, buttonWidth, 28, Component.literal(confirmButtonText),
                () -> { hide(); if (onConfirm != null) onConfirm.run(); });
        confirmButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
        if (singleButtonMode) {
            cancelButton = null;
            confirmButton.setFocused(true);
        } else {
            cancelButton = new TransparentButton(firstX + buttonWidth + 8, buttonY, buttonWidth, 28,
                    Component.literal(cancelButtonText), () -> { hide(); if (onCancel != null) onCancel.run(); });
            cancelButton.setFocused(true);
        }
    }

    public void show() {
        scrollLine = 0;
        animation.show(canvasWidth, canvasHeight);
    }

    public void hide() { animation.hide(); }
    public boolean isVisible() { return animation.isVisible(); }
    public boolean isFullyVisible() { return animation.acceptsInput(); }

    public void render(GuiGraphicsExtractor g, int screenWidth, int screenHeight) {
        if (!animation.update(screenWidth, screenHeight)) return;
        animation.scrim(g);
        var previous = animation.begin(g);
        try {
            YzuiTheme.card(g, dialogX, dialogY, dialogWidth, dialogHeight);
            var font = Minecraft.getInstance().font;
            YzuiTheme.label(g, font, Component.literal(title), dialogX + 24, dialogY + 22,
                    dialogWidth - 48, YzuiTheme.text(), false);
            int top = dialogY + 48, lineHeight = font.lineHeight + 3;
            g.enableScissor(dialogX + 24, top, dialogX + dialogWidth - 24, top + messageHeight);
            int count = Math.min(lines.size(), scrollLine + Math.max(1, messageHeight / lineHeight));
            for (int row = scrollLine; row < count; row++) {
                g.text(font, lines.get(row), dialogX + 24, top + (row - scrollLine) * lineHeight,
                        YzuiTheme.textMuted(), false);
            }
            g.disableScissor();
            if (maxScrollLine() > 0) {
                int barX = dialogX + dialogWidth - 15;
                g.fill(barX, top, barX + 2, top + messageHeight, YzuiTheme.outlineVariant());
                int thumb = Math.max(12, messageHeight * Math.max(1, messageHeight / lineHeight) / lines.size());
                int thumbY = top + (messageHeight - thumb) * scrollLine / maxScrollLine();
                g.fill(barX, thumbY, barX + 2, thumbY + thumb, YzuiTheme.primary());
            }
        } finally {
            animation.end(g, previous);
        }
    }

    public void renderButtons(GuiGraphicsExtractor g, int mouseX, int mouseY, float tick) {
        if (!isVisible()) return;
        int x = isFullyVisible() ? (int) Math.floor(animation.toLocalX(mouseX)) : -10000;
        int y = isFullyVisible() ? (int) Math.floor(animation.toLocalY(mouseY)) : -10000;
        var previous = animation.begin(g);
        try {
            confirmButton.render(g, x, y, tick);
            if (cancelButton != null) cancelButton.render(g, x, y, tick);
        } finally {
            animation.end(g, previous);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!isFullyVisible()) return isVisible();
        double x = animation.toLocalX(mouseX), y = animation.toLocalY(mouseY);
        if (confirmButton.isMouseOver(x, y)) confirmButton.onClick(null, true);
        else if (cancelButton != null && cancelButton.isMouseOver(x, y)) cancelButton.onClick(null, true);
        return true;
    }

    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (!isFullyVisible()) return isVisible();
        x = animation.toLocalX(x);
        y = animation.toLocalY(y);
        if (x >= dialogX && x < dialogX + dialogWidth && y >= dialogY && y < dialogY + dialogHeight) {
            scrollLine = Math.clamp(scrollLine - (int) Math.signum(vertical), 0, maxScrollLine());
        }
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!isFullyVisible()) return isVisible();
        if (event.key() == 256 && cancelButton != null) cancelButton.onClick(null, false);
        else if (event.key() == 264 || event.key() == 265) {
            scrollLine = Math.clamp(scrollLine + (event.key() == 264 ? 1 : -1), 0, maxScrollLine());
        } else WidgetFocus.keyPressed(event, singleButtonMode ? List.of(confirmButton) : List.of(confirmButton, cancelButton));
        return true;
    }

    private int maxScrollLine() {
        int lineHeight = Minecraft.getInstance().font.lineHeight + 3;
        return Math.max(0, lines.size() - Math.max(1, messageHeight / lineHeight));
    }
}
