package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 账户注册 GUI
 * 在玩家传送到虚空维度且未注册时显示。
 * 无返回按钮、无关闭按钮、不能使用 ESC 键关闭。
 */
public class RegisterScreen extends Screen {

    private static final int CONTAINER_WIDTH = 280;
    private static final int CONTAINER_HEIGHT = 250;
    private static final int LABEL_WIDTH = 50;
    private static final int FIELD_WIDTH = 180;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_SPACING = 28;

    private final String playerName;

    private EditBox usernameField;
    private EditBox passwordField;
    private EditBox confirmPasswordField;
    private TransparentButton registerButton;
    private TransparentButton disconnectButton;

    private final List<AbstractWidget> allWidgets = new ArrayList<>();

    private ConfirmationDialog currentDialog;
    private boolean processing = false;

    public RegisterScreen(String playerName) {
        super(Component.translatable("screen.youzaiworldcore.register.title"));
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int containerTop = (this.height - CONTAINER_HEIGHT) / 2;
        int leftColX = centerX - CONTAINER_WIDTH / 2 + 10;
        int fieldX = leftColX + LABEL_WIDTH + 6;

        // 用户名输入框（只读，预填玩家名）
        this.usernameField = new EditBox(this.font, fieldX, containerTop + 55, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.label_username"));
        this.usernameField.setValue(this.playerName);
        this.usernameField.setEditable(false);
        this.usernameField.setCanLoseFocus(false);
        this.usernameField.setTextColor(0xAAAAAA);

        // 密码输入框
        this.passwordField = new EditBox(this.font, fieldX, containerTop + 55 + ROW_SPACING, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.label_password"));
        this.passwordField.setMaxLength(128);
        this.passwordField.setHint(Component.translatable("screen.youzaiworldcore.register.hint_password"));

        // 确认密码输入框
        this.confirmPasswordField = new EditBox(this.font, fieldX, containerTop + 55 + ROW_SPACING * 2, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.label_confirm_password"));
        this.confirmPasswordField.setMaxLength(128);
        this.confirmPasswordField.setHint(Component.translatable("screen.youzaiworldcore.register.hint_confirm"));

        // 注册按钮
        int buttonY = containerTop + 55 + ROW_SPACING * 3 + 30;
        int totalButtonWidth = BUTTON_WIDTH * 2 + 12;
        int buttonStartX = centerX - totalButtonWidth / 2;

        this.registerButton = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.button_register"),
                this::onRegisterClick
        );
        this.registerButton.setTextColor(0xFFFFFF);

        // 断开连接按钮
        this.disconnectButton = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + 12, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.button_disconnect"),
                this::onDisconnectClick
        );
        this.disconnectButton.setTextColor(0xFFFFFF);

        // 收集所有 widget 用于手动渲染和事件分发
        this.allWidgets.clear();
        this.allWidgets.add(this.usernameField);
        this.allWidgets.add(this.passwordField);
        this.allWidgets.add(this.confirmPasswordField);
        this.allWidgets.add(this.registerButton);
        this.allWidgets.add(this.disconnectButton);

        // 默认聚焦到密码框
        this.passwordField.setFocused(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 绘制全屏半透明背景
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        int centerX = this.width / 2;
        int containerTop = (this.height - CONTAINER_HEIGHT) / 2;
        int leftColX = centerX - CONTAINER_WIDTH / 2 + 10;

        // 绘制标题
        String titleText = Component.translatable("screen.youzaiworldcore.register.title").getString();
        float titleScale = 1.3f;
        int titleWidth = (int) (this.font.width(titleText) * titleScale);
        int titleX = (centerX - titleWidth / 2);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        guiGraphics.text(this.font, titleText,
                (int) (titleX / titleScale),
                (int) ((containerTop + 10) / titleScale),
                0xFFFFFFFF, false);
        guiGraphics.pose().popMatrix();

        // 绘制副标题
        String subtitleText = Component.translatable("screen.youzaiworldcore.register.subtitle").getString();
        int subtitleWidth = this.font.width(subtitleText);
        guiGraphics.text(this.font, subtitleText,
                centerX - subtitleWidth / 2,
                containerTop + 35,
                0xFFCCCCCC, false);

        // 绘制标签
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.register.label_username").getString(),
                leftColX, containerTop + 58);
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.register.label_password").getString(),
                leftColX, containerTop + 58 + ROW_SPACING);
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.register.label_confirm_password").getString(),
                leftColX, containerTop + 58 + ROW_SPACING * 2);

        // 绘制提示文本
        String hint1 = Component.translatable("screen.youzaiworldcore.register.hint_line1").getString();
        String hint2 = Component.translatable("screen.youzaiworldcore.register.hint_line2").getString();
        int hintColor = 0xFFAAAAAA;
        int hintY = containerTop + 55 + ROW_SPACING * 3;
        guiGraphics.text(this.font, hint1,
                centerX - this.font.width(hint1) / 2,
                hintY, hintColor, false);
        guiGraphics.text(this.font, hint2,
                centerX - this.font.width(hint2) / 2,
                hintY + this.font.lineHeight + 2,
                hintColor, false);

        // 手动渲染所有 widget（EditBox / TransparentButton）
        // 需要注意 EditBox 的 extractWidgetRenderState 需要正确的鼠标坐标来判断悬停
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox) {
                editBox.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
            } else if (widget instanceof TransparentButton button) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        // 渲染弹窗（在 widget 之上）
        if (currentDialog != null && currentDialog.isVisible()) {
            currentDialog.render(guiGraphics, this.width, this.height);
            currentDialog.renderButtons(guiGraphics, mouseX, mouseY, partialTick);
        } else if (currentDialog != null && !currentDialog.isVisible()) {
            currentDialog = null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        // 弹窗优先处理点击
        if (currentDialog != null && currentDialog.isFullyVisible()) {
            return currentDialog.mouseClicked(event.x(), event.y());
        }

        double mx = event.x();
        double my = event.y();

        // 转发点击到 EditBox（需手动管理焦点）
        if (this.passwordField.mouseClicked(event, isActuallyClick)) {
            this.passwordField.setFocused(true);
            this.confirmPasswordField.setFocused(false);
            this.usernameField.setFocused(false);
            return true;
        }
        if (this.confirmPasswordField.mouseClicked(event, isActuallyClick)) {
            this.confirmPasswordField.setFocused(true);
            this.passwordField.setFocused(false);
            this.usernameField.setFocused(false);
            return true;
        }
        if (this.usernameField.mouseClicked(event, isActuallyClick)) {
            this.usernameField.setFocused(true);
            this.passwordField.setFocused(false);
            this.confirmPasswordField.setFocused(false);
            return true;
        }

        // 转发点击到按钮
        if (isMouseOverButton(this.registerButton, mx, my)) {
            this.registerButton.onClick(event, isActuallyClick);
            return true;
        }
        if (isMouseOverButton(this.disconnectButton, mx, my)) {
            this.disconnectButton.onClick(event, isActuallyClick);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (currentDialog != null && currentDialog.isFullyVisible()) {
            return true;
        }

        // 拦截 ESC 键
        if (keyEvent.key() == 256) { // GLFW_KEY_ESCAPE
            return true;
        }

        // 转发键盘事件到当前聚焦的 EditBox
        if (this.passwordField.isFocused() && this.passwordField.keyPressed(keyEvent)) return true;
        if (this.confirmPasswordField.isFocused() && this.confirmPasswordField.keyPressed(keyEvent)) return true;
        if (this.usernameField.isFocused() && this.usernameField.keyPressed(keyEvent)) return true;

        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent charEvent) {
        // 转发字符输入到当前聚焦的 EditBox
        if (this.passwordField.isFocused() && this.passwordField.charTyped(charEvent)) return true;
        if (this.confirmPasswordField.isFocused() && this.confirmPasswordField.charTyped(charEvent)) return true;
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        this.processing = false;
        this.currentDialog = null;
    }

    // ===== 按钮回调 =====

    private void onRegisterClick() {
        if (processing) return;

        String password = this.passwordField.getValue();
        String confirm = this.confirmPasswordField.getValue();

        // 客户端本地校验
        if (password.isEmpty() || confirm.isEmpty()) {
            showErrorDialog(
                    Component.translatable("screen.youzaiworldcore.register.error_title").getString(),
                    new String[]{Component.translatable("screen.youzaiworldcore.register.error_empty").getString()}
            );
            return;
        }
        if (!password.equals(confirm)) {
            showErrorDialog(
                    Component.translatable("screen.youzaiworldcore.register.error_title").getString(),
                    new String[]{Component.translatable("screen.youzaiworldcore.register.error_mismatch").getString()}
            );
            return;
        }
        if (password.length() < 4) {
            showErrorDialog(
                    Component.translatable("screen.youzaiworldcore.register.error_title").getString(),
                    new String[]{Component.translatable("screen.youzaiworldcore.register.error_too_short").getString()}
            );
            return;
        }

        // 发送注册命令
        processing = true;
        String command = "yzwc account register " + password + " " + confirm;
        sendCommand(command);
    }

    private void onDisconnectClick() {
        if (processing) return;

        // 断开连接
        Minecraft.getInstance().disconnectFromWorld(
                Component.translatable("screen.youzaiworldcore.register.disconnect_message"));
    }

    // ===== 工具方法 =====

    private void sendCommand(String command) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.connection != null) {
            Minecraft.getInstance().player.connection.send(
                    new ServerboundChatCommandPacket(command));
        }
        // 命令已发送，关闭当前 GUI，等待服务器处理
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private void showErrorDialog(String title, String[] messages) {
        this.currentDialog = new ConfirmationDialog(
                title,
                messages,
                Component.translatable("screen.youzaiworldcore.register.dialog_ok").getString(),
                () -> {
                    this.passwordField.setFocused(true);
                    this.processing = false;
                }
        );
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void drawLabel(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y) {
        int labelY = y + (FIELD_HEIGHT - font.lineHeight) / 2;
        guiGraphics.text(font, text, x, labelY, 0xFFFFFFFF, false);
    }

    private boolean isMouseOverButton(TransparentButton button, double mx, double my) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }
}
