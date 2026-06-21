package top.csituka.youzaiworldcore.client.screen.account;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 登入/注册 GUI 抽象基类。
 * <p>
 * 提供所有账户操作界面共用的布局和交互功能：</p>
 * <ul>
 *   <li>半透明背景 + 居中圆角容器</li>
 *   <li>"玩家登入/注册" 标题</li>
 *   <li>"欢迎来到悠哉世界服务器..." 欢迎文本</li>
 *   <li>当前玩家代号显示</li>
 *   <li>密码输入框（及子类可扩展的额外输入框）</li>
 *   <li>确定操作按钮（文本由子类指定）+ 返回按钮</li>
 *   <li>状态信息显示区域</li>
 *   <li>淡入动画效果（easeOutCubic）</li>
 *   <li>Tab 键切换焦点、Enter 键提交、Escape 键关闭</li>
 * </ul>
 */
public abstract class AbstractAccountScreen extends Screen {

    /** GUI 容器总宽度 */
    protected static final int GUI_WIDTH = 280;

    /** GUI 容器总高度 */
    protected static final int GUI_HEIGHT = 290;

    /** 输入框/按钮的标准宽度 */
    protected static final int WIDGET_WIDTH = 200;

    /** 输入框/按钮的标准高度 */
    protected static final int WIDGET_HEIGHT = 20;

    /** 当前玩家的玩家代号（显示在界面中） */
    protected final String playerName;

    /** 密码输入框 */
    protected EditBox passwordField;

    /** 子类额外添加的输入框列表（如注册界面的"确认密码"） */
    protected List<EditBox> extraFields = new ArrayList<>();

    /** 确定操作按钮（"登入"或"注册"） */
    protected TransparentButton actionButton;

    /** 返回按钮 */
    protected TransparentButton backButton;

    /** 状态信息文本（如"密码错误""登录成功"等） */
    protected String statusMessage = "";

    /** 状态信息颜色（默认红色 0xFFFF5555） */
    protected int statusColor = 0xFFFF5555;

    // ===== 淡入动画 =====

    /** 当前动画进度（0.0 ~ 1.0） */
    private float entryProgress = 0f;

    /** 动画开始的时间戳（毫秒） */
    private long entryStartTime = 0;

    /** 动画持续时间（秒） */
    private static final float ENTRY_DURATION = 0.3f;

    /**
     * 构造一个抽象账户界面。
     *
     * @param title      界面标题（用于 Screen 构造）
     * @param playerName 当前玩家的玩家代号
     */
    protected AbstractAccountScreen(@NonNull String title, String playerName) {
        super(Component.literal(title));
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        super.init();
        // 重置淡入动画
        this.entryProgress = 0f;
        this.entryStartTime = System.currentTimeMillis();

        int centerX = this.width / 2;
        // 容器居中，startY 相对于容器顶部
        int containerY = (this.height - GUI_HEIGHT) / 2;
        int startY = containerY + 50;

        // ===== 密码输入框 =====
        this.passwordField = new EditBox(this.font, centerX - WIDGET_WIDTH / 2, startY + 40, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("密码"));
        this.passwordField.setMaxLength(128);
        // 注册到 Screen 的子组件列表，否则无法接收鼠标/键盘/字符事件
        addRenderableWidget(this.passwordField);

        // 子类可以添加额外输入框（如确认密码框）
        initExtraFields(centerX, startY);

        // ===== 确定按钮 =====
        // 每个额外输入框增加 45px 垂直间距以保证不重叠
        int buttonY = startY + 70 + (extraFields.size() * 45);
        this.actionButton = new TransparentButton(
                centerX - 60, buttonY, 120, 25,
                Component.literal(getActionButtonText()),
                this::onActionButtonClick
        );
        addRenderableWidget(this.actionButton);

        // ===== 返回按钮 =====
        this.backButton = new TransparentButton(
                centerX - 60, buttonY + 32, 120, 25,
                Component.literal("返回"),
                () -> onBack()
        );
        addRenderableWidget(this.backButton);
    }

    /** 子类在此添加额外的输入框（如确认密码框） */
    protected void initExtraFields(int centerX, int startY) {
    }

    /** 子类实现：返回确定按钮上显示的文本（"登入"或"注册"） */
    protected abstract String getActionButtonText();

    /** 子类实现：确定按钮点击时的回调逻辑 */
    protected abstract void onActionButtonClick();

    /** 返回按钮回调：默认关闭界面 */
    protected void onBack() {
        onClose();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // ===== 淡入动画 =====
        if (entryProgress < 1f) {
            long elapsed = System.currentTimeMillis() - entryStartTime;
            entryProgress = Math.min(1f, elapsed / (ENTRY_DURATION * 1000f));
        }
        float easedEntry = easeOutCubic(entryProgress);

        // ===== 半透明黑色背景覆盖整个屏幕 =====
        int bgAlpha = (int) (easedEntry * 180);
        guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        // ===== 绘制圆角矩形主容器 =====
        int containerX = this.width / 2 - GUI_WIDTH / 2;
        int containerY = this.height / 2 - GUI_HEIGHT / 2;
        int containerAlpha = (int) (easedEntry * 200);
        fillRoundedRect(guiGraphics, containerX, containerY, GUI_WIDTH, GUI_HEIGHT, 8, (containerAlpha << 24) | 0x1A1A1A);

        // 计算当前透明度下的文本颜色
        int alpha = (int) (easedEntry * 255);
        int textColor = (alpha << 24) | 0xFFFFFF;
        int dimTextColor = (alpha << 24) | 0xAAAAAA;

        // ===== 标题：玩家登入/注册 =====
        String titleText = "玩家登入/注册";
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(this.font, titleText, this.width / 2 - titleWidth / 2, containerY + 12, textColor, false);

        // ===== 欢迎文本 =====
        String welcomeText = "欢迎来到悠哉世界服务器，请登入/注册你的账户：";
        int welcomeWidth = this.font.width(welcomeText);
        guiGraphics.text(this.font, welcomeText, this.width / 2 - welcomeWidth / 2, containerY + 30, dimTextColor, false);

        int centerX = this.width / 2;
        int startY = containerY + 50;

        // ===== 玩家代号显示 =====
        String nameLabel = "玩家代号： " + playerName;
        guiGraphics.text(this.font, nameLabel, centerX - this.font.width(nameLabel) / 2, startY + 2, textColor, false);

        // ===== 密码标签 =====
        guiGraphics.text(this.font, "密码", centerX - WIDGET_WIDTH / 2, startY + 24, dimTextColor, false);

        // ===== 渲染密码输入框 =====
        this.passwordField.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // ===== 渲染额外输入框（确认密码等） =====
        for (EditBox field : extraFields) {
            field.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        // ===== 子类额外绘制内容（如密码要求提示） =====
        renderExtraContent(guiGraphics, mouseX, mouseY, partialTick, easedEntry, containerY, startY, alpha);

        // ===== 状态信息显示 =====
        if (!statusMessage.isEmpty()) {
            int statusAlpha = (int) (alpha * 0.9f);
            int statusColorWithAlpha = (statusAlpha << 24) | (statusColor & 0x00FFFFFF);
            int statusWidth = this.font.width(statusMessage);
            // 状态信息位置：根据额外输入框数量动态调整 Y 偏移
            guiGraphics.text(this.font, statusMessage, this.width / 2 - statusWidth / 2,
                    startY + 135 + (extraFields.size() * 45), statusColorWithAlpha, false);
        }

        // ===== 渲染按钮 =====
        this.actionButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** 子类可在此方法中绘制额外内容（如注册界面的密码要求提示） */
    protected void renderExtraContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, float easedEntry, int containerY, int startY, int alpha) {
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isActuallyClick) {
        // 先让 super 处理子组件（输入框）的点击和焦点切换
        if (super.mouseClicked(event, isActuallyClick)) {
            return true;
        }

        // 手动检测按钮点击（因为 TransparentButton 在 26.2 中 extractWidgetRenderState
        // 不是 public 的，改用 render 方式，所以点击检测需要自行实现）
        int mx = (int) event.x();
        int my = (int) event.y();
        if (isInBounds(mx, my, this.actionButton)) {
            this.actionButton.onClick(event, isActuallyClick);
            return true;
        }
        if (isInBounds(mx, my, this.backButton)) {
            this.backButton.onClick(event, isActuallyClick);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent keyEvent) {
        // 让 super 先处理（转发给聚焦的 EditBox）
        if (super.keyPressed(keyEvent)) {
            return true;
        }

        // Escape 键关闭界面
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        // Tab 键在输入框之间切换焦点
        if (keyEvent.key() == InputConstants.KEY_TAB) {
            List<EditBox> allFields = new ArrayList<>(extraFields);
            allFields.add(0, this.passwordField);
            for (int i = 0; i < allFields.size(); i++) {
                if (allFields.get(i).isFocused()) {
                    int nextIdx = (i + 1) % allFields.size();
                    allFields.get(i).setFocused(false);
                    allFields.get(nextIdx).setFocused(true);
                    return true;
                }
            }
            // 没有已聚焦的输入框，聚焦到第一个
            if (!allFields.isEmpty()) {
                allFields.get(0).setFocused(true);
            }
            return true;
        }
        // Enter 键触发确定按钮
        if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
            onActionButtonClick();
            return true;
        }

        return false;
    }

    @Override
    public void tick() {
        // 26.2 版本 EditBox 已不再需要手动 tick，此方法留空即可
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制默认背景（我们使用自定义半透明背景）
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }

    /**
     * 检测鼠标位置是否在按钮的矩形范围内。
     *
     * @param mx     鼠标 X 坐标
     * @param my     鼠标 Y 坐标
     * @param button 目标按钮
     * @return true 如果鼠标在按钮范围内
     */
    protected boolean isInBounds(int mx, int my, TransparentButton button) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    /**
     * 绘制圆角矩形填充。
     * 使用逐像素绘制四角圆角的方式实现圆角效果。
     *
     * @param g     GuiGraphicsExtractor 实例
     * @param x     矩形左上角 X
     * @param y     矩形左上角 Y
     * @param w     矩形宽度
     * @param h     矩形高度
     * @param r     圆角半径（像素）
     * @param color 填充颜色 ARGB
     */
    protected void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // Body center
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // 四角圆角像素
        for (int i = 0; i < r; i++) {
            for (int j = 0; j < r; j++) {
                int dx = r - 1 - i;
                int dy = r - 1 - j;
                if (dx * dx + dy * dy < r * r) {
                    g.fill(x + i, y + j, x + i + 1, y + j + 1, color);
                    g.fill(x + w - 1 - i, y + j, x + w - i, y + j + 1, color);
                    g.fill(x + i, y + h - 1 - j, x + i + 1, y + h - j, color);
                    g.fill(x + w - 1 - i, y + h - 1 - j, x + w - i, y + h - j, color);
                }
            }
        }
    }

    /** easeOutCubic 缓动函数，用于淡入动画 */
    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}
