package top.csituka.youzaiworldcore.client.screen.account;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.network.account.LoginPayload;

/**
 * 登入 GUI 界面（独立页面）。
 * <p>
 * 已注册玩家进入游戏时显示此界面，仅包含登入相关的表单元素。
 * 功能特性：
 * <ul>
 *   <li>显示当前玩家代号</li>
 *   <li>密码输入框（隐藏输入）</li>
 *   <li>"登入"确定按钮</li>
 *   <li>状态信息显示区域（密码错误等提示）</li>
 *   <li>"回到服务器列表"断开连接按钮</li>
 * </ul>
 * </p>
 *
 * <p>密码通过 {@link LoginPayload} 发送至服务端进行 SHA-256 哈希验证。</p>
 */
public class LoginScreen extends AbstractAccountScreen {

    /**
     * 构造一个登入界面。
     *
     * @param playerName 当前玩家的玩家代号，显示在界面中
     */
    public LoginScreen(String playerName) {
        super("玩家登入", playerName, 290);
    }

    @Override
    protected String getTitleText() {
        return "玩家登入";
    }

    @Override
    protected String getWelcomeText() {
        return "欢迎来到悠哉世界服务器，请登入你的账户：";
    }

    @Override
    protected String getActionButtonText() {
        return "登入";
    }

    @Override
    protected void onActionButtonClick() {
        String password = this.passwordField.getValue().trim();
        if (password.isEmpty()) {
            setStatus("请输入密码！", 0xFFFF5555);
            return;
        }

        // 禁用按钮防止重复点击
        this.actionButton.active = false;

        // 发送登录请求到服务器
        ClientPlayNetworking.send(new LoginPayload(password));

        // 显示"正在登录..."状态
        setStatus("正在登录...", 0xFFAAAAAA);
    }

    /**
     * 设置界面状态信息（由客户端网络回调或基类调用）。
     *
     * @param message 状态文本
     * @param color   文本颜色（ARGB）
     */
    public void setStatus(String message, int color) {
        this.statusMessage = message;
        this.statusColor = color;
        // 重新启用按钮（由于网络调用是异步的，完成后需要启用按钮）
        this.actionButton.active = true;
    }
}
