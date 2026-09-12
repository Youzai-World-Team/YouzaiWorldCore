package top.csituka.youzaiworldcore.client.screen;

import top.csituka.youzaiworldcore.client.render.YzuiTheme;
import top.csituka.youzaiworldcore.client.render.YzuiBackdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.csituka.youzaiworldcore.client.screen.widget.TransparentButton;
import top.csituka.youzaiworldcore.client.animation.GuiAnimationController;

/**
 * 退出确认屏幕。
 * <p>
 * 当玩家点击标题屏幕的"退出游戏"按钮或窗口右上角关闭按钮时显示，
 * 询问玩家是否确定要退出游戏。包含"确定退出"和"取消"两个选项。
 * <p>
 * 对话框样式与 {@code ConfirmationDialog} 保持一致：
 * 官网薄荷色 MD3 卡片、语义文字色和键盘可操作的按钮。
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
    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 184;
    private static final int BUTTON_WIDTH = 170;
    private static final int BUTTON_HEIGHT = 28;

    // ============ 动画常量 ============

    /** 标记是否已确认退出，用于 {@link top.csituka.youzaiworldcore.mixin.client.MinecraftQuitMixin} 判断 */
    public static boolean quitConfirmed = false;

    private TransparentButton confirmButton;
    private TransparentButton cancelButton;

    public QuitConfirmationScreen() {
        super(Component.translatable("youzaiworldcore.message.gui.quit_title"));
    }

    // ============ 动画状态 ============


    @Override
    protected void init() {
        super.init();
        int x = (width - DIALOG_WIDTH) / 2;
        int y = (height - DIALOG_HEIGHT) / 2 + DIALOG_HEIGHT - BUTTON_HEIGHT - 24;
        quitConfirmed = false;
        confirmButton = addRenderableWidget(new TransparentButton(x + 24, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("youzaiworldcore.message.gui.quit_confirm"), this::onConfirmQuit));
        confirmButton.setStyle(YzuiTheme.ButtonStyle.DANGER);
        cancelButton = addRenderableWidget(new TransparentButton(x + 206, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("youzaiworldcore.message.gui.quit_cancel"), this::onCancelQuit));
        setFocused(cancelButton);
    }

    // ============ 淡入状态 ============


    // ============ 渲染 ============

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        float alpha = 1f;
        int x = (width - DIALOG_WIDTH) / 2, y = (height - DIALOG_HEIGHT) / 2;
        YzuiTheme.card(guiGraphics, x, y, DIALOG_WIDTH, DIALOG_HEIGHT, alpha);
        YzuiTheme.label(guiGraphics, font, getTitle(), x + 24, y + 24,
                DIALOG_WIDTH - 48, YzuiTheme.alpha(YzuiTheme.text(), alpha), false);
        YzuiTheme.wrapped(guiGraphics, font, Component.translatable("youzaiworldcore.message.gui.quit_message"),
                x + 24, y + 52, DIALOG_WIDTH - 48, (DIALOG_HEIGHT - 114) / (font.lineHeight + 2),
                YzuiTheme.alpha(YzuiTheme.textMuted(), alpha));
        confirmButton.setExternalAlpha(alpha);
        cancelButton.setExternalAlpha(alpha);
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
    }

    // ============ 输入事件（淡出进行中时屏蔽交互） ============

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isActuallyClick) {
        if (GuiAnimationController.isExiting(this) || event.button() != 0) return true;
        return super.mouseClicked(event, isActuallyClick);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (GuiAnimationController.isExiting(this)) return true;
        if (keyEvent.key() == 256) { onCancelQuit(); return true; }
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
        if (GuiAnimationController.isExiting(this)) return;
        LOGGER.info("Player confirmed quit, shutting down game");
        quitConfirmed = true;
        Minecraft.getInstance().stop();
    }

    /**
     * 取消退出：启动 200ms 淡出动画，动画完成后关闭屏幕返回之前界面
     */
    private void onCancelQuit() {
        if (GuiAnimationController.isExiting(this)) return;
        LOGGER.debug("Player cancelled quit, starting fade-out animation");
        startExitAnimation(() -> {
            quitConfirmed = false;
            Minecraft.getInstance().setScreenAndShow(YzuiBackdrop.parent(this));
        });
    }

    /**
     * 启动淡出动画
     */
    private void startExitAnimation(Runnable onComplete) {
        if (!GuiAnimationController.isExiting(this)) onComplete.run();
    }

    // ========== 工具方法 ==========

    /** easeOutCubic：平滑减速 */

}
