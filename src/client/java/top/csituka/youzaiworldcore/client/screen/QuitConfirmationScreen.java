package top.csituka.youzaiworldcore.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;

/**
 * 退出确认屏幕。
 * <p>
 * 当玩家点击标题屏幕的"退出游戏"按钮或窗口右上角关闭按钮时显示，
 * 询问玩家是否确定要退出游戏。包含"确定退出"和"取消"两个选项。
 * <p>
 * 对话框样式与 {@code ConfirmationDialog} 保持一致：
 * 白色圆角矩形背景 + 黑色文本 + TransparentButton 按钮。
 * <p>
 * 动画：
 * <ul>
 *   <li>淡入：200ms，easeOutCubic</li>
 *   <li>淡出（取消）：200ms，easeOutCubic，动画完成后关闭屏幕</li>
 *   <li>淡出期间鼠标/键盘事件被屏蔽</li>
 * </ul>
 */
@SuppressWarnings("null")
public class QuitConfirmationScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/QuitConfirmationScreen");

    // ============ 布局常量 ============
    private static final int DIALOG_WIDTH = 200;
    private static final int DIALOG_HEIGHT = 120;
    private static final int CORNER_RADIUS = 6;
    private static final int BUTTON_WIDTH = 80;
    private static final int BUTTON_HEIGHT = 24;
    private static final int BUTTON_SPACING = 10;

    // ============ 动画常量 ============
    private static final float FADE_DURATION_MS = 200f;

    /** 标记是否已确认退出，用于 {@link top.csituka.youzaiworldcore.mixin.client.MinecraftQuitMixin} 判断 */
    public static boolean quitConfirmed = false;

    private TransparentButton confirmButton;
    private TransparentButton cancelButton;

    public QuitConfirmationScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.quit_title"));
    }

    // ============ 动画状态 ============
    private long entryStartTime = -1;
    private float entryProgress = 1f;          // 0→1 淡入，初始为 1（立即显示）

    private boolean exiting = false;
    private long exitStartTime = -1;
    private float exitProgress = 0f;           // 0→1 淡出
    private Runnable onExitComplete;

    @Override
    protected void init() {
        super.init();
        LOGGER.debug("Initializing QuitConfirmationScreen");

        // 重置确认标志（新打开的对话框尚未确认）
        quitConfirmed = false;

        // 启动淡入动画
        entering = true;
        entryStartTime = System.currentTimeMillis();
        entryProgress = 0f;

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        int buttonY = dialogY + DIALOG_HEIGHT - BUTTON_HEIGHT - 15;
        int totalButtonWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING;
        int buttonStartX = dialogX + (DIALOG_WIDTH - totalButtonWidth) / 2;

        // "确定退出" 按钮
        this.confirmButton = new TransparentButton(
                buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal(I18n.get("youzaiworldcore.message.gui.quit_confirm")),
                this::onConfirmQuit
        );
        this.confirmButton.setTextColor(0x000000);

        // "取消" 按钮
        this.cancelButton = new TransparentButton(
                buttonStartX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal(I18n.get("youzaiworldcore.message.gui.quit_cancel")),
                this::onCancelQuit
        );
        this.cancelButton.setTextColor(0x000000);
    }

    // ============ 淡入状态 ============

    private boolean entering = false;

    // ============ 渲染 ============

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 更新动画进度
        if (entering && entryStartTime != -1) {
            long elapsed = System.currentTimeMillis() - entryStartTime;
            entryProgress = Math.min(1f, elapsed / FADE_DURATION_MS);
            if (entryProgress >= 1f) {
                entering = false;
                entryStartTime = -1;
            }
        }

        if (exiting && exitStartTime != -1) {
            long elapsed = System.currentTimeMillis() - exitStartTime;
            exitProgress = Math.min(1f, elapsed / FADE_DURATION_MS);
        }

        // 计算当前 alpha（淡入用 entryProgress，淡出用 1 - exitProgress）
        float alpha = exiting
                ? 1f - easeOutCubic(exitProgress)
                : easeOutCubic(entryProgress);

        // 如果淡出完成，执行回调
        if (exiting && exitProgress >= 1f) {
            Runnable callback = onExitComplete;
            exiting = false;
            exitProgress = 0f;
            onExitComplete = null;
            if (callback != null) {
                callback.run();
            }
            return;
        }

        // 绘制全屏半透明黑色遮罩（alpha 驱动）
        int overlayAlpha = (int) (0x50 * alpha);
        guiGraphics.fill(0, 0, this.width, this.height, (overlayAlpha << 24));

        int dialogX = (this.width - DIALOG_WIDTH) / 2;
        int dialogY = (this.height - DIALOG_HEIGHT) / 2;

        // 绘制白色圆角对话框背景（alpha 驱动透明度）
        int bgAlpha = (int) (0xBF * alpha);
        int bgColor = (bgAlpha << 24) | 0xFFFFFF;
        fillRoundedRect(guiGraphics, dialogX, dialogY, DIALOG_WIDTH, DIALOG_HEIGHT, CORNER_RADIUS, bgColor);

        // 文本透明度
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0x000000;
        int titleColor = (textAlpha << 24) | 0x000000;

        // 绘制标题
        String title = Component.translatable("youzaiworldcore.message.gui.quit_title").getString();
        float titleScale = 1.2f;
        int titleWidth = (int) (this.font.width(title) * titleScale);
        int titleX = dialogX + (DIALOG_WIDTH - titleWidth) / 2;
        int titleY = dialogY + 15;

        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(titleScale, titleScale);
        guiGraphics.text(this.font, title,
                (int) (titleX / titleScale),
                (int) (titleY / titleScale),
                titleColor, false);
        guiGraphics.pose().popMatrix();

        // 绘制消息
        String message = I18n.get("youzaiworldcore.message.gui.quit_message");
        int msgWidth = this.font.width(message);
        guiGraphics.text(this.font, message,
                dialogX + (DIALOG_WIDTH - msgWidth) / 2,
                dialogY + 45,
                textColor, false);

        // 按钮透明度同步
        if (this.confirmButton != null) {
            this.confirmButton.setExternalAlpha(alpha);
        }
        if (this.cancelButton != null) {
            this.cancelButton.setExternalAlpha(alpha);
        }

        // 渲染按钮
        if (this.confirmButton != null) {
            this.confirmButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
        if (this.cancelButton != null) {
            this.cancelButton.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    // ============ 输入事件（淡出进行中时屏蔽交互） ============

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (exiting) return true; // 淡出期间屏蔽点击
        if (entering && entryProgress < 1f) return true; // 淡入完成前屏蔽点击

        double mx = event.x();
        double my = event.y();

        if (this.confirmButton != null && isMouseOver(this.confirmButton, mx, my)) {
            this.confirmButton.onClick(event, isActuallyClick);
            return true;
        }
        if (this.cancelButton != null && isMouseOver(this.cancelButton, mx, my)) {
            this.cancelButton.onClick(event, isActuallyClick);
            return true;
        }
        return true; // 点击对话框外部不穿透
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (exiting) return true; // 淡出期间屏蔽键盘

        // ESC 键 = 取消退出
        if (keyEvent.key() == 256) { // GLFW_KEY_ESCAPE
            onCancelQuit();
            return true;
        }
        // Enter 键 = 确认退出
        if (keyEvent.key() == 257 || keyEvent.key() == 335) { // GLFW_KEY_ENTER / KP_ENTER
            onConfirmQuit();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // 由 keyPressed 处理 ESC
    }

    @Override
    public boolean isPauseScreen() {
        return false; // 不暂停游戏
    }

    @Override
    public void onClose() {
        // 阻止直接关闭（如屏幕被替换），视为取消
        onCancelQuit();
    }

    // ========== 按钮回调 ==========

    /**
     * 确认退出：设置确认标志后调用 {@link Minecraft#stop()}
     * <p>
     * 确认时不需要淡出动画，因为 {@code stop()} 会立即退出游戏主循环。
     */
    private void onConfirmQuit() {
        if (exiting) return;
        LOGGER.info("Player confirmed quit, shutting down game");
        quitConfirmed = true;
        Minecraft.getInstance().stop();
    }

    /**
     * 取消退出：启动 200ms 淡出动画，动画完成后关闭屏幕返回之前界面
     */
    private void onCancelQuit() {
        if (exiting) return;
        LOGGER.debug("Player cancelled quit, starting fade-out animation");
        startExitAnimation(() -> {
            quitConfirmed = false;
            Minecraft.getInstance().setScreenAndShow(null);
        });
    }

    /**
     * 启动淡出动画
     */
    private void startExitAnimation(Runnable onComplete) {
        if (exiting) return;
        this.exiting = true;
        this.exitStartTime = System.currentTimeMillis();
        this.exitProgress = 0f;
        this.onExitComplete = onComplete;
    }

    // ========== 工具方法 ==========

    private boolean isMouseOver(TransparentButton button, double mx, double my) {
        return mx >= button.getX() && mx < button.getX() + button.getWidth()
                && my >= button.getY() && my < button.getY() + button.getHeight();
    }

    private void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
        // 主体
        g.fill(x + r, y, x + w - r, y + h, color);
        // 左右边条
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);

        // 填充四分之一圆角内部像素
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

    /** easeOutCubic：平滑减速 */
    private float easeOutCubic(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }
}
