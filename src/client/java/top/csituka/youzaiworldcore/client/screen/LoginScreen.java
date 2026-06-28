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
 * 账户登入 GUI
 * 在玩家传送到虚空维度且已注册但未登录时显示。
 * 无返回按钮、无关闭按钮、不能使用 ESC 键关闭。
 */
public class LoginScreen extends Screen {

    private static final int CONTAINER_WIDTH = 280;
    private static final int CONTAINER_HEIGHT = 200;
    private static final int LABEL_WIDTH = 50;
    private static final int FIELD_WIDTH = 180;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 90;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_SPACING = 28;

    private final String playerName;

    private EditBox usernameField;
    private EditBox passwordField;
    private TransparentButton loginButton;
    private TransparentButton disconnectButton;

    private final List<AbstractWidget> allWidgets = new ArrayList<>();

    private ConfirmationDialog currentDialog;
    private boolean processing = false;

    public LoginScreen(String playerName) {
        super(Component.translatable("screen.youzaiworldcore.login.title"));
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
                Component.translatable("screen.youzaiworldcore.login.label_username"));
        this.usernameField.setValue(this.playerName);
        this.usernameField.setEditable(false);
        this.usernameField.setCanLoseFocus(false);
        this.usernameField.setTextColor(0xAAAAAA);

        // 密码输入框
        this.passwordField = new EditBox(this.font, fieldX, containerTop + 55 + ROW_SPACING, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.login.label_password"));
        this.passwordField.setMaxLength(128);
        this.passwordField.setHint(Component.translatable("screen.youzaiworldcore.login.hint_password"));

        // 登入按钮
        int buttonY = containerTop + 55 + ROW_SPACING + 40;
        int totalButtonWidth = BUTTON_WIDTH * 2 + 12;
        int buttonStartX = centerX - totalButtonWidth / 2;

        this.loginButton = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.login.button_login"),
                this::onLoginClick
        );
        this.loginButton.setTextColor(0xFFFFFF);

        // 断开连接按钮
        this.disconnectButton = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + 12, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.login.button_disconnect"),
                this::onDisconnectClick
        );
        this.disconnectButton.setTextColor(0xFFFFFF);

        // 收集所有 widget
        this.allWidgets.clear();
        this.allWidgets.add(this.usernameField);
        this.allWidgets.add(this.passwordField);
        this.allWidgets.add(this.loginButton);
        this.allWidgets.add(this.disconnectButton);

        // 默认聚焦密码框
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
        String titleText = Component.translatable("screen.youzaiworldcore.login.title").getString();
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
        String subtitleText = Component.translatable("screen.youzaiworldcore.login.subtitle").getString();
        int subtitleWidth = this.font.width(subtitleText);
        guiGraphics.text(this.font, subtitleText,
                centerX - subtitleWidth / 2,
                containerTop + 35,
                0xFFCCCCCC, false);

        // 绘制标签
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.login.label_username").getString(),
                leftColX, containerTop + 58);
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.login.label_password").getString(),
                leftColX, containerTop + 58 + ROW_SPACING);

        // 手动渲染所有 widget
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox) {
                editBox.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
            } else if (widget instanceof TransparentButton button) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        // 渲染弹窗
        if (currentDialog != null && currentDialog.isVisible()) {
            currentDialog.render(guiGraphics, this.width, this.height);
            currentDialog.renderButtons(guiGraphics, mouseX, mouseY, partialTick);
        } else if (currentDialog != null && !currentDialog.isVisible()) {
            currentDialog = null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        // 弹窗优先
        if (currentDialog != null && currentDialog.isFullyVisible()) {
            return currentDialog.mouseClicked(event.x(), event.y());
        }

        double mx = event.x();
        double my = event.y();

        // 转发到 EditBox（需手动管理焦点）
        if (this.passwordField.mouseClicked(event, isActuallyClick)) {
            this.passwordField.setFocused(true);
            this.usernameField.setFocused(false);
            return true;
        }
        if (this.usernameField.mouseClicked(event, isActuallyClick)) {
            this.usernameField.setFocused(true);
            this.passwordField.setFocused(false);
            return true;
        }

        // 转发到按钮
        if (isMouseOverButton(this.loginButton, mx, my)) {
            this.loginButton.onClick(event, isActuallyClick);
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

        // 拦截 ESC
        if (keyEvent.key() == 256) { // GLFW_KEY_ESCAPE
            return true;
        }

        // Enter 键触发登入
        if (keyEvent.key() == 257 || keyEvent.key() == 335) { // GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER
            onLoginClick();
            return true;
        }

        // 转发到当前聚焦的 EditBox
        if (this.passwordField.isFocused() && this.passwordField.keyPressed(keyEvent)) return true;
        if (this.usernameField.isFocused() && this.usernameField.keyPressed(keyEvent)) return true;

        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent charEvent) {
        if (this.passwordField.isFocused() && this.passwordField.charTyped(charEvent)) return true;
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

    private void onLoginClick() {
        if (processing) return;

        String password = this.passwordField.getValue();

        if (password.isEmpty()) {
            showErrorDialog(
                    Component.translatable("screen.youzaiworldcore.login.error_title").getString(),
                    new String[]{Component.translatable("screen.youzaiworldcore.login.error_empty").getString()}
            );
            return;
        }

        processing = true;
        String command = "yzwc account login " + password;
        sendCommand(command);
    }

    private void onDisconnectClick() {
        if (processing) return;

        Minecraft.getInstance().disconnectFromWorld(
                Component.translatable("screen.youzaiworldcore.login.disconnect_message"));
    }

    // ===== 工具方法 =====

    private void sendCommand(String command) {
        if (Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.connection != null) {
            Minecraft.getInstance().player.connection.send(
                    new ServerboundChatCommandPacket(command));
        }
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private void showErrorDialog(String title, String[] messages) {
        this.currentDialog = new ConfirmationDialog(
                title,
                messages,
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
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
