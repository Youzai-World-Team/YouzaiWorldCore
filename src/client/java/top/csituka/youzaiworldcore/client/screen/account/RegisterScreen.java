package top.csituka.youzaiworldcore.client.screen.account;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import top.csituka.youzaiworldcore.network.account.RegisterPayload;

/**
 * 注册 GUI 界面。
 * <p>
 * 新玩家首次进入游戏时显示此界面（未注册）。
 * 包含密码输入框、确认密码输入框和"注册"按钮。
 * 注册成功后，服务器会将玩家传送至主世界。
 * </p>
 *
 * <p>与登录界面的区别：</p>
 * <ul>
 *   <li>多一个"确认密码"输入框</li>
 *   <li>按钮文本为"注册"而非"登入"</li>
 *   <li>显示密码强度建议提示文本</li>
 *   <li>注册成功后自动登录并传送至主世界</li>
 * </ul>
 */
public class RegisterScreen extends AbstractAccountScreen {

    /** 确认密码输入框 */
    private EditBox confirmPasswordField;

    /** 密码强度建议提示文本 */
    private static final String PASSWORD_REQUIREMENTS = "建议密码包含英文字母大小写、阿拉伯数字、英文标点符号以增强安全性！";

    /**
     * 构造一个注册界面。
     *
     * @param playerName 当前玩家的玩家代号，显示在界面中
     */
    public RegisterScreen(String playerName) {
        super("玩家登入/注册", playerName);
    }

    @Override
    protected void initExtraFields(int centerX, int startY) {
        // 确认密码输入框布局坐标计算：
        // 密码输入框在 startY + 40（高度 20，底部在 startY + 60）
        // 确认密码标签放在 startY + 68（与密码框底间隔 8px）
        int labelY = startY + 68;

        this.confirmPasswordField = new EditBox(this.font, centerX - WIDGET_WIDTH / 2, labelY + 16, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("确认密码"));
        this.confirmPasswordField.setMaxLength(128);
        this.extraFields.add(this.confirmPasswordField);
        // 注册到 Screen 的子组件列表，以便接收事件
        addRenderableWidget(this.confirmPasswordField);
    }

    @Override
    protected String getActionButtonText() {
        return "注册";
    }

    @Override
    protected void onActionButtonClick() {
        String password = this.passwordField.getValue().trim();
        String confirmPassword = this.confirmPasswordField != null ? this.confirmPasswordField.getValue().trim() : "";

        // 校验：密码非空
        if (password.isEmpty()) {
            setStatus("请输入密码！", 0xFFFF5555);
            return;
        }
        // 校验：两次密码一致
        if (!password.equals(confirmPassword)) {
            setStatus("两次输入的密码不一致！", 0xFFFF5555);
            return;
        }

        // 禁用按钮防止重复点击
        this.actionButton.active = false;

        // 发送注册请求到服务器
        ClientPlayNetworking.send(new RegisterPayload(password, confirmPassword));

        setStatus("正在注册...", 0xFFAAAAAA);
    }

    @Override
    protected void renderExtraContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, float easedEntry, int containerY, int startY, int alpha) {
        // 绘制"确认密码"标签
        int labelY = startY + 68;
        int dimTextColor = (alpha << 24) | 0xAAAAAA;
        guiGraphics.text(this.font, "确认密码", this.width / 2 - WIDGET_WIDTH / 2, labelY, dimTextColor, false);

        // 绘制密码要求提示（仅当淡入完成时显示，避免突兀出现）
        if (easedEntry >= 1f) {
            int reqAlpha = 180;
            int reqColor = (reqAlpha << 24) | 0xFFAAAA44;
            // 提示文本放在返回按钮下方 10px
            int reqY = startY + 220;
            int reqWidth = this.font.width(PASSWORD_REQUIREMENTS);
            guiGraphics.text(this.font, PASSWORD_REQUIREMENTS, this.width / 2 - reqWidth / 2, reqY, reqColor, false);
        }
    }

    @Override
    protected void onBack() {
        Minecraft.getInstance().setScreenAndShow(null);
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
        // 重新启用按钮
        this.actionButton.active = true;
    }
}
