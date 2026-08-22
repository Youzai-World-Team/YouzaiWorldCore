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
import top.csituka.youzaiworldcore.network.RegistrationEmailRequestPayload;
import top.csituka.youzaiworldcore.network.RegistrationEmailStatePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 邮箱注册 GUI：发送验证码并完成账户注册。 */
@SuppressWarnings("null")
public class RegistrationEmailScreen extends Screen {
    private static final int CONTAINER_WIDTH = 360;
    private static final int CONTAINER_HEIGHT = 245;
    private static final int LABEL_WIDTH = 50;
    private static final int FIELD_WIDTH = 190;
    private static final int FIELD_HEIGHT = 20;
    private static final int SEND_BUTTON_WIDTH = 88;
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 24;
    private static final int ROW_SPACING = 36;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final String playerName;
    private final String sessionId;
    private final List<AbstractWidget> allWidgets = new ArrayList<>();

    private EditBox emailField;
    private EditBox codeField;
    private TransparentButton sendCodeButton;
    private TransparentButton verifyButton;
    private TransparentButton disconnectButton;
    private ConfirmationDialog currentDialog;

    private long sessionExpiresAtMillis;
    private long resendAvailableAtMillis;
    private boolean codeSent;
    private boolean processing;
    private boolean expiryHandled;
    private String statusMessage = "";

    public RegistrationEmailScreen(String playerName, String sessionId, int expiresInSeconds) {
        super(Component.translatable("screen.youzaiworldcore.register.title"));
        this.playerName = playerName;
        this.sessionId = sessionId;
        updateSessionExpiry(expiresInSeconds);
    }

    public boolean matchesSession(String candidate) {
        return this.sessionId.equals(candidate);
    }

    /** 应用服务端返回的邮箱注册状态。 */
    public void applyState(RegistrationEmailStatePayload payload) {
        if (!matchesSession(payload.sessionId())) return;
        switch (payload.state()) {
            case REQUIRED -> updateSessionExpiry(payload.expiresInSeconds());
            case CODE_SENT -> {
                this.processing = false;
                this.codeSent = true;
                this.resendAvailableAtMillis = System.currentTimeMillis()
                        + payload.resendAfterSeconds() * 1000L;
                this.statusMessage = Component.translatable(
                        "screen.youzaiworldcore.register_email.status_code_sent").getString();
                if (this.codeField != null) {
                    focus(this.codeField);
                }
            }
            case ERROR -> {
                this.processing = false;
                if (payload.resendAfterSeconds() > 0) {
                    this.resendAvailableAtMillis = System.currentTimeMillis()
                            + payload.resendAfterSeconds() * 1000L;
                }
                showError(payload.message());
            }
            case COMPLETED -> {
                this.processing = false;
                if (Minecraft.getInstance().gui.screen() == this) {
                    Minecraft.getInstance().setScreenAndShow(null);
                }
            }
            case EXPIRED -> showExpired(payload.message());
        }
        updateButtonState();
    }

    @Override
    protected void init() {
        super.init();
        String email = this.emailField == null ? "" : this.emailField.getValue();
        String code = this.codeField == null ? "" : this.codeField.getValue();

        int centerX = this.width / 2;
        int containerTop = (this.height - CONTAINER_HEIGHT) / 2;
        int leftColX = centerX - CONTAINER_WIDTH / 2 + 10;
        int fieldX = leftColX + LABEL_WIDTH + 8;
        int emailY = containerTop + 70;
        int codeY = emailY + ROW_SPACING;

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

        this.sendCodeButton = new TransparentButton(
                fieldX + FIELD_WIDTH + 8, emailY - 2, SEND_BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register_email.button_send_code"),
                this::onSendCodeClick);
        this.sendCodeButton.setTextColor(0xFFFFFF);

        int buttonY = containerTop + 165;
        int totalButtonWidth = BUTTON_WIDTH * 2 + 12;
        int buttonStartX = centerX - totalButtonWidth / 2;
        this.verifyButton = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register_email.button_verify"),
                this::onVerifyClick);
        this.verifyButton.setTextColor(0xFFFFFF);

        this.disconnectButton = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + 12, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.youzaiworldcore.register.button_disconnect"),
                this::onDisconnectClick);
        this.disconnectButton.setTextColor(0xFFFFFF);

        this.allWidgets.clear();
        this.allWidgets.add(this.emailField);
        this.allWidgets.add(this.codeField);
        this.allWidgets.add(this.sendCodeButton);
        this.allWidgets.add(this.verifyButton);
        this.allWidgets.add(this.disconnectButton);

        focus(this.codeSent ? this.codeField : this.emailField);
        if (this.currentDialog != null) this.currentDialog.init(this.width, this.height);
        updateButtonState();
    }

    @Override
    public void tick() {
        super.tick();
        updateButtonState();
        if (!this.expiryHandled && System.currentTimeMillis() >= this.sessionExpiresAtMillis) {
            showExpired(Component.translatable(
                    "screen.youzaiworldcore.register_email.session_expired").getString());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        updateButtonState();
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        int centerX = this.width / 2;
        int containerTop = (this.height - CONTAINER_HEIGHT) / 2;
        int leftColX = centerX - CONTAINER_WIDTH / 2 + 10;
        int emailY = containerTop + 70;
        int codeY = emailY + ROW_SPACING;

        String titleText = Component.translatable("screen.youzaiworldcore.register.title").getString();
        float titleScale = 1.3f;
        int titleWidth = (int) (this.font.width(titleText) * titleScale);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        guiGraphics.text(this.font, titleText,
                (int) ((centerX - titleWidth / 2) / titleScale),
                (int) ((containerTop + 8) / titleScale), 0xFFFFFFFF, false);
        guiGraphics.pose().popMatrix();

        String subtitle = Component.translatable(
                "screen.youzaiworldcore.register_email.subtitle").getString();
        guiGraphics.text(this.font, subtitle, centerX - this.font.width(subtitle) / 2,
                containerTop + 33, 0xFFCCCCCC, false);

        String remaining = Component.translatable(
                "screen.youzaiworldcore.register_email.session_remaining",
                formatDuration(remainingSessionSeconds())).getString();
        int remainingColor = remainingSessionSeconds() <= 60 ? 0xFFFF8080 : 0xFFAAAAAA;
        guiGraphics.text(this.font, remaining, centerX - this.font.width(remaining) / 2,
                containerTop + 48, remainingColor, false);

        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.register_email.label_email").getString(),
                leftColX, emailY + 3);
        drawLabel(guiGraphics, this.font,
                Component.translatable("screen.youzaiworldcore.register_email.label_code").getString(),
                leftColX, codeY + 3);

        if (!this.statusMessage.isBlank()) {
            guiGraphics.text(this.font, this.statusMessage,
                    centerX - this.font.width(this.statusMessage) / 2,
                    codeY + 27, 0xFF80E080, false);
        }

        String hint = Component.translatable("screen.youzaiworldcore.register_email.hint_line").getString();
        guiGraphics.text(this.font, hint, centerX - this.font.width(hint) / 2,
                containerTop + 205, 0xFFAAAAAA, false);

        for (AbstractWidget widget : this.allWidgets) {
            if (widget instanceof EditBox editBox) {
                editBox.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
            } else if (widget instanceof TransparentButton button) {
                button.render(guiGraphics, mouseX, mouseY, partialTick);
            }
        }

        if (this.currentDialog != null && this.currentDialog.isVisible()) {
            this.currentDialog.render(guiGraphics, this.width, this.height);
            this.currentDialog.renderButtons(guiGraphics, mouseX, mouseY, partialTick);
        } else if (this.currentDialog != null) {
            this.currentDialog = null;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (this.currentDialog != null && this.currentDialog.isFullyVisible()) {
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

        double mouseX = event.x();
        double mouseY = event.y();
        if (this.sendCodeButton.active && isMouseOverButton(this.sendCodeButton, mouseX, mouseY)) {
            this.sendCodeButton.onClick(event, isActuallyClick);
            return true;
        }
        if (this.verifyButton.active && isMouseOverButton(this.verifyButton, mouseX, mouseY)) {
            this.verifyButton.onClick(event, isActuallyClick);
            return true;
        }
        if (this.disconnectButton.active && isMouseOverButton(this.disconnectButton, mouseX, mouseY)) {
            this.disconnectButton.onClick(event, isActuallyClick);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (this.currentDialog != null && this.currentDialog.isFullyVisible()) return true;
        if (keyEvent.key() == 256) return true;
        if (keyEvent.key() == 257 || keyEvent.key() == 335) {
            if (this.codeField.isFocused() || !this.codeField.getValue().isBlank()) {
                onVerifyClick();
            } else {
                onSendCodeClick();
            }
            return true;
        }
        if (this.emailField.isFocused() && this.emailField.keyPressed(keyEvent)) return true;
        if (this.codeField.isFocused() && this.codeField.keyPressed(keyEvent)) return true;
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent charEvent) {
        if (this.emailField.isFocused() && this.emailField.charTyped(charEvent)) return true;
        if (this.codeField.isFocused() && this.codeField.charTyped(charEvent)) return true;
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

    private void onSendCodeClick() {
        if (this.processing || System.currentTimeMillis() < this.resendAvailableAtMillis) return;
        String email = this.emailField.getValue().trim();
        if (email.length() > 254 || !EMAIL_PATTERN.matcher(email).matches()) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register_email.error_invalid_email").getString());
            return;
        }
        this.processing = true;
        send(new RegistrationEmailRequestPayload(
                RegistrationEmailRequestPayload.Action.SEND_CODE, this.sessionId, email));
        updateButtonState();
    }

    private void onVerifyClick() {
        if (this.processing) return;
        String code = this.codeField.getValue().trim();
        if (!code.matches("\\d{6}")) {
            showError(Component.translatable(
                    "screen.youzaiworldcore.register_email.error_invalid_code").getString());
            return;
        }
        this.processing = true;
        send(new RegistrationEmailRequestPayload(
                RegistrationEmailRequestPayload.Action.VERIFY_CODE, this.sessionId, code));
        updateButtonState();
    }

    private void onDisconnectClick() {
        if (this.processing) return;
        Minecraft.getInstance().disconnectFromWorld(
                Component.translatable("screen.youzaiworldcore.register.disconnect_message"));
    }

    private void send(RegistrationEmailRequestPayload payload) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            ClientPlayNetworking.send(payload);
        } else {
            this.processing = false;
        }
    }

    private void showError(String message) {
        this.processing = false;
        this.currentDialog = new ConfirmationDialog(
                Component.translatable("screen.youzaiworldcore.register.error_title").getString(),
                new String[]{message},
                Component.translatable("screen.youzaiworldcore.register.dialog_ok").getString(),
                () -> focus(this.codeSent ? this.codeField : this.emailField));
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void showExpired(String message) {
        if (this.expiryHandled) return;
        this.expiryHandled = true;
        this.processing = false;
        this.currentDialog = new ConfirmationDialog(
                Component.translatable("screen.youzaiworldcore.register.error_title").getString(),
                new String[]{message == null || message.isBlank()
                        ? Component.translatable(
                                "screen.youzaiworldcore.register_email.session_expired").getString()
                        : message},
                Component.translatable("screen.youzaiworldcore.register.dialog_ok").getString(),
                () -> Minecraft.getInstance().setScreenAndShow(new RegisterScreen(this.playerName)));
        this.currentDialog.init(this.width, this.height);
        this.currentDialog.show();
    }

    private void updateSessionExpiry(int expiresInSeconds) {
        int safeSeconds = Math.max(1, Math.min(86_400, expiresInSeconds));
        this.sessionExpiresAtMillis = System.currentTimeMillis() + safeSeconds * 1000L;
    }

    private void updateButtonState() {
        if (this.sendCodeButton == null || this.verifyButton == null || this.disconnectButton == null) return;
        long now = System.currentTimeMillis();
        int resendSeconds = (int) Math.max(0L,
                (this.resendAvailableAtMillis - now + 999L) / 1000L);
        if (resendSeconds > 0) {
            this.sendCodeButton.setMessage(Component.translatable(
                    "screen.youzaiworldcore.register_email.button_resend_countdown", resendSeconds));
        } else {
            this.sendCodeButton.setMessage(Component.translatable(codeSent
                    ? "screen.youzaiworldcore.register_email.button_resend_code"
                    : "screen.youzaiworldcore.register_email.button_send_code"));
        }
        boolean sessionActive = now < this.sessionExpiresAtMillis && !this.expiryHandled;
        this.sendCodeButton.active = sessionActive && !this.processing && resendSeconds == 0;
        this.verifyButton.active = sessionActive && !this.processing;
        this.disconnectButton.active = !this.processing;
    }

    private int remainingSessionSeconds() {
        return (int) Math.max(0L,
                (this.sessionExpiresAtMillis - System.currentTimeMillis() + 999L) / 1000L);
    }

    private String formatDuration(int seconds) {
        int minutes = seconds / 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
    }

    private void focus(EditBox target) {
        if (this.emailField != null) this.emailField.setFocused(target == this.emailField);
        if (this.codeField != null) this.codeField.setFocused(target == this.codeField);
    }

    private void drawLabel(GuiGraphicsExtractor guiGraphics, Font font, String text, int x, int y) {
        guiGraphics.text(font, text, x, y + (FIELD_HEIGHT - font.lineHeight) / 2,
                0xFFFFFFFF, false);
    }

    private boolean isMouseOverButton(TransparentButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }
}
