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
 * 登入/注册 GUI 基类
 * 提供共同布局：标题、欢迎文本、玩家代号显示、密码输入框、按钮、状态信息区域
 */
public abstract class AbstractAccountScreen extends Screen {

    protected static final int GUI_WIDTH = 280;
    protected static final int GUI_HEIGHT = 220;
    protected static final int WIDGET_WIDTH = 200;
    protected static final int WIDGET_HEIGHT = 20;

    protected final String playerName;

    protected EditBox passwordField;
    protected List<EditBox> extraFields = new ArrayList<>();

    protected TransparentButton actionButton;
    protected TransparentButton backButton;
    protected String statusMessage = "";
    protected int statusColor = 0xFFFF5555;

    // 淡入动画
    private float entryProgress = 0f;
    private long entryStartTime = 0;
    private static final float ENTRY_DURATION = 0.3f;

    protected AbstractAccountScreen(@NonNull String title, String playerName) {
        super(Component.literal(title));
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        super.init();
        this.entryProgress = 0f;
        this.entryStartTime = System.currentTimeMillis();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        // 密码输入框
        this.passwordField = new EditBox(this.font, centerX - WIDGET_WIDTH / 2, startY + 40, WIDGET_WIDTH, WIDGET_HEIGHT, Component.literal("\u5BC6\u7801"));
        this.passwordField.setMaxLength(128);
        // 26.2 版本 EditBox 没有 setFilter 方法，移除

        // 子类可以添加额外输入框
        initExtraFields(centerX, startY);

        // 确定按钮（子类设置文本和回调）
        int buttonY = startY + 70 + (extraFields.size() * 25);
        this.actionButton = new TransparentButton(
                centerX - 60, buttonY, 120, 25,
                Component.literal(getActionButtonText()),
                this::onActionButtonClick
        );

        // 返回按钮
        this.backButton = new TransparentButton(
                centerX - 60, buttonY + 32, 120, 25,
                Component.literal("\u8FD4\u56DE"),
                () -> onBack()
        );
    }

    /** 子类在此添加额外的输入框 */
    protected void initExtraFields(int centerX, int startY) {
    }

    /** 确定按钮的文本 */
    protected abstract String getActionButtonText();

    /** 确定按钮点击回调 */
    protected abstract void onActionButtonClick();

    /** 返回按钮回调 */
    protected void onBack() {
        onClose();
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 淡入动画
        if (entryProgress < 1f) {
            long elapsed = System.currentTimeMillis() - entryStartTime;
            entryProgress = Math.min(1f, elapsed / (ENTRY_DURATION * 1000f));
        }
        float easedEntry = easeOutCubic(entryProgress);

        // 半透明黑色背景
        int bgAlpha = (int) (easedEntry * 180);
        guiGraphics.fill(0, 0, this.width, this.height, (bgAlpha << 24));

        // 绘制主容器（圆角矩形背景）
        int containerX = this.width / 2 - GUI_WIDTH / 2;
        int containerY = this.height / 2 - GUI_HEIGHT / 2;
        int containerAlpha = (int) (easedEntry * 200);
        fillRoundedRect(guiGraphics, containerX, containerY, GUI_WIDTH, GUI_HEIGHT, 8, (containerAlpha << 24) | 0x1A1A1A);

        int alpha = (int) (easedEntry * 255);
        int textColor = (alpha << 24) | 0xFFFFFF;
        int dimTextColor = (alpha << 24) | 0xAAAAAA;

        // 标题
        String titleText = "\u73A9\u5BB6\u767B\u5165/\u6CE8\u518C";
        int titleWidth = this.font.width(titleText);
        guiGraphics.text(this.font, titleText, this.width / 2 - titleWidth / 2, containerY + 12, textColor, false);

        // 欢迎文本
        String welcomeText = "\u6B22\u8FCE\u6765\u5230\u60A0\u54C9\u4E16\u754C\u670D\u52A1\u5668\uFF0C\u8BF7\u767B\u5165/\u6CE8\u518C\u4F60\u7684\u8D26\u6237\uFF1A";
        int welcomeWidth = this.font.width(welcomeText);
        guiGraphics.text(this.font, welcomeText, this.width / 2 - welcomeWidth / 2, containerY + 30, dimTextColor, false);

        int centerX = this.width / 2;
        int startY = containerY + 50;

        // 玩家代号
        String nameLabel = "\u73A9\u5BB6\u4EE3\u53F7\uFF1A " + playerName;
        guiGraphics.text(this.font, nameLabel, centerX - this.font.width(nameLabel) / 2, startY + 2, textColor, false);

        // 密码标签
        guiGraphics.text(this.font, "\u5BC6\u7801", centerX - WIDGET_WIDTH / 2, startY + 24, dimTextColor, false);

        // 渲染密码输入框 - 使用 extractRenderState (public final, 继承自 AbstractWidget)
        this.passwordField.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // 额外输入框
        for (EditBox field : extraFields) {
            field.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
        }

        // 由子类绘制额外内容（如密码要求提示）
        renderExtraContent(guiGraphics, mouseX, mouseY, partialTick, easedEntry, containerY, startY, alpha);

        // 状态信息
        if (!statusMessage.isEmpty()) {
            int statusAlpha = (int) (alpha * 0.9f);
            int statusColorWithAlpha = (statusAlpha << 24) | (statusColor & 0x00FFFFFF);
            int statusWidth = this.font.width(statusMessage);
            guiGraphics.text(this.font, statusMessage, this.width / 2 - statusWidth / 2, startY + 105 + (extraFields.size() * 25), statusColorWithAlpha, false);
        }

        // 按钮渲染 - 使用 render 方法（遵循 MenuScreen 中的调用方式）
        this.actionButton.render(guiGraphics, mouseX, mouseY, partialTick);
        this.backButton.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /** 子类可在此方法中绘制额外内容（如密码要求提示） */
    protected void renderExtraContent(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick, float easedEntry, int containerY, int startY, int alpha) {
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean isActuallyClick) {
        // 先检查输入框
        this.passwordField.mouseClicked(event, isActuallyClick);
        for (EditBox field : extraFields) {
            field.mouseClicked(event, isActuallyClick);
        }

        // 检查按钮
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
        if (keyEvent.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyEvent.key() == InputConstants.KEY_TAB) {
            // Tab 切换焦点
            List<EditBox> allFields = new ArrayList<>(extraFields);
            allFields.add(0, this.passwordField);
            // 找到当前聚焦的输入框，切换到下一个
            for (int i = 0; i < allFields.size(); i++) {
                if (allFields.get(i).isFocused()) {
                    int nextIdx = (i + 1) % allFields.size();
                    allFields.get(i).setFocused(false);
                    allFields.get(nextIdx).setFocused(true);
                    return true;
                }
            }
            // 没有聚焦的，聚焦第一个
            if (!allFields.isEmpty()) {
                allFields.get(0).setFocused(true);
            }
            return true;
        }
        if (keyEvent.key() == InputConstants.KEY_RETURN || keyEvent.key() == InputConstants.KEY_NUMPADENTER) {
            onActionButtonClick();
            return true;
        }

        // 将键盘事件发送给输入框
        if (this.passwordField.isFocused()) {
            this.passwordField.keyPressed(keyEvent);
            return true;
        }
        for (EditBox field : extraFields) {
            if (field.isFocused()) {
                field.keyPressed(keyEvent);
                return true;
            }
        }
        return false;
    }

    @Override
    public void tick() {
        // EditBox 在 26.2 中不再有 tick() 方法，无需手动 tick
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 不绘制默认背景
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected boolean isInBounds(int mx, int my, TransparentButton button) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    protected void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // Body center
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // Corners
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

    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}
