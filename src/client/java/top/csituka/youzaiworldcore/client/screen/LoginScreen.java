package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

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
        this.addRenderableWidget(this.usernameField);

        // 密码输入框
        this.passwordField = new EditBox(this.font, fieldX, containerTop + 55 + ROW_SPACING, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.login.label_password"));
        this.passwordField.setMaxLength(128);
        this.passwordField.setHint(Component.translatable("screen.youzaiworldcore.login.hint_password"));
        this.addRenderableWidget(this.passwordField);

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
        this.addRenderableWidget(this.loginButton);

        // 断开连接按钮
        this.disconnectButton = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + 12, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.login.button_disconnect"),
                this::onDisconnectClick
        );
        this.disconnectButton.setTextColor(0xFFFFFF);
        this.addRenderableWidget(this.disconnectButton);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 绘制半透明背景
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
                0xFFFFFF, false);
        guiGraphics.pose().popMatrix();

        // 绘制副标题
        String subtitleText = Component.translatable("screen.youzaiworldcore.login.subtitle").getString();
        int subtitleWidth = this.font.width(subtitleText);
        guiGraphics.text(this.font, subtitleText,
                centerX - subtitleWidth / 2,
                containerTop + 35,
                0xCCCCCC, false);

        // 绘制标签
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.login.label_username").getString(),
                leftColX, containerTop + 58);

        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.login.label_password").getString(),
                leftColX, containerTop + 58 + ROW_SPACING);

        // 渲染弹窗
        if (currentDialog != null && currentDialog.isVisible()) {
            currentDialog.render(guiGraphics, this.width, this.height);
            currentDialog.renderButtons(guiGraphics, mouseX, mouseY, partialTick);
        } else if (currentDialog != null && !currentDialog.isVisible()) {
            currentDialog = null;
        }

        // 如果有弹窗，不要让输入框聚焦
        if (currentDialog != null && currentDialog.isFullyVisible()) {
            this.setFocused(null);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        // 弹窗优先处理点击
        if (currentDialog != null && currentDialog.isFullyVisible()) {
            return currentDialog.mouseClicked(event.x(), event.y());
        }

        // 处理输入框和按钮的点击
        return super.mouseClicked(event, isActuallyClick);
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

        // Enter 键触发登入
        if (keyEvent.key() == 257 || keyEvent.key() == 335) { // GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER
            onLoginClick();
            return true;
        }

        return super.keyPressed(keyEvent);
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

        // 客户端本地校验
        if (password.isEmpty()) {
            showErrorDialog(
                    Component.translatable("screen.youzaiworldcore.login.error_title").getString(),
                    new String[]{Component.translatable("screen.youzaiworldcore.login.error_empty").getString()}
            );
            return;
        }

        // 发送登录命令
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
        // 命令已发送，关闭当前 GUI，等待服务器处理
        Minecraft.getInstance().setScreenAndShow(null);
    }

    private void showErrorDialog(String title, String[] messages) {
        this.currentDialog = new ConfirmationDialog(
                title,
                messages,
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
                () -> {
                    // 关闭弹窗后重新聚焦密码框
                    this.passwordField.setFocused(true);
                }
        );
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void drawLabel(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y) {
        int labelY = y + (FIELD_HEIGHT - font.lineHeight) / 2;
        guiGraphics.text(font, text, x, labelY, 0xFFFFFF, false);
    }
}
