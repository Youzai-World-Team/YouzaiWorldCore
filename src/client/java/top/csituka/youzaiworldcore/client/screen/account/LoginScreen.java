package top.csituka.youzaiworldcore.client.screen.account;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.network.account.LoginPayload;

/**
 * 登入 GUI
 */
public class LoginScreen extends AbstractAccountScreen {

    public LoginScreen(String playerName) {
        super("\u73A9\u5BB6\u767B\u5165/\u6CE8\u518C", playerName);
    }

    @Override
    protected void initExtraFields(int centerX, int startY) {
        // 登入界面只有密码输入框，无需额外字段
    }

    @Override
    protected String getActionButtonText() {
        return "\u767B\u5165";
    }

    @Override
    protected void onActionButtonClick() {
        String password = this.passwordField.getValue().trim();
        if (password.isEmpty()) {
            setStatus("\u8BF7\u8F93\u5165\u5BC6\u7801\uFF01", 0xFFFF5555);
            return;
        }

        // 禁用按钮防止重复点击
        this.actionButton.active = false;

        // 发送登录请求到服务器
        ClientPlayNetworking.send(new LoginPayload(password));

        // 显示"正在登录..."
        setStatus("\u6B63\u5728\u767B\u5F55...", 0xFFAAAAAA);
    }

    public void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        this.actionButton.active = true;
    }

    @Override
    protected void onBack() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
