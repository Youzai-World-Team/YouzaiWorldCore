package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.screen.widget.WidgetFocus;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
import top.csituka.youzaiworldcore.client.screen.widget.ConfirmationDialog;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.network.PasswordResetRequestPayload;
import top.csituka.youzaiworldcore.network.PasswordResetStatePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 登录前通过已绑定邮箱重置当前游戏账户密码。 */
@SuppressWarnings("null")
public class PasswordResetScreen extends Screen {
    private static final int CARD_WIDTH = 440;
    private static final int CARD_HEIGHT = 336;
    private static final int CONTAINER_WIDTH = 380;
    private static final int CONTAINER_HEIGHT = 270;
    private static final int LABEL_WIDTH = 65;
    private static final int FIELD_WIDTH = 190;
    private static final int FIELD_HEIGHT = 20;
    private static final int SEND_BUTTON_WIDTH = 88;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_SPACING = 32;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final String playerName;
    private final List<AbstractWidget> allWidgets = new ArrayList<>();

    private String sessionId = "";
    private EditBox emailField;
    private EditBox codeField;
    private EditBox passwordField;
    private EditBox confirmPasswordField;
    private TransparentButton sendCodeButton;
    private TransparentButton resetButton;
    private TransparentButton backButton;
    private ConfirmationDialog currentDialog;

    private long sessionExpiresAtMillis;
    private long resendAvailableAtMillis;
    private boolean codeSent;
    private boolean processing;
    private boolean expiryHandled;
    private boolean completionHandled;
    private String statusMessage = "";

    public PasswordResetScreen(String playerName) {
        super(Component.translatable("screen.youzaiworldcore.password_reset.title"));
        this.playerName = playerName;
    }

    /** 应用服务端返回的找回密码状态。 */
    public void applyState(PasswordResetStatePayload payload) {
        switch (payload.state()) {
            case CODE_SENT -> {
                this.processing = false;
                this.codeSent = true;
                this.expiryHandled = false;
                this.sessionId = payload.sessionId();
                updateSessionExpiry(payload.expiresInSeconds());
                this.resendAvailableAtMillis = System.currentTimeMillis()
                        + payload.resendAfterSeconds() * 1000L;
                this.statusMessage = Component.translatable(
                        "screen.youzaiworldcore.password_reset.status_code_sent").getString();
                if (this.codeField != null) focus(this.codeField);
            }
            case ERROR -> {
                this.processing = false;
                if (payload.resendAfterSeconds() > 0) {
                    this.resendAvailableAtMillis = System.currentTimeMillis()
                            + payload.resendAfterSeconds() * 1000L;
                }
                showError(payload.message());
            }
            case COMPLETED -> showCompleted();
            case EXPIRED -> showExpired(payload.message());
        }
        updateButtonState();
    }

    @Override
    protected void init() {
        super.init();
        String email = this.emailField == null ? "" : this.emailField.getValue();
        String code = this.codeField == null ? "" : this.codeField.getValue();
        String password = this.passwordField == null ? "" : this.passwordField.getValue();
        String confirmation = this.confirmPasswordField == null
                ? "" : this.confirmPasswordField.getValue();

        int centerX = this.width / 2;
        int containerTop = (this.height - CONTAINER_HEIGHT) / 2;
        int leftColX = centerX - CONTAINER_WIDTH / 2 + 10;
        int fieldX = leftColX + LABEL_WIDTH + 8;
        int emailY = containerTop + 65;
        int codeY = emailY + ROW_SPACING;
        int passwordY = codeY + ROW_SPACING;
        int confirmY = passwordY + ROW_SPACING;

        this.emailField = new EditBox(this.font, fieldX, emailY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register_email.label_email"));
        this.emailField.setMaxLength(254);
        this.emailField.setHint(Component.translatable("screen.youzaiworldcore.register_email.hint_email"));
        this.emailField.setValue(email);

        this.codeField = new EditBox(this.font, fieldX, codeY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register_email.label_code"));
        this.codeField.setMaxLength(6);
        this.codeField.setHint(Component.translatable("screen.youzaiworldcore.register_email.hint_code"));
        this.codeField.setValue(code);

        this.passwordField = new EditBox(this.font, fieldX, passwordY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.label_password"));
        this.passwordField.setMaxLength(128);
        this.passwordField.setHint(Component.translatable("screen.youzaiworldcore.register.hint_password"));
        this.passwordField.setValue(password);

        this.confirmPasswordField = new EditBox(this.font, fieldX, confirmY, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.label_confirm_password"));
        this.confirmPasswordField.setMaxLength(128);
        this.confirmPasswordField.setHint(Component.translatable("screen.youzaiworldcore.register.hint_confirm"));
        this.confirmPasswordField.setValue(confirmation);

        this.sendCodeButton = new TransparentButton(
                fieldX + FIELD_WIDTH + 8, emailY - 2, SEND_BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register_email.button_send_code"),
                this::onSendCodeClick);
        this.sendCodeButton.setTextColor(0xFFFFFF);

        int buttonY = containerTop + 207;
        int buttonStartX = centerX - (BUTTON_WIDTH * 2 + 12) / 2;
        this.resetButton = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.password_reset.button_reset"),
                this::onResetClick);
        this.resetButton.setTextColor(0xFFFFFF);

        this.backButton = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + 12, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.password_reset.button_back"),
                this::onBackClick);
        this.backButton.setTextColor(0xFFFFFF);

        this.allWidgets.clear();
        this.allWidgets.add(this.emailField);
        this.allWidgets.add(this.codeField);
        this.allWidgets.add(this.passwordField);
        this.allWidgets.add(this.confirmPasswordField);
        this.allWidgets.add(this.sendCodeButton);
        this.allWidgets.add(this.resetButton);
        this.allWidgets.add(this.backButton);

        arrangeForm();
        focus(this.codeSent ? this.codeField : this.emailField);
        if (this.currentDialog != null) this.currentDialog.init(this.width, this.height);
        updateButtonState();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonState();
        if (this.codeSent && !this.expiryHandled
                && System.currentTimeMillis() >= this.sessionExpiresAtMillis) {
            showExpired(Component.translatable(
                    "screen.youzaiworldcore.password_reset.session_expired").getString());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cardWidth = Math.min(CARD_WIDTH, width - 40);
        int x = (width - cardWidth) / 2, y = (height - CARD_HEIGHT) / 2;
        YzuiTheme.card(g, x, y, cardWidth, CARD_HEIGHT);
        YzuiTheme.label(g, font, title, x + 24, y + 20, cardWidth - 48, YzuiTheme.text(), false);
        YzuiTheme.wrapped(g, font, Component.translatable("screen.youzaiworldcore.password_reset.subtitle", playerName), x + 24, y + 42, cardWidth - 48, 2, YzuiTheme.textMuted());
        updateButtonState();
        if (codeSent) YzuiTheme.label(g, font, Component.translatable("screen.youzaiworldcore.password_reset.session_remaining",
                formatDuration(remainingSessionSeconds())), x + 24, y + 65, cardWidth - 48,
                remainingSessionSeconds() <= 60 ? YzuiTheme.error() : YzuiTheme.textMuted(), false);
        YzuiTheme.wrapped(g, font, Component.literal(statusMessage), x + 24, y + 227, cardWidth - 48, 2, YzuiTheme.success());
        YzuiTheme.wrapped(g, font, Component.translatable("screen.youzaiworldcore.password_reset.hint_line"),
                x + 24, y + 304, cardWidth - 48, 2, YzuiTheme.textMuted());
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
        if (currentDialog == null || !currentDialog.isVisible()) WidgetFocus.mouseFocus(event.x(), event.y(), allWidgets);
        if (this.currentDialog != null && this.currentDialog.isVisible()) {
            return this.currentDialog.mouseClicked(event.x(), event.y());
        }
        if (this.emailField.mouseClicked(event, isActuallyClick)) {
            focus(this.emailField);
            return true;
        }
        if (this.codeField.mouseClicked(event, isActuallyClick)) {
            focus(this.codeField);
            return true;
        }
        if (this.passwordField.mouseClicked(event, isActuallyClick)) {
            focus(this.passwordField);
            return true;
        }
        if (this.confirmPasswordField.mouseClicked(event, isActuallyClick)) {
            focus(this.confirmPasswordField);
            return true;
        }

        double x = event.x();
        double y = event.y();
        if (this.sendCodeButton.active && isMouseOverButton(this.sendCodeButton, x, y)) {
            this.sendCodeButton.onClick(event, isActuallyClick);
            return true;
        }
        if (this.resetButton.active && isMouseOverButton(this.resetButton, x, y)) {
            this.resetButton.onClick(event, isActuallyClick);
            return true;
        }
        if (this.backButton.active && isMouseOverButton(this.backButton, x, y)) {
            this.backButton.onClick(event, isActuallyClick);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (currentDialog != null && currentDialog.isVisible()) return currentDialog.keyPressed(keyEvent);
        if (currentDialog != null && currentDialog.isVisible()) return true;
        if (WidgetFocus.keyPressed(keyEvent, List.of(emailField, codeField, sendCodeButton, passwordField, confirmPasswordField, resetButton, backButton))) return true;
        if (this.currentDialog != null && this.currentDialog.isVisible()) return true;
        if (keyEvent.key() == 256) {
            onBackClick();
            return true;
        }
        if (keyEvent.key() == 257 || keyEvent.key() == 335) {
            if (this.codeSent) onResetClick();
            else onSendCodeClick();
            return true;
        }
        if (this.emailField.isFocused() && this.emailField.keyPressed(keyEvent)) return true;
        if (this.codeField.isFocused() && this.codeField.keyPressed(keyEvent)) return true;
        if (this.passwordField.isFocused() && this.passwordField.keyPressed(keyEvent)) return true;
        if (this.confirmPasswordField.isFocused()
                && this.confirmPasswordField.keyPressed(keyEvent)) return true;
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (currentDialog != null && currentDialog.isVisible()) return true;
        if (this.emailField.isFocused() && this.emailField.charTyped(event)) return true;
        if (this.codeField.isFocused() && this.codeField.charTyped(event)) return true;
        if (this.passwordField.isFocused() && this.passwordField.charTyped(event)) return true;
        return this.confirmPasswordField.isFocused() && this.confirmPasswordField.charTyped(event);
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

    private void onSendCodeClick() {
        if (this.processing || System.currentTimeMillis() < this.resendAvailableAtMillis) return;
        String email = this.emailField.getValue().trim();
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register_email.error_invalid_email").getString());
            return;
        }
        this.processing = true;
        send(PasswordResetRequestPayload.sendCode(this.sessionId, email));
        updateButtonState();
    }

    private void onResetClick() {
        if (this.processing) return;
        if (!this.codeSent || this.sessionId.isBlank()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.password_reset.error_send_first").getString());
            return;
        }
        String code = this.codeField.getValue().trim();
        if (!code.matches("\\d{6}")) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register_email.error_invalid_code").getString());
            return;
        }
        String password = this.passwordField.getValue();
        String confirmation = this.confirmPasswordField.getValue();
        if (password.isEmpty() || confirmation.isEmpty()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_empty").getString());
            return;
        }
        if (!password.equals(confirmation)) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_mismatch").getString());
            return;
        }
        if (password.length() < 4) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_too_short").getString());
            return;
        }
        this.processing = true;
        send(PasswordResetRequestPayload.resetPassword(this.sessionId, code, password));
        updateButtonState();
    }

    private void onBackClick() {
        if (this.processing) return;
        Minecraft.getInstance().setScreenAndShow(new LoginScreen(this.playerName));
    }

    private void send(PasswordResetRequestPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            ClientPlayNetworking.send(payload);
        } else {
            this.processing = false;
            showError("当前连接不可用，请重新进入服务器");
        }
    }

    private void showError(String message) {
        this.processing = false;
        this.currentDialog = new ConfirmationDialog(
                Component.translatable("screen.youzaiworldcore.password_reset.error_title").getString(),
                new String[]{message},
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
                () -> focus(this.codeSent ? this.codeField : this.emailField));
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void showCompleted() {
        if (this.completionHandled) return;
        this.completionHandled = true;
        this.processing = false;
        this.currentDialog = new ConfirmationDialog(
                Component.translatable("screen.youzaiworldcore.password_reset.success_title").getString(),
                new String[]{Component.translatable(
                        "screen.youzaiworldcore.password_reset.success_message").getString()},
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
                () -> Minecraft.getInstance().setScreenAndShow(new LoginScreen(this.playerName)));
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void showExpired(String message) {
        if (this.expiryHandled) return;
        this.expiryHandled = true;
        this.processing = false;
        this.currentDialog = new ConfirmationDialog(
                Component.translatable("screen.youzaiworldcore.password_reset.error_title").getString(),
                new String[]{message == null || message.isBlank()
                        ? Component.translatable(
                                "screen.youzaiworldcore.password_reset.session_expired").getString()
                        : message},
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
                () -> Minecraft.getInstance().setScreenAndShow(new LoginScreen(this.playerName)));
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void updateSessionExpiry(int expiresInSeconds) {
        int safeSeconds = Math.max(1, Math.min(86_400, expiresInSeconds));
        this.sessionExpiresAtMillis = System.currentTimeMillis() + safeSeconds * 1000L;
    }

    private void updateButtonState() {
        if (this.sendCodeButton == null || this.resetButton == null || this.backButton == null) return;
        long now = System.currentTimeMillis();
        int resendSeconds = (int) Math.max(0L,
                (this.resendAvailableAtMillis - now + 999L) / 1000L);
        if (resendSeconds > 0) {
            this.sendCodeButton.setMessage(Component.translatable(
                    "screen.youzaiworldcore.register_email.button_resend_countdown", resendSeconds));
        } else {
            this.sendCodeButton.setMessage(Component.translatable(this.codeSent
                    ? "screen.youzaiworldcore.register_email.button_resend_code"
                    : "screen.youzaiworldcore.register_email.button_send_code"));
        }
        boolean sessionActive = this.codeSent && !this.expiryHandled
                && now < this.sessionExpiresAtMillis;
        this.sendCodeButton.active = !this.processing && resendSeconds == 0 && !this.completionHandled;
        this.resetButton.active = sessionActive && !this.processing && !this.completionHandled;
        this.backButton.active = !this.processing;
    }

    private int remainingSessionSeconds() {
        return (int) Math.max(0L,
                (this.sessionExpiresAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    private String formatDuration(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void focus(EditBox target) {
        if (this.emailField != null) this.emailField.setFocused(target == this.emailField);
        if (this.codeField != null) this.codeField.setFocused(target == this.codeField);
        if (this.passwordField != null) this.passwordField.setFocused(target == this.passwordField);
        if (this.confirmPasswordField != null) {
            this.confirmPasswordField.setFocused(target == this.confirmPasswordField);
        }
    }

    private void drawLabel(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        graphics.text(font, text, x, y + (FIELD_HEIGHT - font.lineHeight) / 2,
                YzuiTheme.text(), false);
    }

    private boolean isMouseOverButton(TransparentButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    private void arrangeForm() {
        int cardWidth = Math.min(CARD_WIDTH, width - 40);
        int x = (width - cardWidth) / 2 + 24, y = (height - CARD_HEIGHT) / 2;
        int w = cardWidth - 48;
        emailField.setRectangle(w, 24, x, y + 90);
        codeField.setRectangle(w - 120, 24, x, y + 140);
        sendCodeButton.setRectangle(112, 24, x + w - 112, y + 140);
        passwordField.setRectangle((w - 12) / 2, 24, x, y + 190);
        confirmPasswordField.setRectangle((w - 12) / 2, 24, x + (w + 12) / 2, y + 190);
        resetButton.setRectangle((w - 12) / 2, 28, x, y + 258);
        backButton.setRectangle((w - 12) / 2, 28, x + (w + 12) / 2, y + 258);
        resetButton.setStyle(YzuiTheme.ButtonStyle.FILLED);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (currentDialog != null && currentDialog.isVisible()) return currentDialog.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
