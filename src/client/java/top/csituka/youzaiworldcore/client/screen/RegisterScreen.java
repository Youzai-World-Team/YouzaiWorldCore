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
 * 账户注册 GUI
 * 在玩家传送到虚空维度且未注册时显示。
 * 无返回按钮、无关闭按钮、不能使用 ESC 键关闭。
 */
@SuppressWarnings("null")
public class RegisterScreen extends Screen {
    private static final int CARD_WIDTH = 420;
    private static final int CARD_HEIGHT = 336;

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
        String savedPassword = passwordField == null ? "" : passwordField.getValue();
        String savedConfirmation = confirmPasswordField == null ? "" : confirmPasswordField.getValue();


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
        this.usernameField.setTextColor(YzuiTheme.textMuted());

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
        passwordField.setValue(savedPassword);
        confirmPasswordField.setValue(savedConfirmation);
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
        YzuiTheme.wrapped(g, font, Component.translatable("screen.youzaiworldcore.register.subtitle"), x + 24, y + 42, cardWidth - 48, 2, YzuiTheme.textMuted());
        YzuiTheme.wrapped(g, font, Component.translatable("screen.youzaiworldcore.register.hint_line1"),
                x + 24, y + 214, cardWidth - 48, 2, YzuiTheme.textMuted());
        YzuiTheme.wrapped(g, font, Component.translatable("screen.youzaiworldcore.register.hint_line2"),
                x + 24, y + 240, cardWidth - 48, 2, YzuiTheme.textMuted());
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
        if (currentDialog == null || !currentDialog.isVisible()) WidgetFocus.mouseFocus(event.x(), event.y(), List.of(passwordField, confirmPasswordField, registerButton, disconnectButton));
        // 弹窗优先处理点击
        if (currentDialog != null && currentDialog.isVisible()) {
            return currentDialog.mouseClicked(event.x(), event.y());
        }

        double mx = event.x();
        double my = event.y();

        // 转发点击到 EditBox（需手动管理焦点，只读账户框忽略点击以免出现光标闪烁）
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
        // 账户输入框为只读，不转发点击，避免出现光标闪烁标记
        if (isMouseOverEditBox(this.usernameField, mx, my)) {
            this.passwordField.setFocused(true);
            this.confirmPasswordField.setFocused(false);
            this.usernameField.setFocused(false);
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
        if (currentDialog != null && currentDialog.isVisible()) return currentDialog.keyPressed(keyEvent);
        if (currentDialog != null && currentDialog.isVisible()) return true;
        if (WidgetFocus.keyPressed(keyEvent, List.of(passwordField, confirmPasswordField, registerButton, disconnectButton))) return true;
        if (currentDialog != null && currentDialog.isVisible()) {
            return true;
        }

        // 拦截 ESC 键
        if (keyEvent.key() == 256) { // GLFW_KEY_ESCAPE
            return true;
        }

        // 转发键盘事件到当前聚焦的 EditBox（账户只读，不转发）
        if (this.passwordField.isFocused() && this.passwordField.keyPressed(keyEvent)) return true;
        if (this.confirmPasswordField.isFocused() && this.confirmPasswordField.keyPressed(keyEvent)) return true;

        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent charEvent) {
        if (currentDialog != null && currentDialog.isVisible()) return true;
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

        processing = true;
        sendAuthRequest(new AuthRequestPayload(AuthRequestPayload.Action.REGISTER, password, confirm));
    }

    private void onDisconnectClick() {
        if (processing) return;

        // 断开连接
        Minecraft.getInstance().disconnectFromWorld(
                Component.translatable("screen.youzaiworldcore.register.disconnect_message"));
    }

    // ===== 工具方法 =====

    private void sendAuthRequest(AuthRequestPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            ClientPlayNetworking.send(payload);
        }
        // 命令已发送，关闭当前 GUI，等待服务器处理
        if (Minecraft.getInstance().gui.screen() == this) {
            Minecraft.getInstance().setScreenAndShow(null);
        }
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
        passwordField.setRectangle(w, 24, x, y + 128);
        confirmPasswordField.setRectangle(w, 24, x, y + 172);
        registerButton.setRectangle((w - 12) / 2, 28, x, y + 280);
        disconnectButton.setRectangle((w - 12) / 2, 28, x + (w + 12) / 2, y + 280);
        registerButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentDialog != null && currentDialog.isVisible()) return currentDialog.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
