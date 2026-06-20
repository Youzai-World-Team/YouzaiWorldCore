package top.csituka.youzaiworldcore.client.screen.account;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.network.account.RegisterPayload;

/**
 * 注册 GUI
 */
public class RegisterScreen extends AbstractAccountScreen {

    private EditBox confirmPasswordField;

    private static final String PASSWORD_REQUIREMENTS = "\u5EFA\u8BAE\u5BC6\u7801\u5305\u542B\u82F1\u6587\u5B57\u6BCD\u5927\u5C0F\u5199\u3001\u963F\u62C9\u4F2F\u6570\u5B57\u3001\u82F1\u6587\u6807\u70B9\u7B26\u53F7\u4EE5\u589E\u5F3A\u5B89\u5168\u6027\uFF01";

    public RegisterScreen(String playerName) {
        super("\u73A9\u5BB6\u767B\u5165/\u6CE8\u518C", playerName);
    }

    @Override
    protected void initExtraFields(int centerX, int startY) {
        // 确认密码输入框
        int labelY = startY + 68;

        this.confirmPasswordField = new EditBox(this.font, centerX - WIDGET_WIDTH / 2, labelY + 14, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("\u786E\u8BA4\u5BC6\u7801"));
        this.confirmPasswordField.setMaxLength(128);
        this.extraFields.add(this.confirmPasswordField);
    }

    @Override
    protected String getActionButtonText() {
        return "\u6CE8\u518C";
    }

    @Override
    protected void onActionButtonClick() {
        String password = this.passwordField.getValue().trim();
        String confirmPassword = this.confirmPasswordField != null ? this.confirmPasswordField.getValue().trim() : "";

        if (password.isEmpty()) {
            setStatus("\u8BF7\u8F93\u5165\u5BC6\u7801\uFF01", 0xFFFF5555);
            return;
        }
        if (!password.equals(confirmPassword)) {
            setStatus("\u4E24\u6B21\u8F93\u5165\u7684\u5BC6\u7801\u4E0D\u4E00\u81F4\uFF01", 0xFFFF5555);
            return;
        }

        // 禁用按钮
        this.actionButton.active = false;

        // 发送注册请求
        ClientPlayNetworking.send(new RegisterPayload(password, confirmPassword));

        setStatus("\u6B63\u5728\u6CE8\u518C...", 0xFFAAAAAA);
    }

    @Override
    protected void renderExtraContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, float easedEntry, int containerY, int startY, int alpha) {
        // 绘制"确认密码"标签
        int labelY = startY + 68;
        int dimTextColor = (alpha << 24) | 0xAAAAAA;
        guiGraphics.text(this.font, "\u786E\u8BA4\u5BC6\u7801", this.width / 2 - WIDGET_WIDTH / 2, labelY, dimTextColor, false);

        // 绘制密码要求提示
        if (easedEntry >= 1f) {
            int reqAlpha = 180;
            int reqColor = (reqAlpha << 24) | 0xFFAAAA44;
            int reqY = startY + 132 + (extraFields.size() * 25);
            int reqWidth = this.font.width(PASSWORD_REQUIREMENTS);
            guiGraphics.text(this.font, PASSWORD_REQUIREMENTS, this.width / 2 - reqWidth / 2, reqY, reqColor, false);
        }
    }

    @Override
    protected void onBack() {
        Minecraft.getInstance().setScreenAndShow(null);
    }

    public void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.actionButton.active = true;
    }
}
