package top.csituka.youzaiworldcore.client.screen.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.client.render.RoundedRect;

@SuppressWarnings("null")
public class ConfirmationDialog {

    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 120;
    private static final int CORNER_RADIUS = 6;
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 10;

    private final String title;
    private final String[] messages;
    private final Runnable onConfirm;
    private final Runnable onCancel;
    private final String confirmButtonText;
    private final String cancelButtonText;
    private final boolean singleButtonMode;

    private int dialogX;
    private int dialogY;
    private TransparentButton confirmButton;
    private TransparentButton cancelButton;

    private boolean visible = false;
    private boolean fadingOut = false;
    private float alpha = 0f;
    private long showTime = -1;
    private long hideTime = -1;
    private static final long FADE_DURATION_MS = 200;

    /**
     * 双按钮对话框构造函数（保留原有的兼容构造函数）
     */
    public ConfirmationDialog(String title, String[] messages, Runnable onConfirm, Runnable onCancel) {
        this.title = title;
        this.messages = messages;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        this.confirmButtonText = I18n.get("youzaiworldcore.message.gui.confirm_yes");
        this.cancelButtonText = I18n.get("youzaiworldcore.message.gui.confirm_cancel");
        this.singleButtonMode = false;
    }

    /**
     * 单按钮对话框构造函数
     * @param title 标题
     * @param messages 消息数组
     * @param buttonText 按钮文本
     * @param onClick 点击回调
     */
    public ConfirmationDialog(String title, String[] messages, String buttonText, Runnable onClick) {
        this.title = title;
        this.messages = messages;
        this.onConfirm = onClick;
        this.onCancel = null;
        this.confirmButtonText = buttonText;
        this.cancelButtonText = null;
        this.singleButtonMode = true;
    }

    public void show() {
        this.visible = true;
        this.fadingOut = false;
        this.showTime = System.currentTimeMillis();
        this.hideTime = -1;
        this.alpha = 0f;
    }

    public void hide() {
        this.fadingOut = true;
        this.hideTime = System.currentTimeMillis();
    }

    public boolean isVisible() {
        return visible || fadingOut;
    }

    public boolean isFullyVisible() {
        return visible && !fadingOut;
    }

    public void init(int screenWidth, int screenHeight) {
        dialogX = (screenWidth - DIALOG_WIDTH) / 2;
        dialogY = (screenHeight - DIALOG_HEIGHT) / 2;

        int buttonY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - 15;

        if (singleButtonMode) {
            // 单按钮模式：按钮居中
            int buttonStartX = dialogX + (DIALOG_WIDTH - BUTTON_WIDTH) / 2;

            confirmButton = new TransparentButton(
                    buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal(confirmButtonText),
                    () -> {
                        hide();
                        if (onConfirm != null) onConfirm.run();
                    }
            );
            confirmButton.setTextColor(0x000000);
            cancelButton = null;
        } else {
            // 双按钮模式
            int totalButtonWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
            int buttonStartX = dialogX + (DIALOG_WIDTH - totalButtonWidth) / 2;

            confirmButton = new TransparentButton(
                    buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal(confirmButtonText),
                    () -> {
                        hide();
                        if (onConfirm != null) onConfirm.run();
                    }
            );
            confirmButton.setTextColor(0x000000);

            cancelButton = new TransparentButton(
                    buttonStartX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal(cancelButtonText),
                    () -> {
                        hide();
                        if (onCancel != null) onCancel.run();
                    }
            );
            cancelButton.setTextColor(0x000000);
        }
    }

    public void render(GuiGraphicsExtractor guiGraphics, int screenWidth, int screenHeight) {
        if (!visible && !fadingOut) return;

        if (fadingOut && hideTime != -1) {
            long elapsed = System.currentTimeMillis() - hideTime;
            alpha = Math.max(0f, 1f - elapsed / (float) FADE_DURATION_MS);
            if (alpha <= 0f) {
                fadingOut = false;
                visible = false;
                return;
            }
        } else if (showTime != -1) {
            long elapsed = System.currentTimeMillis() - showTime;
            alpha = Math.min(1f, elapsed / (float) FADE_DURATION_MS);
        }

        float bgFinalAlpha = 0.75f * alpha;
        int bgColor = colorWithAlpha(0xFFFFFF, bgFinalAlpha);
        fillRoundedRect(guiGraphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, CORNER_RADIUS, bgColor);

        var font = Minecraft.getInstance().font;
        int titleColor = colorWithAlpha(0x000000, alpha);
        int messageColor = colorWithAlpha(0x000000, alpha);

        float titleScale = 1.2f;
        int titleWidth = (int) (font.width(title) * titleScale);
        int titleX = dialogX + (DIALOG_WIDTH - titleWidth) / 2;
        int titleY = dialogY + 15;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        guiGraphics.text(font, title, (int) (titleX / titleScale), (int) (titleY / titleScale), titleColor, false);
        guiGraphics.pose().popMatrix();

        int messageY = dialogY + 38;
        for (String message : messages) {
            int msgWidth = font.width(message);
            guiGraphics.text(font, message, dialogX + (DIALOG_WIDTH - msgWidth) / 2, messageY, messageColor, false);
            messageY += font.lineHeight + 4;
        }

        if (confirmButton != null) {
            confirmButton.setExternalAlpha(alpha);
        }
        if (cancelButton != null) {
            cancelButton.setExternalAlpha(alpha);
        }
    }

    public void renderButtons(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!visible && !fadingOut) return;
        if (confirmButton != null) {
            confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (cancelButton != null) {
            cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible || fadingOut) return false;
        if (confirmButton != null && isMouseOverButton(confirmButton, mouseX, mouseY)) {
            confirmButton.onClick(null, true);
            return true;
        }
        if (cancelButton != null && isMouseOverButton(cancelButton, mouseX, mouseY)) {
            cancelButton.onClick(null, true);
            return true;
        }
        return visible;
    }

    private boolean isMouseOverButton(TransparentButton button, double mouseX, double mouseY) {
        int x = button.getX();
        int y = button.getY();
        return mouseX >= x && mouseX < x + button.getWidth() && mouseY >= y && mouseY < y + button.getHeight();
    }

    // 圆角矩形绘制统一走 RoundedRect（行扫描），点亮像素与原逐像素实现一致。
    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        RoundedRect.fill(g, x, y, w, h, r, color);
    }

    private int colorWithAlpha(int color, float alpha) {
        int a = (int) (alpha * 255);
        return (a << 24) | (color & 0x00FFFFFF);
    }
}