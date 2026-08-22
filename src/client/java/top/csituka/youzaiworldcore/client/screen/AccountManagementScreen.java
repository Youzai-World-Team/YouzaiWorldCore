package top.csituka.youzaiworldcore.client.screen;

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
import top.csituka.youzaiworldcore.network.AccountManagementRequestPayload;
import top.csituka.youzaiworldcore.network.AccountManagementStatePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 已登录玩家的账户管理页面。 */
@SuppressWarnings("null")
public class AccountManagementScreen extends Screen {
    private static final int CONTAINER_WIDTH = 420;
    private static final int CONTAINER_HEIGHT = 280;
    private static final int FIELD_WIDTH = 190;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_SPACING = 34;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private enum Mode {
        HOME,
        CHANGE_PASSWORD,
        CHANGE_EMAIL,
        DEACTIVATE
    }

    private final Screen parent;
    private final String playerName;
    private final List<AbstractWidget> allWidgets = new ArrayList<>();

    private Mode mode = Mode.HOME;
    private boolean loaded;
    private boolean loadRequested;
    private boolean processing;
    private boolean completed;
    private String currentEmail = "";
    private String statusMessage = "";
    private String emailChangeSessionId = "";
    private boolean emailCodeSent;
    private long emailSessionExpiresAtMillis;
    private long resendAvailableAtMillis;

    private String passwordCurrentValue = "";
    private String passwordNewValue = "";
    private String passwordConfirmationValue = "";
    private String emailCurrentPasswordValue = "";
    private String newEmailValue = "";
    private String emailCodeValue = "";
    private String deactivatePasswordValue = "";

    private EditBox currentPasswordField;
    private EditBox newPasswordField;
    private EditBox confirmPasswordField;
    private EditBox emailField;
    private EditBox codeField;
    private EditBox deactivatePasswordField;

    private TransparentButton changePasswordButton;
    private TransparentButton changeEmailButton;
    private TransparentButton deactivateModeButton;
    private TransparentButton closeButton;
    private TransparentButton submitPasswordButton;
    private TransparentButton sendCodeButton;
    private TransparentButton verifyEmailButton;
    private TransparentButton confirmDeactivateButton;
    private TransparentButton backButton;
    private ConfirmationDialog currentDialog;

    public AccountManagementScreen(Screen parent) {
        super(Component.translatable("screen.youzaiworldcore.account_management.title"));
        this.parent = parent;
        var player = Minecraft.getInstance().player;
        this.playerName = player == null ? "" : player.getScoreboardName();
    }

    /** 应用服务端返回的账户快照或操作结果。 */
    public void applyState(AccountManagementStatePayload payload) {
        switch (payload.state()) {
            case LOADED -> {
                this.loaded = true;
                this.loadRequested = true;
                this.processing = false;
                this.currentEmail = payload.email();
                this.statusMessage = "";
            }
            case PASSWORD_CHANGED -> {
                this.processing = false;
                this.completed = true;
                showSuccess(
                        Component.translatable(
                                "screen.youzaiworldcore.account_management.password_changed").getString(),
                        () -> Minecraft.getInstance().setScreenAndShow(new LoginScreen(this.playerName)));
            }
            case EMAIL_CODE_SENT -> {
                this.processing = false;
                this.emailCodeSent = true;
                this.emailChangeSessionId = payload.sessionId();
                this.emailSessionExpiresAtMillis = System.currentTimeMillis()
                        + Math.max(1, payload.expiresInSeconds()) * 1000L;
                this.resendAvailableAtMillis = System.currentTimeMillis()
                        + payload.resendAfterSeconds() * 1000L;
                this.statusMessage = Component.translatable(
                        "screen.youzaiworldcore.account_management.status_code_sent").getString();
                if (this.codeField != null) focus(this.codeField);
            }
            case EMAIL_CHANGED -> {
                this.processing = false;
                this.currentEmail = payload.email();
                this.emailCodeSent = false;
                this.emailChangeSessionId = "";
                this.emailCodeValue = "";
                this.statusMessage = "";
                showSuccess(
                        Component.translatable(
                                "screen.youzaiworldcore.account_management.email_changed").getString(),
                        () -> switchMode(Mode.HOME));
            }
            case DEACTIVATED -> {
                this.processing = false;
                this.completed = true;
                showSuccess(
                        Component.translatable(
                                "screen.youzaiworldcore.account_management.deactivated").getString(),
                        () -> Minecraft.getInstance().setScreenAndShow(null));
            }
            case ERROR -> {
                this.processing = false;
                if (payload.resendAfterSeconds() > 0) {
                    this.resendAvailableAtMillis = System.currentTimeMillis()
                            + payload.resendAfterSeconds() * 1000L;
                }
                if (!this.loaded && this.mode == Mode.HOME) this.loadRequested = false;
                showError(payload.message());
            }
            case EXPIRED -> {
                this.processing = false;
                this.emailCodeSent = false;
                this.emailChangeSessionId = "";
                this.emailCodeValue = "";
                showError(payload.message());
            }
        }
        updateButtonState();
    }

    @Override
    protected void init() {
        super.init();
        captureValues();
        clearWidgetReferences();
        this.allWidgets.clear();

        switch (this.mode) {
            case HOME -> initHome();
            case CHANGE_PASSWORD -> initPasswordForm();
            case CHANGE_EMAIL -> initEmailForm();
            case DEACTIVATE -> initDeactivateForm();
        }
        if (this.currentDialog != null) this.currentDialog.init(this.width, this.height);
        updateButtonState();
        if (!this.loaded && !this.loadRequested) requestLoad();
    }

    private void initHome() {
        int centerX = this.width / 2;
        int top = containerTop();
        int x = centerX - 90;
        int width = 180;
        this.changePasswordButton = button(
                x, top + 82, width,
                "screen.youzaiworldcore.account_management.button_change_password",
                () -> switchMode(Mode.CHANGE_PASSWORD));
        this.changeEmailButton = button(
                x, top + 116, width,
                "screen.youzaiworldcore.account_management.button_change_email",
                () -> switchMode(Mode.CHANGE_EMAIL));
        this.deactivateModeButton = button(
                x, top + 150, width,
                "screen.youzaiworldcore.account_management.button_deactivate",
                () -> switchMode(Mode.DEACTIVATE));
        this.deactivateModeButton.setTextColor(0xFFFF8080);
        this.closeButton = button(
                centerX - BUTTON_WIDTH / 2, top + 205, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_close", this::onClose);
    }

    private void initPasswordForm() {
        int centerX = this.width / 2;
        int top = containerTop();
        int fieldX = centerX - FIELD_WIDTH / 2 + 35;
        int firstY = top + 77;

        this.currentPasswordField = editBox(
                fieldX, firstY,
                "screen.youzaiworldcore.account_management.label_current_password",
                this.passwordCurrentValue, 128);
        this.newPasswordField = editBox(
                fieldX, firstY + ROW_SPACING,
                "screen.youzaiworldcore.account_management.label_new_password",
                this.passwordNewValue, 128);
        this.confirmPasswordField = editBox(
                fieldX, firstY + ROW_SPACING * 2,
                "screen.youzaiworldcore.register.label_confirm_password",
                this.passwordConfirmationValue, 128);

        int buttonY = top + 207;
        this.submitPasswordButton = button(
                centerX - BUTTON_WIDTH - 6, buttonY, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_save_password",
                this::onChangePassword);
        this.backButton = button(
                centerX + 6, buttonY, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_back", this::onBack);
        focus(this.currentPasswordField);
    }

    private void initEmailForm() {
        int centerX = this.width / 2;
        int top = containerTop();
        int fieldX = centerX - FIELD_WIDTH / 2 + 15;
        int firstY = top + 73;

        this.currentPasswordField = editBox(
                fieldX, firstY,
                "screen.youzaiworldcore.account_management.label_current_password",
                this.emailCurrentPasswordValue, 128);
        this.emailField = editBox(
                fieldX, firstY + ROW_SPACING,
                "screen.youzaiworldcore.account_management.label_new_email",
                this.newEmailValue, 254);
        this.codeField = editBox(
                fieldX, firstY + ROW_SPACING * 2,
                "screen.youzaiworldcore.register_email.label_code",
                this.emailCodeValue, 6);
        this.sendCodeButton = button(
                fieldX + FIELD_WIDTH + 8, firstY + ROW_SPACING - 2, 88,
                "screen.youzaiworldcore.register_email.button_send_code", this::onSendEmailCode);

        int buttonY = top + 207;
        this.verifyEmailButton = button(
                centerX - BUTTON_WIDTH - 6, buttonY, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_verify_email",
                this::onVerifyEmail);
        this.backButton = button(
                centerX + 6, buttonY, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_back", this::onBack);
        focus(this.emailCodeSent ? this.codeField : this.currentPasswordField);
    }

    private void initDeactivateForm() {
        int centerX = this.width / 2;
        int top = containerTop();
        int fieldX = centerX - FIELD_WIDTH / 2 + 35;
        this.deactivatePasswordField = editBox(
                fieldX, top + 128,
                "screen.youzaiworldcore.account_management.label_current_password",
                this.deactivatePasswordValue, 128);

        int buttonY = top + 190;
        this.confirmDeactivateButton = button(
                centerX - BUTTON_WIDTH - 6, buttonY, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_deactivate_confirm",
                this::onDeactivate);
        this.confirmDeactivateButton.setTextColor(0xFFFF8080);
        this.backButton = button(
                centerX + 6, buttonY, BUTTON_WIDTH,
                "screen.youzaiworldcore.account_management.button_back", this::onBack);
        focus(this.deactivatePasswordField);
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonState();
        if (this.emailCodeSent
                && System.currentTimeMillis() >= this.emailSessionExpiresAtMillis) {
            this.emailCodeSent = false;
            this.emailChangeSessionId = "";
            this.emailCodeValue = "";
            if (this.codeField != null) this.codeField.setValue("");
            showError(Component.translatable(
                    "screen.youzaiworldcore.account_management.email_session_expired").getString());
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        updateButtonState();
        graphics.fill(0, 0, this.width, this.height, 0xB0000000);
        int centerX = this.width / 2;
        int top = containerTop();

        String title = Component.translatable(
                "screen.youzaiworldcore.account_management.title").getString();
        float titleScale = 1.3F;
        int titleWidth = (int) (this.font.width(title) * titleScale);
        graphics.pose().pushMatrix();
        graphics.pose().scale(titleScale, titleScale);
        graphics.text(this.font, title,
                (int) ((centerX - titleWidth / 2) / titleScale),
                (int) ((top + 8) / titleScale), 0xFFFFFFFF, false);
        graphics.pose().popMatrix();

        String subtitle = Component.translatable(
                "screen.youzaiworldcore.account_management.subtitle", this.playerName).getString();
        graphics.text(this.font, subtitle, centerX - this.font.width(subtitle) / 2,
                top + 34, 0xFFCCCCCC, false);

        switch (this.mode) {
            case HOME -> renderHome(graphics, centerX, top);
            case CHANGE_PASSWORD -> renderPasswordForm(graphics, centerX, top);
            case CHANGE_EMAIL -> renderEmailForm(graphics, centerX, top);
            case DEACTIVATE -> renderDeactivateForm(graphics, centerX, top);
        }

        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox) {
                editBox.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
            } else if (widget instanceof TransparentButton transparentButton) {
                transparentButton.render(graphics, mouseX, mouseY, partialTick);
            }
        }
        if (this.currentDialog != null && this.currentDialog.isVisible()) {
            this.currentDialog.render(graphics, this.width, this.height);
            this.currentDialog.renderButtons(graphics, mouseX, mouseY, partialTick);
        } else if (this.currentDialog != null) {
            this.currentDialog = null;
        }
    }

    private void renderHome(GuiGraphicsExtractor graphics, int centerX, int top) {
        String emailText;
        if (!this.loaded) {
            emailText = Component.translatable(
                    "screen.youzaiworldcore.account_management.email_loading").getString();
        } else {
            String email = this.currentEmail.isBlank()
                    ? Component.translatable(
                            "screen.youzaiworldcore.account_management.email_unbound").getString()
                    : abbreviate(this.currentEmail, 48);
            emailText = Component.translatable(
                    "screen.youzaiworldcore.account_management.current_email", email).getString();
        }
        graphics.text(this.font, emailText, centerX - this.font.width(emailText) / 2,
                top + 59, 0xFFAAAAAA, false);
    }

    private void renderPasswordForm(GuiGraphicsExtractor graphics, int centerX, int top) {
        renderModeTitle(graphics, centerX, top,
                "screen.youzaiworldcore.account_management.change_password_title");
        int fieldX = centerX - FIELD_WIDTH / 2 + 35;
        int labelX = fieldX - 104;
        int firstY = top + 77;
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.account_management.label_current_password",
                labelX, firstY);
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.account_management.label_new_password",
                labelX, firstY + ROW_SPACING);
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.register.label_confirm_password",
                labelX, firstY + ROW_SPACING * 2);
    }

    private void renderEmailForm(GuiGraphicsExtractor graphics, int centerX, int top) {
        renderModeTitle(graphics, centerX, top,
                "screen.youzaiworldcore.account_management.change_email_title");
        int fieldX = centerX - FIELD_WIDTH / 2 + 15;
        int labelX = fieldX - 104;
        int firstY = top + 73;
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.account_management.label_current_password",
                labelX, firstY);
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.account_management.label_new_email",
                labelX, firstY + ROW_SPACING);
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.register_email.label_code",
                labelX, firstY + ROW_SPACING * 2);

        if (this.emailCodeSent) {
            String remaining = Component.translatable(
                    "screen.youzaiworldcore.account_management.email_session_remaining",
                    formatDuration(remainingEmailSessionSeconds())).getString();
            graphics.text(this.font, remaining, centerX - this.font.width(remaining) / 2,
                    top + 178, remainingEmailSessionSeconds() <= 60
                            ? 0xFFFF8080 : 0xFFAAAAAA, false);
        }
        if (!this.statusMessage.isBlank()) {
            graphics.text(this.font, this.statusMessage,
                    centerX - this.font.width(this.statusMessage) / 2,
                    top + 192, 0xFF80E080, false);
        }
    }

    private void renderDeactivateForm(GuiGraphicsExtractor graphics, int centerX, int top) {
        renderModeTitle(graphics, centerX, top,
                "screen.youzaiworldcore.account_management.deactivate_title");
        String warning = Component.translatable(
                "screen.youzaiworldcore.account_management.deactivate_warning").getString();
        graphics.text(this.font, warning, centerX - this.font.width(warning) / 2,
                top + 82, 0xFFFF8080, false);
        int fieldX = centerX - FIELD_WIDTH / 2 + 35;
        drawLabel(graphics, this.font,
                "screen.youzaiworldcore.account_management.label_current_password",
                fieldX - 104, top + 128);
    }

    private void renderModeTitle(
            GuiGraphicsExtractor graphics, int centerX, int top, String translationKey) {
        String modeTitle = Component.translatable(translationKey).getString();
        graphics.text(this.font, modeTitle, centerX - this.font.width(modeTitle) / 2,
                top + 52, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (this.currentDialog != null && this.currentDialog.isFullyVisible()) {
            return this.currentDialog.mouseClicked(event.x(), event.y());
        }
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox
                    && editBox.mouseClicked(event, isActuallyClick)) {
                focus(editBox);
                return true;
            }
        }
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof TransparentButton button
                    && button.active
                    && isMouseOverButton(button, event.x(), event.y())) {
                button.onClick(event, isActuallyClick);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.currentDialog != null && this.currentDialog.isFullyVisible()) return true;
        if (event.key() == 256) {
            onBack();
            return true;
        }
        if (event.key() == 257 || event.key() == 335) {
            switch (this.mode) {
                case HOME -> {
                }
                case CHANGE_PASSWORD -> onChangePassword();
                case CHANGE_EMAIL -> {
                    if (this.emailCodeSent) onVerifyEmail();
                    else onSendEmailCode();
                }
                case DEACTIVATE -> onDeactivate();
            }
            return true;
        }
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox
                    && editBox.isFocused()
                    && editBox.keyPressed(event)) return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox
                    && editBox.isFocused()
                    && editBox.charTyped(event)) return true;
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        // 页面需要持续处理 C2S/S2C 账户请求，不能暂停集成服务端。
        return false;
    }

    @Override
    public void removed() {
        captureValues();
        this.currentDialog = null;
        super.removed();
    }

    private void requestLoad() {
        this.loadRequested = true;
        this.processing = true;
        if (!send(AccountManagementRequestPayload.load())) {
            this.loadRequested = false;
        }
        updateButtonState();
    }

    private void onChangePassword() {
        if (this.processing || !this.loaded) return;
        captureValues();
        if (this.passwordCurrentValue.isEmpty()
                || this.passwordNewValue.isEmpty()
                || this.passwordConfirmationValue.isEmpty()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_empty").getString());
            return;
        }
        if (!this.passwordNewValue.equals(this.passwordConfirmationValue)) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_mismatch").getString());
            return;
        }
        if (this.passwordNewValue.length() < 4) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_too_short").getString());
            return;
        }
        if (this.passwordNewValue.length() > 128) {
            showError(Component.translatable(
                    "youzaiworldcore.message.account.password_too_long").getString());
            return;
        }
        this.processing = true;
        send(AccountManagementRequestPayload.changePassword(
                this.passwordCurrentValue, this.passwordNewValue));
        updateButtonState();
    }

    private void onSendEmailCode() {
        if (this.processing || !this.loaded
                || System.currentTimeMillis() < this.resendAvailableAtMillis) return;
        captureValues();
        if (this.emailCurrentPasswordValue.isEmpty()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_empty").getString());
            return;
        }
        String email = this.newEmailValue.trim();
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register_email.error_invalid_email").getString());
            return;
        }
        this.processing = true;
        send(AccountManagementRequestPayload.sendEmailCode(
                this.emailCurrentPasswordValue, email));
        updateButtonState();
    }

    private void onVerifyEmail() {
        if (this.processing || !this.emailCodeSent || this.emailChangeSessionId.isBlank()) {
            if (!this.processing) {
                showError(Component.translatable(
                        "screen.youzaiworldcore.account_management.error_send_first").getString());
            }
            return;
        }
        captureValues();
        String code = this.emailCodeValue.trim();
        if (!code.matches("\\d{6}")) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register_email.error_invalid_code").getString());
            return;
        }
        this.processing = true;
        send(AccountManagementRequestPayload.verifyEmailCode(
                this.emailChangeSessionId, code));
        updateButtonState();
    }

    private void onDeactivate() {
        if (this.processing || !this.loaded) return;
        captureValues();
        if (this.deactivatePasswordValue.isEmpty()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register.error_empty").getString());
            return;
        }
        this.currentDialog = new ConfirmationDialog(
                Component.translatable(
                        "screen.youzaiworldcore.account_management.confirm_deactivate_title").getString(),
                new String[]{
                        Component.translatable(
                                "screen.youzaiworldcore.account_management.confirm_deactivate_line1").getString(),
                        Component.translatable(
                                "screen.youzaiworldcore.account_management.confirm_deactivate_line2").getString(),
                },
                () -> {
                    this.processing = true;
                    send(AccountManagementRequestPayload.deactivate(
                            this.deactivatePasswordValue));
                    updateButtonState();
                },
                () -> focus(this.deactivatePasswordField));
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void onBack() {
        if (this.processing || this.completed) return;
        if (this.mode == Mode.HOME) onClose();
        else switchMode(Mode.HOME);
    }

    @Override
    public void onClose() {
        if (this.processing || this.completed) return;
        Minecraft.getInstance().setScreenAndShow(this.parent);
    }

    private boolean send(AccountManagementRequestPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            ClientPlayNetworking.send(payload);
            return true;
        }
        this.processing = false;
        showError(Component.translatable(
                "screen.youzaiworldcore.account_management.no_connection").getString());
        return false;
    }

    private void switchMode(Mode nextMode) {
        if (this.processing || this.completed) return;
        Mode previousMode = this.mode;
        captureValues();
        if (previousMode != nextMode) clearFormValues(previousMode);
        clearWidgetReferences();
        this.currentDialog = null;
        this.statusMessage = "";
        this.mode = nextMode;
        this.init();
    }

    private void showError(String message) {
        this.processing = false;
        this.currentDialog = new ConfirmationDialog(
                Component.translatable(
                        "screen.youzaiworldcore.account_management.error_title").getString(),
                new String[]{message == null || message.isBlank()
                        ? Component.translatable(
                                "screen.youzaiworldcore.account_management.error_unknown").getString()
                        : message},
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
                () -> {
                    if (!this.loaded && this.mode == Mode.HOME && !this.loadRequested) {
                        requestLoad();
                    } else {
                        focusFirstField();
                    }
                });
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void showSuccess(String message, Runnable onConfirm) {
        this.currentDialog = new ConfirmationDialog(
                Component.translatable(
                        "screen.youzaiworldcore.account_management.success_title").getString(),
                new String[]{message},
                Component.translatable("screen.youzaiworldcore.login.dialog_ok").getString(),
                onConfirm);
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void updateButtonState() {
        long now = System.currentTimeMillis();
        int resendSeconds = (int) Math.max(0L,
                (this.resendAvailableAtMillis - now + 999L) / 1000L);
        if (this.changePasswordButton != null) {
            this.changePasswordButton.active = this.loaded && !this.processing && !this.completed;
        }
        if (this.changeEmailButton != null) {
            this.changeEmailButton.active = this.loaded && !this.processing && !this.completed;
        }
        if (this.deactivateModeButton != null) {
            this.deactivateModeButton.active = this.loaded && !this.processing && !this.completed;
        }
        if (this.closeButton != null) this.closeButton.active = !this.processing && !this.completed;
        if (this.submitPasswordButton != null) {
            this.submitPasswordButton.active = this.loaded && !this.processing && !this.completed;
        }
        if (this.sendCodeButton != null) {
            this.sendCodeButton.active = this.loaded && !this.processing
                    && resendSeconds == 0 && !this.completed;
            if (resendSeconds > 0) {
                this.sendCodeButton.setMessage(Component.translatable(
                        "screen.youzaiworldcore.register_email.button_resend_countdown",
                        resendSeconds));
            } else {
                this.sendCodeButton.setMessage(Component.translatable(this.emailCodeSent
                        ? "screen.youzaiworldcore.register_email.button_resend_code"
                        : "screen.youzaiworldcore.register_email.button_send_code"));
            }
        }
        if (this.verifyEmailButton != null) {
            this.verifyEmailButton.active = this.loaded && this.emailCodeSent
                    && now < this.emailSessionExpiresAtMillis
                    && !this.processing && !this.completed;
        }
        if (this.confirmDeactivateButton != null) {
            this.confirmDeactivateButton.active = this.loaded && !this.processing && !this.completed;
        }
        if (this.backButton != null) this.backButton.active = !this.processing && !this.completed;
    }

    private TransparentButton button(
            int x, int y, int width, String translationKey, Runnable onClick) {
        TransparentButton button = new TransparentButton(
                x, y, width, BUTTON_HEIGHT, Component.translatable(translationKey), onClick);
        button.setTextColor(0xFFFFFFFF);
        this.allWidgets.add(button);
        return button;
    }

    private EditBox editBox(
            int x, int y, String translationKey, String value, int maxLength) {
        EditBox editBox = new EditBox(
                this.font, x, y, FIELD_WIDTH, FIELD_HEIGHT, Component.translatable(translationKey));
        editBox.setMaxLength(maxLength);
        editBox.setHint(Component.translatable(translationKey));
        editBox.setValue(value == null ? "" : value);
        this.allWidgets.add(editBox);
        return editBox;
    }

    private void captureValues() {
        if (this.mode == Mode.CHANGE_PASSWORD) {
            if (this.currentPasswordField != null) {
                this.passwordCurrentValue = this.currentPasswordField.getValue();
            }
            if (this.newPasswordField != null) this.passwordNewValue = this.newPasswordField.getValue();
            if (this.confirmPasswordField != null) {
                this.passwordConfirmationValue = this.confirmPasswordField.getValue();
            }
        } else if (this.mode == Mode.CHANGE_EMAIL) {
            if (this.currentPasswordField != null) {
                this.emailCurrentPasswordValue = this.currentPasswordField.getValue();
            }
            if (this.emailField != null) this.newEmailValue = this.emailField.getValue();
            if (this.codeField != null) this.emailCodeValue = this.codeField.getValue();
        } else if (this.mode == Mode.DEACTIVATE && this.deactivatePasswordField != null) {
            this.deactivatePasswordValue = this.deactivatePasswordField.getValue();
        }
    }

    private void clearWidgetReferences() {
        this.currentPasswordField = null;
        this.newPasswordField = null;
        this.confirmPasswordField = null;
        this.emailField = null;
        this.codeField = null;
        this.deactivatePasswordField = null;
        this.changePasswordButton = null;
        this.changeEmailButton = null;
        this.deactivateModeButton = null;
        this.closeButton = null;
        this.submitPasswordButton = null;
        this.sendCodeButton = null;
        this.verifyEmailButton = null;
        this.confirmDeactivateButton = null;
        this.backButton = null;
    }

    /** 离开子页时清空密码、验证码等敏感输入，服务端会话仍可在有效期内继续使用。 */
    private void clearFormValues(Mode previousMode) {
        switch (previousMode) {
            case HOME -> {
            }
            case CHANGE_PASSWORD -> {
                this.passwordCurrentValue = "";
                this.passwordNewValue = "";
                this.passwordConfirmationValue = "";
            }
            case CHANGE_EMAIL -> {
                this.emailCurrentPasswordValue = "";
                this.newEmailValue = "";
                this.emailCodeValue = "";
            }
            case DEACTIVATE -> this.deactivatePasswordValue = "";
        }
    }

    private void focus(EditBox target) {
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox) editBox.setFocused(editBox == target);
        }
    }

    private void focusFirstField() {
        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox) {
                focus(editBox);
                return;
            }
        }
    }

    private void drawLabel(
            GuiGraphicsExtractor graphics, Font font, String translationKey, int x, int y) {
        String text = Component.translatable(translationKey).getString();
        graphics.text(font, text, x, y + (FIELD_HEIGHT - font.lineHeight) / 2,
                0xFFFFFFFF, false);
    }

    private boolean isMouseOverButton(
            TransparentButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    private int containerTop() {
        return Math.max(8, (this.height - CONTAINER_HEIGHT) / 2);
    }

    private int remainingEmailSessionSeconds() {
        return (int) Math.max(0L,
                (this.emailSessionExpiresAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    private String formatDuration(int seconds) {
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, Math.max(1, maxLength - 3)) + "...";
    }
}
