package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.WidgetFocus;

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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.AuthRequestPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * 账户登入 GUI
 * 在玩家传送到虚空维度且已注册但未登录时显示。
 * 无返回按钮、无关闭按钮、不能使用 ESC 键关闭。
 */
@SuppressWarnings("null")
public class LoginScreen extends Screen {
    private static final int CARD_WIDTH = 400;
    private static final int CARD_HEIGHT = 288;

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
    private TransparentButton forgotPasswordButton;

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
        String savedPassword = passwordField == null ? "" : passwordField.getValue();


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
        this.usernameField.setTextColor(YzuiTheme.textMuted());

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

        this.forgotPasswordButton = new TransparentButton(
                centerX - 60, buttonY + 31, 120, 20,
                Component.translatable("screen.youzaiworldcore.login.button_forgot_password"),
                this::onForgotPasswordClick
        );
        this.forgotPasswordButton.setTextColor(YzuiTheme.textMuted());

        // 收集所有 widget
        this.allWidgets.clear();
        this.allWidgets.add(this.usernameField);
        this.allWidgets.add(this.passwordField);
        this.allWidgets.add(this.loginButton);
        this.allWidgets.add(this.disconnectButton);
        this.allWidgets.add(this.forgotPasswordButton);

        // 默认聚焦密码框
        passwordField.setValue(savedPassword);
        arrangeForm();
        if (currentDialog != null) currentDialog.init(width, height);
        this.passwordField.setFocused(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cardWidth = Math.min(CARD_WIDTH, width - 40);
        int x = (width - cardWidth) / 2, y = (height - CARD_HEIGHT) / 2;
        YzuiTheme.card(g, x, y, cardWidth, CARD_HEIGHT);
        YzuiTheme.label(g, font, title, x + 24, y + 20, cardWidth - 48, YzuiTheme.text(), false);
        YzuiTheme.wrapped(g, font, Component.translatable("screen.youzaiworldcore.login.subtitle"), x + 24, y + 42, cardWidth - 48, 2, YzuiTheme.textMuted());

        for (AbstractWidget widget : allWidgets) {
            if (widget instanceof EditBox input) {
                YzuiTheme.label(g, font, input.getMessage(), input.getX(), input.getY() - 12,
                        input.getWidth(), YzuiTheme.textMuted(), false);
            }
            widget.extractRenderState(g, mouseX, mouseY, partialTick);
        }
        if (currentDialog != null && currentDialog.isVisible()) {
            currentDialog.render(g, width, height);
            currentDialog.renderButtons(g, mouseX, mouseY, partialTick);
        } else if (currentDialog != null) currentDialog = null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (currentDialog == null || !currentDialog.isVisible()) WidgetFocus.mouseFocus(event.x(), event.y(), List.of(passwordField, loginButton, disconnectButton, forgotPasswordButton));
        // 弹窗优先
        if (currentDialog != null && currentDialog.isVisible()) {
            return currentDialog.mouseClicked(event.x(), event.y());
        }

        double mx = event.x();
        double my = event.y();

        // 转发到 EditBox（需手动管理焦点，只读账户框忽略点击以免出现光标闪烁）
        if (this.passwordField.mouseClicked(event, isActuallyClick)) {
            this.passwordField.setFocused(true);
            this.usernameField.setFocused(false);
            return true;
        }
        // 账户输入框为只读，不转发点击，避免出现光标闪烁标记
        if (isMouseOverEditBox(this.usernameField, mx, my)) {
            this.passwordField.setFocused(true);
            this.usernameField.setFocused(false);
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
        if (isMouseOverButton(this.forgotPasswordButton, mx, my)) {
            this.forgotPasswordButton.onClick(event, isActuallyClick);
            return true;
        }

        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (currentDialog != null && currentDialog.isVisible()) return currentDialog.keyPressed(keyEvent);
        if (currentDialog != null && currentDialog.isVisible()) return true;
        if (WidgetFocus.keyPressed(keyEvent, List.of(passwordField, loginButton, disconnectButton, forgotPasswordButton))) return true;
        if (currentDialog != null && currentDialog.isVisible()) {
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

        // 转发到当前聚焦的 EditBox（账户只读，不转发）
        if (this.passwordField.isFocused() && this.passwordField.keyPressed(keyEvent)) return true;

        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent charEvent) {
        if (currentDialog != null && currentDialog.isVisible()) return true;
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
        sendAuthRequest(new AuthRequestPayload(AuthRequestPayload.Action.LOGIN, password, ""));
    }

    private void onDisconnectClick() {
        if (processing) return;

        Minecraft.getInstance().disconnectFromWorld(
                Component.translatable("screen.youzaiworldcore.login.disconnect_message"));
    }

    private void onForgotPasswordClick() {
        if (processing) return;
        Minecraft.getInstance().setScreenAndShow(new PasswordResetScreen(this.playerName));
    }

    // ===== 工具方法 =====

    private void sendAuthRequest(AuthRequestPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            ClientPlayNetworking.send(payload);
        }
        if (Minecraft.getInstance().gui.screen() == this) {
            Minecraft.getInstance().setScreenAndShow(null);
        }
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
        guiGraphics.text(font, text, x, labelY, YzuiTheme.text(), false);
    }

    private boolean isMouseOverButton(TransparentButton button, double mx, double my) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    private boolean isMouseOverEditBox(EditBox box, double mx, double my) {
        return mx >= box.getX() && mx < box.getX() + box.getWidth()
                && my >= box.getY() && my < box.getY() + box.getHeight();
    }

    private void arrangeForm() {
        int cardWidth = Math.min(CARD_WIDTH, width - 40);
        int x = (width - cardWidth) / 2 + 24, y = (height - CARD_HEIGHT) / 2;
        int w = cardWidth - 48;
        usernameField.setRectangle(w, 24, x, y + 84);
        passwordField.setRectangle(w, 24, x, y + 132);
        loginButton.setRectangle((w - 12) / 2, 28, x, y + 188);
        disconnectButton.setRectangle((w - 12) / 2, 28, x + (w + 12) / 2, y + 188);
        forgotPasswordButton.setRectangle(w, 24, x, y + 228);
        loginButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
        forgotPasswordButton.setStyle(YzuiTheme.ButtonStyle.TEXT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentDialog != null && currentDialog.isVisible()) return currentDialog.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
